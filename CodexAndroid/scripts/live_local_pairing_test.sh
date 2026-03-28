#!/usr/bin/env bash

# FILE: live_local_pairing_test.sh
# Purpose: Runs a local Remodex relay+bridge session and injects the fresh QR payload into Android for living tests.
# Layer: developer utility
# Exports: none
# Depends on: bash, node, /Users/yyy/adb/adb, run-local-remodex.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="${ROOT_DIR}/CodexAndroid"
APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/debug/app-debug.apk"
APP_PACKAGE="com.remodex.mobile"
PAIRING_SESSION_FILE="${REMODEX_DEVICE_STATE_DIR:-${HOME}/.remodex}/pairing-session.json"

ADB_PATH="${ADB_PATH:-/Users/yyy/adb/adb}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
RELAY_HOSTNAME="${RELAY_HOSTNAME:-192.168.31.138}"
RELAY_PORT="${RELAY_PORT:-9100}"
WAIT_SECONDS="${WAIT_SECONDS:-45}"
SKIP_BUILD="false"
RUN_LOG_PATH="${RUN_LOG_PATH:-/tmp/remodex-run-local-live.log}"
RUN_PID=""

usage() {
  cat <<'EOF'
Usage: CodexAndroid/scripts/live_local_pairing_test.sh [options]

Options:
  --hostname <ip-or-hostname>  Hostname/IP advertised by run-local-remodex (default: 192.168.31.138)
  --port <port>                Relay port for run-local-remodex (default: 9100)
  --adb <path>                 adb path (default: /Users/yyy/adb/adb)
  --device <serial>            adb device serial (optional)
  --wait-seconds <n>           max wait for connected status (default: 45)
  --skip-build                 skip APK build/install
  --run-log <path>             path for run-local-remodex combined logs
  --help                       show this help text
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --hostname)
      RELAY_HOSTNAME="$2"
      shift 2
      ;;
    --adb)
      ADB_PATH="$2"
      shift 2
      ;;
    --port)
      RELAY_PORT="$2"
      shift 2
      ;;
    --device)
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --wait-seconds)
      WAIT_SECONDS="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD="true"
      shift 1
      ;;
    --run-log)
      RUN_LOG_PATH="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -x "${ADB_PATH}" ]]; then
  echo "adb not executable at ${ADB_PATH}" >&2
  exit 1
fi

ADB_ARGS=("${ADB_PATH}")
if [[ -n "${DEVICE_SERIAL}" ]]; then
  ADB_ARGS+=("-s" "${DEVICE_SERIAL}")
fi

cleanup() {
  if [[ -n "${RUN_PID}" ]] && kill -0 "${RUN_PID}" >/dev/null 2>&1; then
    kill "${RUN_PID}" >/dev/null 2>&1 || true
    wait "${RUN_PID}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

if [[ "${SKIP_BUILD}" != "true" ]]; then
  echo "[live-test] building debug APK"
  (
    cd "${ANDROID_DIR}"
    ./gradlew -g /tmp/gradle-home :app:assembleDebug
  )

  echo "[live-test] installing APK"
  "${ADB_ARGS[@]}" install -r "${APK_PATH}"
fi

echo "[live-test] starting run-local-remodex on hostname=${RELAY_HOSTNAME} port=${RELAY_PORT}"
(
  cd "${ROOT_DIR}"
  bash ./run-local-remodex.sh --hostname "${RELAY_HOSTNAME}" --port "${RELAY_PORT}"
) >"${RUN_LOG_PATH}" 2>&1 &
RUN_PID=$!

start_epoch="$(date +%s)"
echo "[live-test] waiting for fresh pairing session at ${PAIRING_SESSION_FILE}"

found_pairing="false"
for _ in $(seq 1 40); do
  if [[ -f "${PAIRING_SESSION_FILE}" ]]; then
    created_epoch="$(node -e '
const fs = require("fs");
const file = process.argv[1];
try {
  const session = JSON.parse(fs.readFileSync(file, "utf8"));
  const createdAt = Date.parse(session?.createdAt || "");
  if (Number.isFinite(createdAt)) {
    process.stdout.write(String(Math.floor(createdAt / 1000)));
  }
} catch {}
' "${PAIRING_SESSION_FILE}")"
    if [[ -n "${created_epoch}" && "${created_epoch}" -ge "${start_epoch}" ]]; then
      found_pairing="true"
      break
    fi
  fi
  sleep 1
done

if [[ "${found_pairing}" != "true" ]]; then
  echo "[live-test] failed: no fresh pairing session was published." >&2
  tail -n 80 "${RUN_LOG_PATH}" >&2 || true
  exit 1
fi

pairing_json="$(node -e '
const fs = require("fs");
const file = process.argv[1];
const session = JSON.parse(fs.readFileSync(file, "utf8"));
if (!session?.pairingPayload) {
  process.exit(1);
}
process.stdout.write(JSON.stringify(session.pairingPayload));
' "${PAIRING_SESSION_FILE}")"

pairing_b64="$(printf '%s' "${pairing_json}" | base64 | tr -d '\n')"

echo "[live-test] launching app and injecting pairing payload"
"${ADB_ARGS[@]}" logcat -c || true
"${ADB_ARGS[@]}" shell am start \
  -n "${APP_PACKAGE}/.MainActivity" \
  --es payload_b64 "${pairing_b64}" \
  --ez connect_live true >/dev/null

deadline=$((SECONDS + WAIT_SECONDS))
success="false"
while (( SECONDS < deadline )); do
  logs="$("${ADB_ARGS[@]}" logcat -d -v brief -s \
    Remodex-CodexService:I \
    Remodex-RelayTransport:I \
    Remodex-MainActivity:I \
    *:S || true)"

  if grep -q "Connected via live secure relay" <<<"${logs}"; then
    success="true"
    break
  fi
  if grep -q "connect(live secure relay) failed" <<<"${logs}"; then
    break
  fi
  sleep 2
done

if [[ "${success}" == "true" ]]; then
  echo "[live-test] PASS: Android connected via local live secure relay."
  exit 0
fi

echo "[live-test] FAIL: did not observe successful local live connect within ${WAIT_SECONDS}s." >&2
echo "[live-test] --- Android logs ---" >&2
"${ADB_ARGS[@]}" logcat -d -v brief -s Remodex-CodexService:I Remodex-RelayTransport:I Remodex-MainActivity:I *:S >&2 || true
echo "[live-test] --- run-local-remodex tail ---" >&2
tail -n 120 "${RUN_LOG_PATH}" >&2 || true
exit 1

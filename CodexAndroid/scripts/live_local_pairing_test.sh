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
DEVICE_STATE_FILE="${REMODEX_DEVICE_STATE_DIR:-${HOME}/.remodex}/device-state.json"

ADB_PATH="${ADB_PATH:-/Users/yyy/adb/adb}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
RELAY_HOSTNAME="${RELAY_HOSTNAME:-192.168.31.138}"
RELAY_PORT="${RELAY_PORT:-9000}"
WAIT_SECONDS="${WAIT_SECONDS:-45}"
SKIP_BUILD="false"
RUN_LOG_PATH="${RUN_LOG_PATH:-/tmp/remodex-run-local-live.log}"
RUN_PID=""

usage() {
  cat <<'EOF'
Usage: CodexAndroid/scripts/live_local_pairing_test.sh [options]

Options:
  --hostname <ip-or-hostname>  Hostname/IP advertised by run-local-remodex (default: 192.168.31.138)
  --port <port>                Relay port for run-local-remodex (default: 9000)
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
    pkill -TERM -P "${RUN_PID}" >/dev/null 2>&1 || true
    kill "${RUN_PID}" >/dev/null 2>&1 || true
    wait "${RUN_PID}" >/dev/null 2>&1 || true
    pkill -KILL -P "${RUN_PID}" >/dev/null 2>&1 || true
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

if ! "${ADB_ARGS[@]}" shell pm path "${APP_PACKAGE}" 2>/dev/null | grep -q '^package:'; then
  if [[ ! -f "${APK_PATH}" ]]; then
    echo "[live-test] failed: APK missing at ${APK_PATH}; build first or remove --skip-build." >&2
    exit 1
  fi
  echo "[live-test] app package not found on device; installing APK"
  "${ADB_ARGS[@]}" install -r "${APK_PATH}"
fi

echo "[live-test] starting run-local-remodex on hostname=${RELAY_HOSTNAME} port=${RELAY_PORT}"
(
  cd "${ROOT_DIR}"
  bash ./run-local-remodex.sh --hostname "${RELAY_HOSTNAME}" --port "${RELAY_PORT}"
) >"${RUN_LOG_PATH}" 2>&1 &
RUN_PID=$!

start_epoch="$(date +%s)"
echo "[live-test] waiting for pairing payload (session file or foreground QR log)"

pairing_json=""
for _ in $(seq 1 60); do
  pairing_json="$(node -e '
const fs = require("fs");
const pairingSessionFile = process.argv[1];
const runLogFile = process.argv[2];
const deviceStateFile = process.argv[3];
const relayHostname = process.argv[4];
const relayPort = process.argv[5];
const startEpoch = Number.parseInt(process.argv[6], 10);

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

if (fs.existsSync(pairingSessionFile)) {
  const session = readJson(pairingSessionFile);
  const createdAt = Date.parse(session?.createdAt || "");
  if (session?.pairingPayload && Number.isFinite(createdAt) && createdAt >= startEpoch * 1000) {
    process.stdout.write(JSON.stringify(session.pairingPayload));
    process.exit(0);
  }
}

if (!fs.existsSync(runLogFile) || !fs.existsSync(deviceStateFile)) {
  process.exit(0);
}

const logText = fs.readFileSync(runLogFile, "utf8");
const pick = (regex) => {
  const match = logText.match(regex);
  return match && match[1] ? match[1].trim() : "";
};

const sessionId = pick(/Session ID:\s*([^\r\n]+)/i);
const macDeviceId = pick(/Device ID:\s*([^\r\n]+)/i);
const expiresRaw = pick(/Expires:\s*([^\r\n]+)/i);
const expiresAt = Date.parse(expiresRaw);
const state = readJson(deviceStateFile);
const macIdentityPublicKey = (state && state.macIdentityPublicKey) ? String(state.macIdentityPublicKey).trim() : "";

if (!sessionId || !macDeviceId || !macIdentityPublicKey || !Number.isFinite(expiresAt)) {
  process.exit(0);
}

const pairingPayload = {
  v: 2,
  relay: `ws://${relayHostname}:${relayPort}/relay`,
  sessionId,
  macDeviceId,
  macIdentityPublicKey,
  expiresAt
};
process.stdout.write(JSON.stringify(pairingPayload));
' "${PAIRING_SESSION_FILE}" "${RUN_LOG_PATH}" "${DEVICE_STATE_FILE}" "${RELAY_HOSTNAME}" "${RELAY_PORT}" "${start_epoch}")"
  if [[ -n "${pairing_json}" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "${pairing_json}" ]]; then
  echo "[live-test] failed: no fresh pairing payload was discovered." >&2
  tail -n 80 "${RUN_LOG_PATH}" >&2 || true
  exit 1
fi

pairing_b64="$(printf '%s' "${pairing_json}" | base64 | tr -d '\n')"

echo "[live-test] launching app and injecting pairing payload"
"${ADB_ARGS[@]}" logcat -c || true
"${ADB_ARGS[@]}" shell am start \
  -S \
  -W \
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

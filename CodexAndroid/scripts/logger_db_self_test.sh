#!/usr/bin/env bash

# FILE: logger_db_self_test.sh
# Purpose: Pull Android logger SQLite DB, print latest rows, and fail on obvious sensitive leaks.
# Layer: developer utility
# Exports: none
# Depends on: bash, sqlite3, /Users/yyy/adb/adb, debug build with run-as enabled

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ADB_PATH="${ADB_PATH:-/Users/yyy/adb/adb}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
APP_PACKAGE="${APP_PACKAGE:-com.remodex.mobile}"
OUTPUT_DB="${OUTPUT_DB:-/tmp/remodex_logger.db}"

usage() {
  cat <<'EOF'
Usage: CodexAndroid/scripts/logger_db_self_test.sh [options]

Options:
  --adb <path>         adb path (default: /Users/yyy/adb/adb)
  --device <serial>    adb device serial (optional)
  --package <name>     app package (default: com.remodex.mobile)
  --out <path>         output sqlite db path (default: /tmp/remodex_logger.db)
  --help               show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --adb)
      ADB_PATH="$2"
      shift 2
      ;;
    --device)
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --package)
      APP_PACKAGE="$2"
      shift 2
      ;;
    --out)
      OUTPUT_DB="$2"
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

mkdir -p "$(dirname "${OUTPUT_DB}")"

echo "[logger-self-test] pulling DB from ${APP_PACKAGE}"
"${ADB_ARGS[@]}" exec-out run-as "${APP_PACKAGE}" cat databases/remodex_logger.db > "${OUTPUT_DB}"

echo "[logger-self-test] latest log rows"
sqlite3 "${OUTPUT_DB}" "select id, level, tag, message from log_entries order by id desc limit 50;"

echo "[logger-self-test] checking for obvious sensitive patterns"
rows="$(sqlite3 "${OUTPUT_DB}" "select message from log_entries order by id desc limit 300;")"

if grep -Eiq '[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|wss?://[^ ]+/relay/[A-Za-z0-9._~-]{6,}|token=[^< ]|sessionId=[^< ]' <<<"${rows}"; then
  echo "[logger-self-test] FAIL: potential sensitive content detected in persisted logs." >&2
  exit 1
fi

echo "[logger-self-test] PASS: no obvious sensitive leaks in sampled rows."

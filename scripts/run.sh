#!/bin/bash
# Starts the app with ANTHROPIC_API_KEY taken from the macOS Keychain — the key never hits disk,
# shell history or stdout. One-time setup:
#   security add-generic-password -a "$USER" -s anthropic-api-key -w
set -euo pipefail
cd "$(dirname "$0")/.."

if ! key=$(security find-generic-password -a "$USER" -s anthropic-api-key -w 2>/dev/null); then
  echo "❌ No 'anthropic-api-key' in Keychain. Add it with:" >&2
  echo "   security add-generic-password -a \"\$USER\" -s anthropic-api-key -w" >&2
  exit 1
fi

export ANTHROPIC_API_KEY="$key"
unset key
exec ./gradlew bootRun

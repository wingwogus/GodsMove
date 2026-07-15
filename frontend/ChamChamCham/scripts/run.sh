#!/usr/bin/env bash
#
# Build → install → launch the app on a booted simulator, then stream its logs.
# Xcode GUI never opens. Uses the shared DerivedData that buildServer.json (LSP)
# points at, so editor diagnostics and CLI builds stay consistent.
#
# Usage: scripts/run.sh ["iPhone 17"]
set -euo pipefail

SCHEME="ChamChamCham"
BUNDLE_ID="me.GodsMove.ChamChamCham"
DEVICE="${1:-iPhone 17}"
DEST="platform=iOS Simulator,name=${DEVICE}"

cd "$(dirname "$0")/.."   # -> frontend/ChamChamCham

echo "▸ Building ${SCHEME} (Debug) for ${DEVICE}…"
set -o pipefail
xcodebuild -scheme "$SCHEME" -configuration Debug -destination "$DEST" build \
  | xcbeautify

APP_PATH=$(xcodebuild -scheme "$SCHEME" -configuration Debug -destination "$DEST" \
  -showBuildSettings 2>/dev/null \
  | awk -F' = ' '/ CODESIGNING_FOLDER_PATH =/{print $2; exit}')

if [[ -z "${APP_PATH:-}" || ! -d "$APP_PATH" ]]; then
  echo "✗ Could not locate built .app (got: '${APP_PATH:-}')" >&2
  exit 1
fi
echo "▸ App: $APP_PATH"

echo "▸ Booting ${DEVICE}…"
xcrun simctl boot "$DEVICE" 2>/dev/null || true
open -a Simulator

echo "▸ Installing…"
xcrun simctl install "$DEVICE" "$APP_PATH"

echo "▸ Launching (Ctrl-C to stop logs)…"
xcrun simctl launch --console-pty "$DEVICE" "$BUNDLE_ID"

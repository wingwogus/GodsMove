#!/usr/bin/env bash
#
# Build ONCE, then install + launch on every currently-booted simulator.
# Boot the sims you want first (Simulator app, or `xcrun simctl boot "<name>"`),
# then run this to see the same build side-by-side on all of them.
#
# Usage: scripts/run-all.sh
set -euo pipefail

SCHEME="ChamChamCham"
BUNDLE_ID="me.GodsMove.ChamChamCham"
# Build for a generic simulator so one .app runs on every booted device.
DEST="generic/platform=iOS Simulator"

cd "$(dirname "$0")/.."   # -> frontend/ChamChamCham

echo "▸ Building ${SCHEME} (Debug) once…"
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

BOOTED=$(xcrun simctl list devices booted -j \
  | /usr/bin/python3 -c 'import json,sys; [print(d["udid"], d["name"]) for v in json.load(sys.stdin)["devices"].values() for d in v]')

if [[ -z "$BOOTED" ]]; then
  echo "✗ No booted simulators. Boot some first, e.g.:" >&2
  echo "    xcrun simctl boot \"iPhone 17\"; open -a Simulator" >&2
  exit 1
fi

open -a Simulator
while read -r UDID NAME; do
  [[ -z "$UDID" ]] && continue
  echo "▸ [$NAME] install + launch…"
  xcrun simctl install "$UDID" "$APP_PATH"
  xcrun simctl launch "$UDID" "$BUNDLE_ID" >/dev/null
done <<< "$BOOTED"

echo "✓ Launched on all booted simulators."

#!/usr/bin/env bash
#
# Clean build. Removes this project's DerivedData (the most reliable clean),
# then runs `xcodebuild clean`. Re-run scripts/run.sh afterwards to rebuild.
set -euo pipefail

SCHEME="ChamChamCham"
cd "$(dirname "$0")/.."   # -> frontend/ChamChamCham

echo "▸ Removing DerivedData for ${SCHEME}…"
rm -rf ~/Library/Developer/Xcode/DerivedData/ChamChamCham-*

echo "▸ xcodebuild clean…"
xcodebuild -scheme "$SCHEME" clean | xcbeautify

echo "✓ Clean done. LSP note: buildServer.json still points at the old"
echo "  DerivedData hash — run 'xcode-build-server config -project"
echo "  ChamChamCham.xcodeproj -scheme ${SCHEME}' if editor diagnostics go stale."

#!/bin/bash
# render-outfits.sh — compile and run the Mochi outfit planche renderer
# NOT in CI. Run manually: bash scripts/render-outfits.sh
# Output: /tmp/cocoa-outfits.png

set -e
cd "$(dirname "$0")/.."

SDK=$(xcrun --sdk macosx --show-sdk-path)

echo "Compiling renderer..."
swiftc \
  -parse-as-library \
  -sdk "$SDK" \
  -target arm64-apple-macosx15.0 \
  NotchBuddy/Sources/CocoaKit/IslandScreenGeometry.swift \
  NotchBuddy/Sources/CocoaKit/IslandTypes.swift \
  NotchBuddy/Sources/CocoaKit/MochiWardrobe.swift \
  NotchBuddy/Sources/CocoaKit/BotEngine.swift \
  NotchBuddy/Sources/CocoaKit/MochiOutfitDrawing.swift \
  scripts/RenderOutfits.swift \
  -framework AppKit \
  -framework SwiftUI \
  -o /tmp/cocoa-render-outfits \
  2>&1

echo "Running renderer..."
/tmp/cocoa-render-outfits
echo "Opening..."
open /tmp/cocoa-outfits.png

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/cocoa-wardrobe.XXXXXX")"
trap 'rm -rf "$TEST_DIR"' EXIT
swiftc NotchBuddy/Sources/CocoaKit/MochiWardrobe.swift \
    tests/MochiWardrobeTests.swift -o "$TEST_DIR/wardrobe-tests"
"$TEST_DIR/wardrobe-tests"

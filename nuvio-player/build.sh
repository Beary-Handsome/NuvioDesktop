#!/usr/bin/env bash
# Build nuvio-player for Linux and copy binary to Compose resources.
# Run from nuvio-player/ directory.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BINARY_NAME="nuvio-player"

echo "Building nuvio-player (Linux)..."

# Build Tauri app (release)
cd "$SCRIPT_DIR/src-tauri"
cargo build --release
cd "$SCRIPT_DIR"

# Binary location
BINARY_SOURCE="$SCRIPT_DIR/src-tauri/target/release/$BINARY_NAME"
if [ ! -f "$BINARY_SOURCE" ]; then
    echo "Binary not found at $BINARY_SOURCE" >&2
    exit 1
fi

# Copy to Compose resources (Linux)
RESOURCE_DIR="$PROJECT_ROOT/composeApp/resources/linux"
mkdir -p "$RESOURCE_DIR"

cp "$BINARY_SOURCE" "$RESOURCE_DIR/$BINARY_NAME"
chmod +x "$RESOURCE_DIR/$BINARY_NAME"
echo "Copied binary to $RESOURCE_DIR/$BINARY_NAME"
echo "Build complete."

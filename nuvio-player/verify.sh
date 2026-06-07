#!/bin/bash
set -e
echo "=== Nuvio Player Build Verification ==="

# 1. Check system dependencies
echo "[1/6] Checking dependencies..."
for dep in libmpv-dev libx11-dev libegl1-mesa-dev pkg-config; do
    if dpkg -l "$dep" > /dev/null 2>&1; then
        echo "  ✓ $dep"
    else
        echo "  ✗ MISSING: $dep"
    fi
done

# 2. Cargo build
echo "[2/6] Building Rust backend..."
cargo build --release 2>&1

# 3. Check binary exists
echo "[3/6] Checking binary..."
if [ -f target/release/nuvio-player ]; then
    echo "  ✓ Binary found"
else
    echo "  ✗ Binary missing"
    exit 1
fi

# 4. Basic launch test (--help)
echo "[4/6] CLI args test..."
./target/release/nuvio-player --help

# 5. Test with local file if available
echo "[5/6] Playback test (skip if no test file)..."
if [ -f /tmp/test.mp4 ]; then
    timeout 5 ./target/release/nuvio-player --url /tmp/test.mp4 --title "Test" || true
else
    echo "  — /tmp/test.mp4 not found, skipping"
fi

# 6. Copy to resources
echo "[6/6] Copying to composeApp resources..."
mkdir -p ../composeApp/src/desktopMain/resources/linux
cp target/release/nuvio-player ../composeApp/src/desktopMain/resources/linux/
echo "  ✓ Copied"

echo "=== Done ==="

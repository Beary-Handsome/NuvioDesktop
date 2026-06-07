#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"
Write-Host "=== Nuvio Player Build Verification ===" -ForegroundColor Cyan

# 1. Check Rust installed
Write-Host "[1/6] Checking dependencies..."
try {
    $null = Get-Command cargo -ErrorAction Stop
    Write-Host "  ✓ cargo"
} catch {
    Write-Host "  ✗ MISSING: cargo — install Rust from https://rustup.rs"
}

# 2. Cargo build
Write-Host "[2/6] Building Rust backend..."
Push-Location src-tauri
cargo build --release
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ Build failed" -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location
Write-Host "  ✓ Build succeeded"

# 3. Check binary exists
Write-Host "[3/6] Checking binary..."
$binary = "target/release/nuvio-player.exe"
if (Test-Path $binary) {
    Write-Host "  ✓ Binary found at $binary"
} else {
    Write-Host "  ✗ Binary missing" -ForegroundColor Red
    exit 1
}

# 4. Basic launch test (--help)
Write-Host "[4/6] CLI args test..."
$output = & $binary --help 2>&1 | Out-String
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ --help works"
} else {
    Write-Host "  ✗ --help failed" -ForegroundColor Red
}

# 5. Test with local file if available
Write-Host "[5/6] Playback test (skip if no test file)..."
if (Test-Path "C:\tmp\test.mp4") {
    $proc = Start-Process -FilePath $binary -ArgumentList "--url", "C:\tmp\test.mp4", "--title", "Test" -NoNewWindow -PassThru
    Start-Sleep -Seconds 5
    if (!$proc.HasExited) { $proc.Kill() }
    Write-Host "  ✓ Playback launched"
} else {
    Write-Host "  — C:\tmp\test.mp4 not found, skipping"
}

# 6. Copy to resources
Write-Host "[6/6] Copying to composeApp resources..."
$resourceDir = "../composeApp/src/desktopMain/resources/windows"
if (-not (Test-Path $resourceDir)) {
    New-Item -ItemType Directory -Path $resourceDir -Force | Out-Null
}
Copy-Item -Path $binary -Destination "$resourceDir/nuvio-player.exe" -Force
Write-Host "  ✓ Copied to $resourceDir"

Write-Host "=== Done ===" -ForegroundColor Cyan

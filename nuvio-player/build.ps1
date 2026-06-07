#!/usr/bin/env pwsh
# Build nuvio-player for Windows and copy binary to Compose resources.
# Run from nuvio-player/ directory.

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BinaryName = "nuvio-player.exe"

Write-Host "Building nuvio-player (Windows)..." -ForegroundColor Cyan

# Build Tauri app (release)
Push-Location "$PSScriptRoot\src-tauri"
cargo build --release
if ($LASTEXITCODE -ne 0) {
    Write-Host "cargo build failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}
Pop-Location

# Binary location
$BinarySource = "$PSScriptRoot\src-tauri\target\release\$BinaryName"
if (-not (Test-Path $BinarySource)) {
    Write-Host "Binary not found at $BinarySource" -ForegroundColor Red
    exit 1
}

# Copy to Compose resources (Windows)
$ResourceDir = "$ProjectRoot\composeApp\resources\windows"
if (-not (Test-Path $ResourceDir)) {
    New-Item -ItemType Directory -Path $ResourceDir -Force | Out-Null
}

Copy-Item -Path $BinarySource -Destination "$ResourceDir\$BinaryName" -Force
Write-Host "Copied binary to $ResourceDir\$BinaryName" -ForegroundColor Green
Write-Host "Build complete." -ForegroundColor Cyan

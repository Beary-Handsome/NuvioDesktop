#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Downloads the missing FFmpeg shared libraries for the Linux mpv build.

.DESCRIPTION
    Fetches the FFmpeg 7.1 GPL shared build from BtbN/FFmpeg-Builds and
    extracts the required .so files (libavformat, libavutil, libswresample,
    libavfilter, libpostproc) into mediamp-mpv/libmpv/lib/linux/x86_64/.

    The existing libavcodec and libswscale are also replaced to ensure all
    libraries come from the same FFmpeg build (avoiding ABI mismatches).

    Run this from the repo root (NuvioDesktop).
#>

$ErrorActionPreference = "Stop"

# --- paths ---
$RepoRoot = Resolve-Path "$PSScriptRoot/.."
$LinuxLibDir = "$RepoRoot/mediamp/mediamp-mpv/libmpv/lib/linux/x86_64"

if (-not (Test-Path $LinuxLibDir)) {
    Write-Error "Linux library directory not found: $LinuxLibDir`nMake sure you're running from the repo root."
    exit 1
}

Write-Host "==> Target directory: $LinuxLibDir"

# --- download ---
# Floating URL that always points to the latest n7.1 gpl-shared build
$TarballUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/ffmpeg-n7.1-latest-linux64-gpl-shared-7.1.tar.xz"
$TarballName = "ffmpeg-n7.1-latest-linux64-gpl-shared-7.1.tar.xz"
$TmpDir = "$RepoRoot/tmp_ffmpeg_download"

try {
    New-Item -ItemType Directory -Force -Path $TmpDir | Out-Null
    $TarballPath = "$TmpDir/$TarballName"

    if (-not (Test-Path $TarballPath)) {
        Write-Host "==> Downloading $TarballUrl ..."
        Invoke-WebRequest -Uri $TarballUrl -OutFile $TarballPath -UseBasicParsing
        Write-Host "    Download complete: $((Get-Item $TarballPath).Length / 1MB -as [int]) MB"
    } else {
        Write-Host "==> Tarball already exists, reusing: $TarballPath"
    }

    # --- extract ---
    $ExtractDir = "$TmpDir/extracted"
    New-Item -ItemType Directory -Force -Path $ExtractDir | Out-Null
    Write-Host "==> Extracting ..."
    # Windows tar handles .tar.xz via -J
    & tar.exe -xJf $TarballPath -C $ExtractDir 2>&1

    # Find all .so files inside the extracted tree
    Write-Host "==> Locating .so files ..."
    $SoFiles = Get-ChildItem -Recurse -File -Path $ExtractDir -Filter "lib*.so*"
    if (-not $SoFiles) {
        Write-Error "No .so files found in tarball — the download URL or archive format may have changed."
        exit 1
    }
    Write-Host "    Found $($SoFiles.Count) .so files. Copying to $LinuxLibDir ..."
    foreach ($f in $SoFiles) {
        $dest = "$LinuxLibDir/$($f.Name)"
        Copy-Item -Path $f.FullName -Destination $dest -Force
        Write-Host "      $($f.Name)"
    }

    Write-Host "==> Extraction complete."

    # --- verify ---
    $expected = @(
        "libavformat.so",
        "libavutil.so",
        "libswresample.so",
        "libavfilter.so",
        "libpostproc.so"
    )
    $missing = $expected | Where-Object { -not (Test-Path "$LinuxLibDir/$_") }
    if ($missing) {
        Write-Warning "Some libraries were not extracted: $($missing -join ', ')"
        Write-Warning "The tarball may have a different internal layout. Check $LinuxLibDir for actual files."
    } else {
        Write-Host "==> All required libraries present."
    }

    # --- list the result ---
    Write-Host "`n==> Contents of $LinuxLibDir :"
    Get-ChildItem -Path $LinuxLibDir -Name | Sort-Object

    Write-Host "`n==> SUCCESS! The Linux FFmpeg dependencies are now in place."
    Write-Host "    Rebuild the native library on Linux and the deps/ folder will include them."
}
finally {
    # clean up
    if (Test-Path $TmpDir) {
        Remove-Item -Recurse -Force $TmpDir -ErrorAction SilentlyContinue
    }
}

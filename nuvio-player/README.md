# Nuvio Player — standalone mpv video player for NuvioDesktop

A lightweight, full-screen video player built with **Tauri 2.0** (Rust + React)
that wraps **libmpv** for hardware-accelerated playback. Designed to replace the
in-process mediamp-mpv backend in NuvioDesktop with an isolated child process.

## System Requirements

| Platform | Dependencies |
|----------|-------------|
| **Windows 10/11** | libmpv-2.dll (bundled with NuvioDesktop), WebView2 runtime |
| **Linux (X11/Wayland)** | `libmpv-dev`, `libx11-dev`, `libegl1-mesa-dev`, `pkg-config`, `libwebkit2gtk-4.1-dev` |

## Build

```bash
cd nuvio-player/

# Install dependencies (Linux)
sudo apt install libmpv-dev libx11-dev libegl1-mesa-dev \
                 pkg-config libwebkit2gtk-4.1-dev

# Build release binary
cd src-tauri && cargo build --release && cd ..

# Or use the build helpers:
./build.sh       # Linux — also copies to composeApp resources
./build.ps1      # Windows
```

## CLI Usage

```text
Nuvio Player 1.0.0

Usage: nuvio-player [OPTIONS] --url <URL>

Options:
      --url <URL>              Stream URL to play [required]
      --title <TITLE>          Media title [default: ]
      --user-agent <USER_AGENT> HTTP User-Agent [default: libmpv/nuvio]
      --referer <REFERER>      HTTP Referer [default: ]
      --start-time <START_TIME> Start position in seconds [default: 0]
      --hwdec <HWDEC>          Hardware decoding: auto, no, copy [default: auto]
      --volume <VOLUME>        Initial volume 0–200 [default: 100]
      --sub-file <SUB_FILE>    External subtitle file path
  -h, --help                   Print help
  -V, --version                Print version
```

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Space` | Play / Pause |
| `F` | Toggle fullscreen |
| `M` | Toggle mute |
| `←` / `→` | Seek -5s / +5s |
| `Shift + ←` / `→` | Seek -60s / +60s |
| `↑` / `↓` | Volume +10 / -10 |
| `Escape` | Exit fullscreen |

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Playback ended normally |
| `1` | Error (mpv init failed, invalid URL, etc.) |
| `2` | User closed window manually |

## Integration with NuvioDesktop

At runtime, `NuvioPlayerLauncher` resolves the binary in this order:

1. **Bundled** — `compose.application.resources.dir` (Gradle `appResourcesRootDir`)
2. **Dev mode** — `../nuvio-player/src-tauri/target/release/nuvio-player`
3. **PATH** — scanned from `$PATH`

See `composeApp/.../features/player/NuvioPlayerLauncher.kt`.

### Gradle packaging

```kotlin
nativeDistributions {
    appResourcesRootDir.set(project.layout.projectDirectory.dir("src/desktopMain/resources"))
}
```

Build scripts copy the binary to `src/desktopMain/resources/<platform>/`, which
gets bundled into the distribution automatically.

## Troubleshooting

### Black screen on NVIDIA Wayland

```bash
nuvio-player --url <url> --hwdec no
```

NVIDIA proprietary drivers have poor Wayland DMA-BUF support. Setting
`--hwdec no` forces software decoding.

### No video on X11

Ensure `$DISPLAY` is set:

```bash
echo $DISPLAY   # should be :0 or similar
```

For Wayland sessions, the player auto-detects `$WAYLAND_DISPLAY`.

### Binary not found by NuvioDesktop

```bash
# Check if binary exists in dev location
ls -la nuvio-player/src-tauri/target/release/nuvio-player

# Or copy it to the app resources
cp nuvio-player/src-tauri/target/release/nuvio-player \
   composeApp/src/desktopMain/resources/linux/nuvio-player
```

### Player opens but shows black screen (Windows)

Ensure `libmpv-2.dll` is in the same directory as `nuvio-player.exe`, or on
the system `PATH`.

### Logs for launch diagnostics

```bash
tail -f ~/.config/nuvio/player-launcher.log
```

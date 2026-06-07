use libmpv_sys::*;
use raw_window_handle::{HasWindowHandle, RawWindowHandle};
use std::ffi::CString;
use std::sync::OnceLock;
use tauri::WebviewWindow;

// ---------------------------------------------------------------------------
// Session type detection
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum SessionType {
    Wayland,
    X11,
    Unknown,
}

pub fn detect_session_type() -> SessionType {
    if std::env::var("WAYLAND_DISPLAY").is_ok() {
        SessionType::Wayland
    } else if std::env::var("DISPLAY").is_ok() {
        SessionType::X11
    } else {
        SessionType::Unknown
    }
}

pub fn detect_nvidia_linux() -> bool {
    if !cfg!(all(unix, not(target_os = "macos"))) {
        return false;
    }
    std::path::Path::new("/proc/driver/nvidia/version").exists()
}

/// Set environment variables to prefer native Wayland over XWayland.
pub fn enforce_wayland_env() {
    let session = detect_session_type();
    if session == SessionType::Wayland {
        // Force GDK/GTK to use Wayland backend.
        std::env::set_var("GDK_BACKEND", "wayland");
        // Prevent Qt from falling back to XWayland.
        std::env::set_var("QT_QPA_PLATFORM", "wayland");
        // SDL Wayland hint.
        std::env::set_var("SDL_VIDEO_DRIVER", "wayland");
        log::info!("session: Wayland — enforced Wayland env vars");
    } else {
        log::info!("session: {:?} — no Wayland env override needed", session);
    }
}

static SESSION_TYPE: OnceLock<SessionType> = OnceLock::new();

pub fn cached_session_type() -> SessionType {
    *SESSION_TYPE.get_or_init(detect_session_type)
}

// ---------------------------------------------------------------------------
// WindowId — platform-specific window handle for mpv --wid
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy)]
pub enum WindowId {
    /// Windows: raw HWND value
    Win32(isize),
    /// X11: raw Window ID
    X11(u64),
    /// Wayland: raw wl_surface pointer value
    Wayland(u64),
    /// Headless / unsupported — mpv creates its own window
    Null,
}

impl WindowId {
    /// Apply this window ID to mpv as the `--wid` option.
    /// **Must** be called via `mpv_set_option` **before** `mpv_initialize()`.
    pub fn apply_to_mpv(&self, handle: *mut mpv_handle) -> Result<(), String> {
        let wid: i64 = match self {
            WindowId::Win32(h) => *h as i64,
            WindowId::X11(w) => *w as i64,
            WindowId::Wayland(s) => *s as i64,
            WindowId::Null => return Ok(()), // skip — mpv uses its own window
        };

        let name = CString::new("wid").unwrap();
        let mut val = wid;
        let ret = unsafe {
            mpv_set_option(
                handle,
                name.as_ptr(),
                mpv_format_MPV_FORMAT_INT64,
                &mut val as *mut i64 as *mut libc::c_void,
            )
        };
        if ret < 0 {
            return Err(format!("mpv_set_option(wid={wid}): {}", mpv_err(ret)));
        }
        Ok(())
    }

    /// Platform-appropriate `gpu-context` value.
    pub fn gpu_context(&self) -> &'static str {
        match self {
            WindowId::Win32(_) => "d3d11",
            WindowId::X11(_) => "x11egl",
            WindowId::Wayland(_) => "wayland",
            WindowId::Null => "auto", // fallback
        }
    }

    /// Platform-appropriate `vo` value.
    pub fn vo(&self) -> &'static str {
        match self {
            WindowId::Wayland(_) => "gpu-next",
            _ => "gpu",
        }
    }
}

// ---------------------------------------------------------------------------
// Window handle extraction
// ---------------------------------------------------------------------------

/// Extract a `WindowId` from the Tauri webview window.
///
/// On Windows and X11 this creates a **child window** for mpv to render into.
/// On Wayland the parent `wl_surface` pointer is passed directly (mpv creates
/// an internal subsurface).
pub fn extract_window_id(window: &WebviewWindow) -> Option<WindowId> {
    let raw = match window.window_handle() {
        Ok(h) => h,
        Err(e) => {
            log::error!("window_handle() failed: {e}");
            return None;
        }
    };

    let session = cached_session_type();
    let nvidia = detect_nvidia_linux();
    log::info!(
        "window: protocol={:?} nvidia={} handle={:?}",
        session,
        nvidia,
        raw.as_ref()
    );

    match raw.as_ref() {
        RawWindowHandle::Win32(hw) => {
            let parent = hw.hwnd.get() as isize;
            match create_child_win32(parent) {
                Ok(child) => Some(WindowId::Win32(child)),
                Err(e) => {
                    log::error!("Win32 child window: {e}; falling back to null");
                    Some(WindowId::Null)
                }
            }
        }

        RawWindowHandle::Xlib(xw) => {
            let parent = xw.window;
            match create_child_x11(parent) {
                Ok(child) => Some(WindowId::X11(child)),
                Err(e) => {
                    log::error!("X11 child window: {e}; falling back to null");
                    Some(WindowId::Null)
                }
            }
        }

        RawWindowHandle::Xcb(xw) => {
            log::warn!("XCB window — using parent window for wid (no child)");
            Some(WindowId::X11(xw.window.get() as u64))
        }

        RawWindowHandle::Wayland(wl) => {
            let surface = wl.surface.as_ptr() as u64;
            log::info!("wayland: wl_surface=0x{surface:x}");
            Some(WindowId::Wayland(surface))
        }

        other => {
            log::warn!("unsupported RawWindowHandle variant: {other:?}");
            None
        }
    }
}

// ---------------------------------------------------------------------------
// Child-window creation helpers
// ---------------------------------------------------------------------------

#[cfg(windows)]
fn create_child_win32(parent_hwnd: isize) -> Result<isize, String> {
    use windows::Win32::Foundation::*;
    use windows::Win32::Graphics::Gdi::*;
    use windows::Win32::UI::WindowsAndMessaging::*;

    unsafe {
        let instance = GetModuleHandleA(None).map_err(|e| format!("GetModuleHandle: {e:?}"))?;

        // Register a minimal window class (once).
        let class_name = "NuvioVideoSurface\0".encode_utf16().collect::<Vec<_>>();
        let wc = WNDCLASSEXA {
            cbSize: std::mem::size_of::<WNDCLASSEXA>() as u32,
            style: CS_HREDRAW | CS_VREDRAW | CS_OWNDC,
            lpfnWndProc: Some(DefWindowProcA),
            hInstance: instance,
            hbrBackground: HBRUSH(GetStockObject(5)), // BLACK_BRUSH
            lpszClassName: class_name.as_ptr(),
            ..Default::default()
        };
        RegisterClassExA(&wc);

        let hwnd = CreateWindowExA(
            WS_EX_NOPARENTNOTIFY,
            class_name.as_ptr(),
            "",
            WS_CHILD | WS_VISIBLE | WS_CLIPCHILDREN | WS_CLIPSIBLINGS,
            0, 0, 1280, 720,
            HWND(parent_hwnd as *mut _),
            None,
            instance,
            None,
        );

        if hwnd.0.is_null() {
            return Err(format!("CreateWindowExA failed: {:?}", GetLastError()));
        }

        log::info!("created Win32 child video HWND = {:x}", hwnd.0 as isize);
        Ok(hwnd.0 as isize)
    }
}

#[cfg(not(windows))]
fn create_child_win32(_: isize) -> Result<isize, String> {
    Err("not Windows".into())
}

#[cfg(all(unix, not(target_os = "macos")))]
fn create_child_x11(parent: u64) -> Result<u64, String> {
    use std::ptr;

    unsafe {
        let display = x11::xlib::XOpenDisplay(ptr::null());
        if display.is_null() {
            return Err("XOpenDisplay returned null".into());
        }

        let mut attrs: x11::xlib::XSetWindowAttributes = std::mem::zeroed();
        let child = x11::xlib::XCreateWindow(
            display,
            parent as x11::xlib::Window,
            0, 0, 1280, 720, 0,
            x11::xlib::CopyFromParent as i32,
            1, // InputOutput
            ptr::null_mut(),
            0,
            &mut attrs,
        );

        if child == 0 {
            x11::xlib::XCloseDisplay(display);
            return Err("XCreateWindow returned 0".into());
        }

        x11::xlib::XSelectInput(display, child, x11::xlib::StructureNotifyMask);
        x11::xlib::XMapWindow(display, child);
        x11::xlib::XFlush(display);

        log::info!("created X11 child video Window = {child:x}");
        Ok(child as u64)
    }
}

#[cfg(not(all(unix, not(target_os = "macos"))))]
fn create_child_x11(_: u64) -> Result<u64, String> {
    Err("not Linux/X11".into())
}

// ---------------------------------------------------------------------------
// mpv error helper (re-used from mpv.rs)
// ---------------------------------------------------------------------------

fn mpv_err(code: i32) -> String {
    let s = unsafe { mpv_error_string(code) };
    if s.is_null() {
        format!("error code {code}")
    } else {
        unsafe { std::ffi::CStr::from_ptr(s) }
            .to_string_lossy()
            .to_string()
    }
}

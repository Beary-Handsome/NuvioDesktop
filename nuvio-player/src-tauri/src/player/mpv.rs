use libmpv_sys::*;
use serde::{Deserialize, Serialize};
use std::ffi::{CStr, CString};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use tauri::Emitter;

use super::config::PlayerConfig;
use super::window::{cached_session_type, detect_nvidia_linux, WindowId, SessionType};

// ---------------------------------------------------------------------------
// mpv event-id constants (from the C header; avoid naming mismatches)
// ---------------------------------------------------------------------------

const MPV_EVENT_SHUTDOWN: u32 = 1;
const MPV_EVENT_LOG_MESSAGE: u32 = 2;
const MPV_EVENT_GET_PROPERTY_REPLY: u32 = 3;
const MPV_EVENT_SET_PROPERTY_REPLY: u32 = 4;
const MPV_EVENT_COMMAND_REPLY: u32 = 5;
const MPV_EVENT_START_FILE: u32 = 6;
const MPV_EVENT_END_FILE: u32 = 7;
const MPV_EVENT_FILE_LOADED: u32 = 8;
const MPV_EVENT_PROPERTY_CHANGE: u32 = 23;
const MPV_EVENT_PLAYBACK_RESTART: u32 = 26;

const MPV_END_FILE_REASON_EOF: u32 = 0;
const MPV_END_FILE_REASON_ERROR: u32 = 2;
const MPV_END_FILE_REASON_STOP: u32 = 3;
const MPV_END_FILE_REASON_QUIT: u32 = 4;

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

/// Snapshot of current playback state, emitted to the frontend.
#[derive(Debug, Clone, Serialize)]
pub struct PlaybackState {
    pub state: String,
    pub position: f64,
    pub duration: f64,
    pub volume: i64,
    pub speed: f64,
    pub mute: bool,
    pub paused_for_cache: bool,
}

/// Information about a single media track (video/audio/subtitle).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrackInfo {
    pub id: i64,
    #[serde(rename = "type")]
    pub track_type: String,
    pub title: String,
    pub lang: String,
    pub selected: bool,
}

/// Events emitted to the React frontend via `app.emit("player-event", …)`.
#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type")]
pub enum PlayerEvent {
    #[serde(rename = "state")]
    StateChanged { state: PlaybackState },

    #[serde(rename = "position")]
    PositionChanged { position: f64, duration: f64 },

    #[serde(rename = "file-loaded")]
    FileLoaded,

    #[serde(rename = "ended")]
    Ended { reason: String },

    #[serde(rename = "shutdown")]
    Shutdown,

    #[serde(rename = "error")]
    Error { message: String },

    #[serde(rename = "tracks-changed")]
    TracksChanged { tracks: Vec<TrackInfo> },

    #[serde(rename = "subtitle-changed")]
    SubtitleChanged { id: i64 },

    #[serde(rename = "audio-changed")]
    AudioChanged { id: i64 },
}

// ---------------------------------------------------------------------------
// Safe pointer wrapper
// ---------------------------------------------------------------------------

#[derive(Clone, Copy)]
struct MpvHandle(*mut mpv_handle);

unsafe impl Send for MpvHandle {}
unsafe impl Sync for MpvHandle {}

impl MpvHandle {
    fn raw(&self) -> *mut mpv_handle {
        self.0
    }
    fn is_null(&self) -> bool {
        self.0.is_null()
    }
}

// ---------------------------------------------------------------------------
// MpvPlayer
// ---------------------------------------------------------------------------

pub struct MpvPlayer {
    handle: Arc<Mutex<MpvHandle>>,
    running: Arc<AtomicBool>,
    app_handle: Arc<Mutex<Option<tauri::AppHandle>>>,
    user_agent: String,
    referer: String,
    start_time: f64,
    sub_file: Option<String>,
}

impl MpvPlayer {
    /// Create a new mpv instance, set options, initialise.
    /// Call `load_url()` afterwards to start playback.
    ///
    /// `window_id` — optional native window handle for mpv `--wid`.
    /// When `None`, mpv creates its own window (headless/audio mode).
    pub fn new(config: &PlayerConfig, window_id: Option<WindowId>) -> Result<Self, String> {
        let handle = unsafe { mpv_create() };
        if handle.is_null() {
            return Err("mpv_create returned null".into());
        }
        let inner = MpvHandle(handle);
        let handle_arc = Arc::new(Mutex::new(inner));

        // Apply window embedding (wid, gpu-context, vo) BEFORE other options.
        if let Some(wid) = &window_id {
            wid.apply_to_mpv(inner.raw())?;
            set_opt(inner, "gpu-context", wid.gpu_context())?;
            set_opt(inner, "vo", wid.vo())?;
        }

        let running = Arc::new(AtomicBool::new(true));

        let player = Self {
            handle: handle_arc,
            running,
            app_handle: Arc::new(Mutex::new(None)),
            user_agent: config.user_agent.clone(),
            referer: config.referer.clone(),
            start_time: config.start_time,
            sub_file: config.sub_file.clone(),
        };

        // Set remaining options before mpv_initialize()
        player.apply_options(config)?;

        let ret = unsafe { mpv_initialize(handle) };
        if ret < 0 {
            return Err(format!("mpv_initialize: {}", mpv_err_str(ret)));
        }

        // Initial volume (set after init so it "sticks").
        player.set_volume(config.volume);

        Ok(player)
    }

    /// Attach the Tauri `AppHandle` (must be called before `load_url`).
    pub fn set_app_handle(&self, app: tauri::AppHandle) {
        *self.app_handle.lock().unwrap() = Some(app);
    }

    /// Load a URL and start the event-loop thread.
    ///
    /// `ended_flag` — optional shared flag set to `true` on playback end (EOF/error).
    /// Used by the host to distinguish natural end from manual close.
    pub fn load_url(
        &self,
        url: &str,
        ended_flag: Option<Arc<AtomicBool>>,
    ) -> Result<(), String> {
        let handle = *self.handle.lock().unwrap();

        // Build http-header-fields before issuing loadfile.
        let mut header_fields = format!("User-Agent: {}", self.user_agent);
        if !self.referer.is_empty() {
            header_fields.push_str(&format!("\r\nReferer: {}", self.referer));
        }
        set_opt(handle, "http-header-fields", &header_fields)?;
        set_opt(handle, "network-timeout", "30")?;
        set_opt(handle, "demuxer-max-bytes", "256MiB")?;
        set_opt(handle, "demuxer-max-back-bytes", "128MiB")?;

        // Observe properties we care about.
        Self::observe_properties(handle);

        // Spawn the event-loop thread.
        let handle_arc = self.handle.clone();
        let running = self.running.clone();
        let app = self.app_handle.lock().unwrap().clone();
        let start_time = self.start_time;
        let sub_file = self.sub_file.clone();
        thread::spawn(move || {
            Self::event_loop(handle_arc, running, app, start_time, sub_file, ended_flag);
        });

        // Issue loadfile command.
        let url_c = CString::new(url).map_err(|e| format!("CString: {e}"))?;
        let loadfile = CString::new("loadfile").unwrap();
        let args: &[*const i8] = &[
            loadfile.as_ptr(),
            url_c.as_ptr(),
            std::ptr::null(),
        ];
        let mut args_mut = args.to_vec();
        let ret = unsafe { mpv_command(raw_handle(handle), args_mut.as_mut_ptr()) };
        if ret < 0 {
            return Err(format!("mpv loadfile: {}", mpv_err_str(ret)));
        }
        Ok(())
    }

    // -----------------------------------------------------------------------
    // Playback control
    // -----------------------------------------------------------------------

    pub fn play(&self) {
        let pos = self.get_position();
        let dur = self.get_duration();
        let at_end = dur > 0.0 && pos >= dur - 1.0;
        // If the file reached EOF (keep-open=always keeps it loaded), seek to 0
        // so the user can restart playback with a single click.
        if at_end {
            self.seek(0.0);
        }
        self.set_flag("pause", false);
        // Seek to current position to force stream reconnect on HLS/HTTP streams.
        // The connection may have become stale during pause.
        if pos > 1.0 && !at_end {
            self.seek(pos);
        }
    }

    pub fn pause(&self) {
        self.set_flag("pause", true);
    }

    pub fn toggle_play(&self) {
        // Use mpv's atomic `cycle` command instead of get_flag + set_flag
        // to eliminate the race condition between read and write.
        self.mpv_command(&["cycle", "pause"]);
    }

    pub fn stop(&self) {
        self.mpv_command(&["stop"]);
    }

    /// Seek to absolute position (seconds).
    pub fn seek(&self, seconds: f64) {
        let sec = format!("{seconds}");
        self.mpv_command(&["seek", &sec, "absolute"]);
    }

    /// Seek relative to current position.
    pub fn seek_relative(&self, seconds: f64) {
        let sec = format!("{seconds}");
        self.mpv_command(&["seek", &sec, "relative+exact"]);
    }

    pub fn set_volume(&self, vol: i64) {
        let vol = vol.clamp(0, 200);
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return;
        }
        let h = raw_handle(handle);
        let name = CString::new("volume").unwrap();
        let mut v = vol;
        unsafe {
            mpv_set_property(
                h,
                name.as_ptr(),
                mpv_format_MPV_FORMAT_INT64,
                &mut v as *mut i64 as *mut libc::c_void,
            );
        }
    }

    pub fn set_speed(&self, speed: f64) {
        let speed = speed.clamp(0.25, 4.0);
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return;
        }
        let h = raw_handle(handle);
        let name = CString::new("speed").unwrap();
        let mut v = speed;
        unsafe {
            mpv_set_property(
                h,
                name.as_ptr(),
                mpv_format_MPV_FORMAT_DOUBLE,
                &mut v as *mut f64 as *mut libc::c_void,
            );
        }
    }

    pub fn set_mute(&self, mute: bool) {
        self.set_flag("mute", mute);
    }

    // -----------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------

    pub fn get_position(&self) -> f64 {
        self.get_double("time-pos").unwrap_or(0.0)
    }

    pub fn get_duration(&self) -> f64 {
        self.get_double("duration").unwrap_or(0.0)
    }

    pub fn get_state(&self) -> PlaybackState {
        let handle = *self.handle.lock().unwrap();
        let paused = get_flag_raw(handle, "pause");
        let idle = get_flag_raw(handle, "idle-active");
        let cache = get_flag_raw(handle, "paused-for-cache");
        let pos = get_double_raw(handle, "time-pos").unwrap_or(0.0);
        let dur = get_double_raw(handle, "duration").unwrap_or(0.0);
        let vol = get_int64_raw(handle, "volume").unwrap_or(100);
        let spd = get_double_raw(handle, "speed").unwrap_or(1.0);
        let mute = get_flag_raw(handle, "mute");

        let state = if idle {
            "idle"
        } else if cache {
            "buffering"
        } else if paused {
            "paused"
        } else {
            "playing"
        };

        PlaybackState {
            state: state.to_string(),
            position: pos.max(0.0),
            duration: dur.max(0.0),
            volume: vol,
            speed: spd,
            mute,
            paused_for_cache: cache,
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    fn apply_options(&self, config: &PlayerConfig) -> Result<(), String> {
        let handle = *self.handle.lock().unwrap();

        let session = cached_session_type();
        let is_wayland = session == SessionType::Wayland;
        let is_nvidia = detect_nvidia_linux();

        let gpu_api = if is_wayland && is_nvidia {
            "opengl"
        } else if is_wayland {
            "vulkan"
        } else {
            "opengl"
        };

        log::info!(
            "mpv: session={:?} nvidia={} gpu-api={}",
            session,
            is_nvidia,
            gpu_api
        );

        let mut opts: Vec<(&str, &str)> = vec![
            ("config", "yes"),
            ("gpu-api", gpu_api),
            ("hwdec", &config.hwdec),
            ("terminal", "no"),
            ("msg-level", "all=error"),
            ("audio-file-auto", "exact"),
            ("sub-auto", "exact"),
            ("volume-max", "200"),
            ("keepaspect-window", "no"),
            ("osc", "no"),
            ("osd-level", "0"),
            ("cache", "yes"),
            ("cache-pause", "yes"),
            ("demuxer-cache-secs", "10"),
            ("audio-buffer-duration", "0.5"),
            ("video-sync", "display-resample"),
            ("keep-open", "always"),
            ("force-seekable", "no"),
            ("framedrop", "decoder+vo"),
            ("vd-lavc-fast", "yes"),
            ("hr-seek", "yes"),
            ("fast-seek", "yes"),
        ];

        if is_wayland {
            opts.push(("wayland-app-id", "com.nuvio.player"));
        }

        for (k, v) in &opts {
            set_opt(handle, k, v)?;
        }

        Ok(())
    }

    fn observe_properties(handle: MpvHandle) {
        let h = raw_handle(handle);
        let props = [
            "time-pos", "duration", "pause", "volume", "speed", "mute",
            "paused-for-cache", "idle-active", "seekable", "eof-reached",
            "track-list", "sid", "aid",
        ];
        for (i, prop) in props.iter().enumerate() {
            let name = CString::new(*prop).unwrap();
            unsafe {
                mpv_observe_property(
                    h,
                    i as u64,
                    name.as_ptr(),
                    mpv_format_MPV_FORMAT_NONE,
                );
            }
        }
    }

    fn event_loop(
        handle_arc: Arc<Mutex<MpvHandle>>,
        running: Arc<AtomicBool>,
        app_handle: Option<tauri::AppHandle>,
        start_time: f64,
        sub_file: Option<String>,
        ended_flag: Option<Arc<AtomicBool>>,
    ) {
        let h = raw_handle(*handle_arc.lock().unwrap());

        let emit = |event: &PlayerEvent| {
            if let Some(ref app) = app_handle {
                let _ = app.emit("player-event", event);
            }
        };

        while running.load(Ordering::SeqCst) {
            let ev = unsafe { mpv_wait_event(h, 0.1) };
            if ev.is_null() {
                continue;
            }
            let event = unsafe { *ev };

            match event.event_id {
                MPV_EVENT_PROPERTY_CHANGE => {
                    let prop = unsafe { &*(event.data as *const mpv_event_property) };
                    Self::on_property(&emit, &handle_arc, prop);
                }

                MPV_EVENT_FILE_LOADED => {
                    emit(&PlayerEvent::FileLoaded);
                    Self::emit_state(&emit, &handle_arc);

                    // Seek to start_time after file is loaded.
                    if start_time > 0.0 {
                        let sec = start_time.to_string();
                        let args = [
                            CString::new("seek").unwrap(),
                            CString::new(sec).unwrap(),
                            CString::new("absolute").unwrap(),
                        ];
                        let mut ptrs: Vec<*const i8> =
                            args.iter().map(|c| c.as_ptr()).collect();
                        ptrs.push(std::ptr::null());
                        unsafe {
                            mpv_command(raw_handle(*handle_arc.lock().unwrap()), ptrs.as_mut_ptr());
                        }
                    }

                    // Load external subtitle file after file is loaded.
                    if let Some(ref path) = sub_file {
                        let path_c = CString::new(path.as_str()).unwrap();
                        let sub_add = CString::new("sub-add").unwrap();
                        let mut ptrs: Vec<*const i8> = vec![
                            sub_add.as_ptr(),
                            path_c.as_ptr(),
                            std::ptr::null(),
                        ];
                        unsafe {
                            mpv_command(raw_handle(*handle_arc.lock().unwrap()), ptrs.as_mut_ptr());
                        }
                    }
                }

                MPV_EVENT_PLAYBACK_RESTART => {
                    Self::emit_state(&emit, &handle_arc);
                }

                MPV_EVENT_END_FILE => {
                    let reason = unsafe {
                        let ef = &*(event.data as *const mpv_event_end_file);
                        match ef.reason as u32 {
                            MPV_END_FILE_REASON_EOF => "eof",
                            MPV_END_FILE_REASON_ERROR => "error",
                            MPV_END_FILE_REASON_STOP => "stop",
                            MPV_END_FILE_REASON_QUIT => "quit",
                            _ => "unknown",
                        }
                    };
                    emit(&PlayerEvent::Ended {
                        reason: reason.into(),
                    });

                    // Signal ended to the host (used for window-close detection).
                    if let Some(ref flag) = ended_flag {
                        flag.store(true, Ordering::SeqCst);
                    }

                    // With keep-open=always, EOF keeps the file loaded so the user
                    // can seek/replay. Only exit on error or explicit quit.
                    if let Some(ref app) = app_handle {
                        match reason {
                            "error" => {
                                let app_clone = app.clone();
                                thread::spawn(move || {
                                    std::thread::sleep(std::time::Duration::from_secs(2));
                                    let _ = app_clone.emit("player-event", &PlayerEvent::Shutdown);
                                    std::process::exit(1);
                                });
                            }
                            "quit" => {
                                running.store(false, Ordering::SeqCst);
                            }
                            // EOF: keep-open=always keeps the file loaded; don't exit.
                            // STOP: event loop stays alive so the user can replay.
                            _ => {}
                        }
                    }
                }

                MPV_EVENT_SHUTDOWN => {
                    emit(&PlayerEvent::Shutdown);
                    running.store(false, Ordering::SeqCst);
                }

                // Silently ignore uninteresting events.
                _ => {}
            }
        }
    }

    fn on_property(
        emit: &impl Fn(&PlayerEvent),
        handle_arc: &Arc<Mutex<MpvHandle>>,
        prop: &mpv_event_property,
    ) {
        if prop.name.is_null() || prop.data.is_null() {
            return;
        }
        let name = unsafe { CStr::from_ptr(prop.name) }
            .to_string_lossy()
            .to_string();

        match name.as_str() {
            "time-pos" | "duration" => {
                if prop.format == mpv_format_MPV_FORMAT_DOUBLE {
                    let handle = *handle_arc.lock().unwrap();
                    let pos = get_double_raw(handle, "time-pos").unwrap_or(0.0);
                    let dur = get_double_raw(handle, "duration").unwrap_or(0.0);
                    emit(&PlayerEvent::PositionChanged {
                        position: pos.max(0.0),
                        duration: dur.max(0.0),
                    });
                }
            }
            "pause" | "paused-for-cache" | "volume" | "speed" | "mute" | "idle-active" => {
                Self::emit_state(emit, handle_arc);
            }
            "track-list" => {
                if prop.format == mpv_format_MPV_FORMAT_NODE {
                    let node = unsafe { &*(prop.data as *const mpv_node) };
                    if let Ok(tracks) = parse_track_list(node) {
                        emit(&PlayerEvent::TracksChanged { tracks });
                    }
                }
            }
            "sid" => {
                if prop.format == mpv_format_MPV_FORMAT_INT64 {
                    let id = unsafe { *(prop.data as *const i64) };
                    emit(&PlayerEvent::SubtitleChanged { id });
                }
            }
            "aid" => {
                if prop.format == mpv_format_MPV_FORMAT_INT64 {
                    let id = unsafe { *(prop.data as *const i64) };
                    emit(&PlayerEvent::AudioChanged { id });
                }
            }
            _ => {}
        }
    }

    fn emit_state(
        emit: &impl Fn(&PlayerEvent),
        handle_arc: &Arc<Mutex<MpvHandle>>,
    ) {
        let handle = *handle_arc.lock().unwrap();
        let paused = get_flag_raw(handle, "pause");
        let idle = get_flag_raw(handle, "idle-active");
        let cache = get_flag_raw(handle, "paused-for-cache");
        let pos = get_double_raw(handle, "time-pos").unwrap_or(0.0);
        let dur = get_double_raw(handle, "duration").unwrap_or(0.0);
        let vol = get_int64_raw(handle, "volume").unwrap_or(100);
        let spd = get_double_raw(handle, "speed").unwrap_or(1.0);
        let mute = get_flag_raw(handle, "mute");

        let state = if idle {
            "idle"
        } else if cache {
            "buffering"
        } else if paused {
            "paused"
        } else {
            "playing"
        };

        emit(&PlayerEvent::StateChanged {
            state: PlaybackState {
                state: state.to_string(),
                position: pos.max(0.0),
                duration: dur.max(0.0),
                volume: vol,
                speed: spd,
                mute,
                paused_for_cache: cache,
            },
        });
    }

    fn set_flag(&self, name: &str, value: bool) {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return;
        }
        set_flag_raw(handle, name, value);
    }

    fn get_flag(&self, name: &str) -> bool {
        let handle = *self.handle.lock().unwrap();
        get_flag_raw(handle, name)
    }

    fn get_double(&self, name: &str) -> Option<f64> {
        let handle = *self.handle.lock().unwrap();
        get_double_raw(handle, name)
    }

    // -----------------------------------------------------------------------
    // Track selection
    // -----------------------------------------------------------------------

    pub fn get_tracks(&self) -> Result<Vec<TrackInfo>, String> {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return Err("Player not initialized".into());
        }
        get_tracks_raw(handle)
    }

    pub fn set_subtitle_track(&self, id: i64) -> Result<(), String> {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return Err("Player not initialized".into());
        }
        set_int_property_raw(handle, "sid", id)
    }

    pub fn set_audio_track(&self, id: i64) -> Result<(), String> {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return Err("Player not initialized".into());
        }
        set_int_property_raw(handle, "aid", id)
    }

    pub fn load_subtitle_file(&self, path: &str) -> Result<(), String> {
        self.mpv_command_result(&["sub-add", path])
    }

    pub fn set_subtitle_delay(&self, delay_ms: i64) -> Result<(), String> {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return Err("Player not initialized".into());
        }
        let sec = delay_ms as f64 / 1000.0;
        set_double_property_raw(handle, "sub-delay", sec)
    }

    pub fn set_audio_delay(&self, delay_ms: i64) -> Result<(), String> {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return Err("Player not initialized".into());
        }
        let sec = delay_ms as f64 / 1000.0;
        set_double_property_raw(handle, "audio-delay", sec)
    }

    /// Issue an mpv command. CStrings are kept alive for the duration of the
    /// mpv_command call because the slice references them.
    fn mpv_command(&self, args: &[&str]) {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return;
        }
        let h = raw_handle(handle);
        let owned: Vec<CString> = args
            .iter()
            .map(|a| CString::new(*a).unwrap())
            .collect();
        let mut ptrs: Vec<*const i8> = owned.iter().map(|c| c.as_ptr()).collect();
        ptrs.push(std::ptr::null());
        unsafe {
            mpv_command(h, ptrs.as_mut_ptr());
        }
    }

    /// Issue an mpv command and return an error on failure.
    fn mpv_command_result(&self, args: &[&str]) -> Result<(), String> {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return Err("Player not initialized".into());
        }
        let h = raw_handle(handle);
        let owned: Vec<CString> = args
            .iter()
            .map(|a| CString::new(*a).map_err(|e| format!("CString: {e}")))
            .collect::<Result<Vec<_>, _>>()?;
        let mut ptrs: Vec<*const i8> = owned.iter().map(|c| c.as_ptr()).collect();
        ptrs.push(std::ptr::null());
        let ret = unsafe { mpv_command(h, ptrs.as_mut_ptr()) };
        if ret < 0 {
            return Err(mpv_err_str(ret));
        }
        Ok(())
    }

    pub fn set_prop_string(&self, name: &str, value: &str) {
        let handle = *self.handle.lock().unwrap();
        if handle.is_null() {
            return;
        }
        set_prop_string_raw(handle, name, value);
    }
}

impl Drop for MpvPlayer {
    fn drop(&mut self) {
        self.running.store(false, Ordering::SeqCst);
        let handle = *self.handle.lock().unwrap();
        if !handle.is_null() {
            unsafe {
                mpv_destroy(raw_handle(handle));
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Free functions — low-level mpv access (no locking, caller must hold lock)
// ---------------------------------------------------------------------------

fn raw_handle(handle: MpvHandle) -> *mut mpv_handle {
    handle.0
}

fn set_opt(handle: MpvHandle, name: &str, value: &str) -> Result<(), String> {
    let h = raw_handle(handle);
    let n = CString::new(name).unwrap();
    let v = CString::new(value).map_err(|e| format!("CString: {e}"))?;
    let ret = unsafe { mpv_set_option_string(h, n.as_ptr(), v.as_ptr()) };
    if ret < 0 {
        return Err(format!("option {name}={value}: {}", mpv_err_str(ret)));
    }
    Ok(())
}

fn set_flag_raw(handle: MpvHandle, name: &str, value: bool) {
    let h = raw_handle(handle);
    let n = CString::new(name).unwrap();
    let mut v: i32 = if value { 1 } else { 0 };
    unsafe {
        mpv_set_property(
            h,
            n.as_ptr(),
            mpv_format_MPV_FORMAT_FLAG,
            &mut v as *mut i32 as *mut libc::c_void,
        );
    }
}

fn set_prop_string_raw(handle: MpvHandle, name: &str, value: &str) {
    let h = raw_handle(handle);
    let n = CString::new(name).unwrap();
    let v = CString::new(value).unwrap();
    unsafe { mpv_set_property_string(h, n.as_ptr(), v.as_ptr()); }
}

fn get_flag_raw(handle: MpvHandle, name: &str) -> bool {
    let h = raw_handle(handle);
    if h.is_null() {
        return false;
    }
    let n = CString::new(name).unwrap();
    let mut v: i64 = 0;
    let ret = unsafe {
        mpv_get_property(
            h,
            n.as_ptr(),
            mpv_format_MPV_FORMAT_FLAG,
            &mut v as *mut i64 as *mut libc::c_void,
        )
    };
    ret >= 0 && v != 0
}

fn get_double_raw(handle: MpvHandle, name: &str) -> Option<f64> {
    let h = raw_handle(handle);
    if h.is_null() {
        return None;
    }
    let n = CString::new(name).unwrap();
    let mut v: f64 = 0.0;
    let ret = unsafe {
        mpv_get_property(
            h,
            n.as_ptr(),
            mpv_format_MPV_FORMAT_DOUBLE,
            &mut v as *mut f64 as *mut libc::c_void,
        )
    };
    if ret >= 0 { Some(v) } else { None }
}

fn get_int64_raw(handle: MpvHandle, name: &str) -> Option<i64> {
    let h = raw_handle(handle);
    if h.is_null() {
        return None;
    }
    let n = CString::new(name).unwrap();
    let mut v: i64 = 0;
    let ret = unsafe {
        mpv_get_property(
            h,
            n.as_ptr(),
            mpv_format_MPV_FORMAT_INT64,
            &mut v as *mut i64 as *mut libc::c_void,
        )
    };
    if ret >= 0 { Some(v) } else { None }
}

fn mpv_err_str(code: i32) -> String {
    let s = unsafe { mpv_error_string(code) };
    if s.is_null() {
        format!("error code {code}")
    } else {
        unsafe { CStr::from_ptr(s) }
            .to_string_lossy()
            .to_string()
    }
}

// ---------------------------------------------------------------------------
// Track-list parsing helpers
// ---------------------------------------------------------------------------

fn get_tracks_raw(handle: MpvHandle) -> Result<Vec<TrackInfo>, String> {
    let h = raw_handle(handle);
    let name = CString::new("track-list").unwrap();
    let mut node: mpv_node = unsafe { std::mem::zeroed() };
    let ret = unsafe {
        mpv_get_property(
            h,
            name.as_ptr(),
            mpv_format_MPV_FORMAT_NODE,
            &mut node as *mut mpv_node as *mut libc::c_void,
        )
    };
    if ret < 0 {
        return Err(format!("get track-list: {}", mpv_err_str(ret)));
    }

    let result = parse_track_list(&node);
    unsafe { mpv_free_node_contents(&mut node) };
    result
}

fn parse_track_list(node: &mpv_node) -> Result<Vec<TrackInfo>, String> {
    if node.format != mpv_format_MPV_FORMAT_NODE_ARRAY {
        return Err("track-list is not a NODE_ARRAY".into());
    }
    let list = unsafe { &*node.u.list };
    let mut tracks = Vec::with_capacity(list.num as usize);

    for i in 0..list.num as usize {
        let entry = unsafe { &*list.values.add(i) };
        if entry.format != mpv_format_MPV_FORMAT_NODE_MAP {
            continue;
        }
        let map = unsafe { &*entry.u.list };

        let mut id: i64 = 0;
        let mut track_type = String::new();
        let mut title = String::new();
        let mut lang = String::new();
        let mut selected = false;

        for j in 0..map.num as usize {
            let key = unsafe { CStr::from_ptr(*map.keys.add(j)) }
                .to_string_lossy()
                .to_string();
            let val = unsafe { &*map.values.add(j) };
            match key.as_str() {
                "id" => id = get_node_int(val),
                "type" => track_type = get_node_string(val),
                "title" => title = get_node_string(val),
                "lang" => lang = get_node_string(val),
                "selected" => selected = get_node_flag(val),
                _ => {}
            }
        }

        tracks.push(TrackInfo {
            id,
            track_type,
            title,
            lang,
            selected,
        });
    }

    Ok(tracks)
}

fn get_node_int(node: &mpv_node) -> i64 {
    if node.format == mpv_format_MPV_FORMAT_INT64 {
        unsafe { node.u.int64 }
    } else {
        0
    }
}

fn get_node_string(node: &mpv_node) -> String {
    if node.format == mpv_format_MPV_FORMAT_STRING {
        let ptr = unsafe { node.u.string };
        if ptr.is_null() {
            String::new()
        } else {
            unsafe { CStr::from_ptr(ptr) }
                .to_string_lossy()
                .to_string()
        }
    } else {
        String::new()
    }
}

fn get_node_flag(node: &mpv_node) -> bool {
    if node.format == mpv_format_MPV_FORMAT_FLAG {
        unsafe { node.u.flag != 0 }
    } else {
        false
    }
}

// ---------------------------------------------------------------------------
// Raw property setters (caller must hold the lock, handle must not be null)
// ---------------------------------------------------------------------------

fn set_int_property_raw(handle: MpvHandle, name: &str, value: i64) -> Result<(), String> {
    let h = raw_handle(handle);
    let n = CString::new(name).unwrap();
    let mut v = value;
    let ret = unsafe {
        mpv_set_property(
            h,
            n.as_ptr(),
            mpv_format_MPV_FORMAT_INT64,
            &mut v as *mut i64 as *mut libc::c_void,
        )
    };
    if ret < 0 {
        return Err(format!("set {name}={value}: {}", mpv_err_str(ret)));
    }
    Ok(())
}

fn set_double_property_raw(
    handle: MpvHandle,
    name: &str,
    value: f64,
) -> Result<(), String> {
    let h = raw_handle(handle);
    let n = CString::new(name).unwrap();
    let mut v = value;
    let ret = unsafe {
        mpv_set_property(
            h,
            n.as_ptr(),
            mpv_format_MPV_FORMAT_DOUBLE,
            &mut v as *mut f64 as *mut libc::c_void,
        )
    };
    if ret < 0 {
        return Err(format!("set {name}={value}: {}", mpv_err_str(ret)));
    }
    Ok(())
}

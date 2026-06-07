use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use tauri::Manager;

mod player;

use player::window::enforce_wayland_env;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    enforce_wayland_env();

    let config = player::config::parse_cli();
    let playback_ended = Arc::new(AtomicBool::new(false));

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .setup(move |app| {
            let app_handle = app.handle().clone();
            let playback_ended_for_load = playback_ended.clone();
            let playback_ended_clone = playback_ended.clone();

            // Extract native window handle for mpv embedding.
            let window_id = app
                .get_webview_window("main")
                .and_then(|w| player::window::extract_window_id(&w));

            if window_id.is_some() {
                log::info!("mpv window embedding active");
            } else {
                log::info!("no window handle — mpv runs headless/audio-only");
            }

            // Initialise the mpv player.
            let player = Arc::new(
                player::mpv::MpvPlayer::new(&config, window_id)
                    .expect("failed to create mpv player"),
            );

            player.set_app_handle(app_handle);

            // Load the stream URL.
            player
                .load_url(&config.url, Some(playback_ended_for_load))
                .expect("failed to load stream URL");

            // Store as managed state (accessible by commands via State<…>).
            app.manage(config.clone());
            app.manage(player);

            // Detect window close — if playback hasn't ended, exit code 2.
            if let Some(window) = app.get_webview_window("main") {
                window.on_window_event(move |event| {
                    if let tauri::WindowEvent::CloseRequested { .. } = event {
                        if !playback_ended_clone.load(Ordering::SeqCst) {
                            std::process::exit(2);
                        }
                    }
                });
            }

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            player::commands::get_title,
            player::commands::play,
            player::commands::pause,
            player::commands::toggle_play,
            player::commands::stop,
            player::commands::seek,
            player::commands::seek_relative,
            player::commands::set_volume,
            player::commands::set_speed,
            player::commands::set_mute,
            player::commands::get_state,
            player::commands::get_position,
            player::commands::get_duration,
            player::commands::get_tracks,
            player::commands::set_subtitle_track,
            player::commands::set_audio_track,
            player::commands::load_subtitle_file,
            player::commands::set_subtitle_delay,
            player::commands::set_audio_delay,
            player::commands::set_hwdec,
            player::commands::open_mpv_config,
        ])
        .run(tauri::generate_context!())
        .expect("error while running nuvio-player");
}

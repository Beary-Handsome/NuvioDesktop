use std::sync::Arc;
use tauri::State;

use super::config::PlayerConfig;
use super::mpv::MpvPlayer;

#[tauri::command]
pub fn get_title(config: State<'_, PlayerConfig>) -> String {
    config.title.clone()
}

#[tauri::command]
pub fn play(player: State<'_, Arc<MpvPlayer>>) -> Result<(), String> {
    player.play();
    Ok(())
}

#[tauri::command]
pub fn pause(player: State<'_, Arc<MpvPlayer>>) -> Result<(), String> {
    player.pause();
    Ok(())
}

#[tauri::command]
pub fn toggle_play(player: State<'_, Arc<MpvPlayer>>) -> Result<(), String> {
    player.toggle_play();
    Ok(())
}

#[tauri::command]
pub fn stop(player: State<'_, Arc<MpvPlayer>>) -> Result<(), String> {
    player.stop();
    Ok(())
}

#[tauri::command]
pub fn seek(player: State<'_, Arc<MpvPlayer>>, seconds: f64) -> Result<(), String> {
    player.seek(seconds);
    Ok(())
}

#[tauri::command]
pub fn seek_relative(player: State<'_, Arc<MpvPlayer>>, seconds: f64) -> Result<(), String> {
    player.seek_relative(seconds);
    Ok(())
}

#[tauri::command]
pub fn set_volume(player: State<'_, Arc<MpvPlayer>>, volume: i64) -> Result<(), String> {
    player.set_volume(volume);
    Ok(())
}

#[tauri::command]
pub fn set_speed(player: State<'_, Arc<MpvPlayer>>, speed: f64) -> Result<(), String> {
    player.set_speed(speed);
    Ok(())
}

#[tauri::command]
pub fn set_mute(player: State<'_, Arc<MpvPlayer>>, mute: bool) -> Result<(), String> {
    player.set_mute(mute);
    Ok(())
}

#[tauri::command]
pub fn get_state(
    player: State<'_, Arc<MpvPlayer>>,
) -> Result<super::mpv::PlaybackState, String> {
    Ok(player.get_state())
}

#[tauri::command]
pub fn get_position(player: State<'_, Arc<MpvPlayer>>) -> Result<f64, String> {
    Ok(player.get_position())
}

#[tauri::command]
pub fn get_duration(player: State<'_, Arc<MpvPlayer>>) -> Result<f64, String> {
    Ok(player.get_duration())
}

// ---------------------------------------------------------------------------
// Track selection
// ---------------------------------------------------------------------------

#[tauri::command]
pub fn get_tracks(
    player: State<'_, Arc<MpvPlayer>>,
) -> Result<Vec<super::mpv::TrackInfo>, String> {
    player.get_tracks()
}

#[tauri::command]
pub fn set_subtitle_track(
    player: State<'_, Arc<MpvPlayer>>,
    id: i64,
) -> Result<(), String> {
    player.set_subtitle_track(id)
}

#[tauri::command]
pub fn set_audio_track(
    player: State<'_, Arc<MpvPlayer>>,
    id: i64,
) -> Result<(), String> {
    player.set_audio_track(id)
}

#[tauri::command]
pub fn set_hwdec(player: State<'_, Arc<MpvPlayer>>, enabled: bool) -> Result<(), String> {
    let mode = if enabled { "auto" } else { "no" };
    player.set_prop_string("hwdec", mode);
    Ok(())
}

#[tauri::command]
pub fn load_subtitle_file(
    player: State<'_, Arc<MpvPlayer>>,
    path: String,
) -> Result<(), String> {
    player.load_subtitle_file(&path)
}

#[tauri::command]
pub fn set_subtitle_delay(
    player: State<'_, Arc<MpvPlayer>>,
    delay_ms: i64,
) -> Result<(), String> {
    player.set_subtitle_delay(delay_ms)
}

#[tauri::command]
pub fn set_audio_delay(
    player: State<'_, Arc<MpvPlayer>>,
    delay_ms: i64,
) -> Result<(), String> {
    player.set_audio_delay(delay_ms)
}

#[tauri::command]
pub fn open_mpv_config() -> Result<(), String> {
    let config_dir = if cfg!(target_os = "windows") {
        std::env::var("APPDATA")
            .map(|a| std::path::PathBuf::from(a).join("mpv"))
            .map_err(|e| format!("APPDATA not set: {e}"))
    } else {
        let home = std::env::var("HOME").map_err(|e| format!("HOME not set: {e}"))?;
        Ok(std::path::PathBuf::from(home).join(".config").join("mpv"))
    }?;

    std::fs::create_dir_all(&config_dir).map_err(|e| format!("mkdir: {e}"))?;

    let config_file = config_dir.join("mpv.conf");
    if !config_file.exists() {
        let template = concat!(
            "# MPV Configuration for Nuvio Player\n",
            "# Add your mpv options here (one per line), e.g.:\n",
            "# hwdec=auto\n",
            "# profile=gpu-hq\n",
            "\n"
        );
        std::fs::write(&config_file, template).map_err(|e| format!("write: {e}"))?;
    }

    if cfg!(target_os = "windows") {
        std::process::Command::new("notepad.exe")
            .arg(config_file.as_os_str())
            .spawn()
            .map_err(|e| format!("launch notepad: {e}"))?;
    } else if cfg!(target_os = "macos") {
        std::process::Command::new("open")
            .arg(config_file.as_os_str())
            .spawn()
            .map_err(|e| format!("launch open: {e}"))?;
    } else {
        std::process::Command::new("xdg-open")
            .arg(config_file.as_os_str())
            .spawn()
            .map_err(|e| format!("launch xdg-open: {e}"))?;
    }

    Ok(())
}

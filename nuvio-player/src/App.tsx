import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Play, Pause } from "lucide-react";
import { usePlayer } from "./hooks/usePlayer";
import { useMouseActivity } from "./hooks/useMouseActivity";
import { useKeyboard } from "./hooks/useKeyboard";
import TitleBar from "./components/TitleBar";
import VideoArea from "./components/VideoArea";
import ControlsOverlay from "./components/ControlsOverlay";
import "./App.css";

function App() {
  const { state, tracks, actions } = usePlayer();
  const controlsVisible = useMouseActivity(8000);
  useKeyboard(actions, state);

  const [title, setTitle] = useState("");
  const [menusOpen, setMenusOpen] = useState(false);

  useEffect(() => {
    invoke<string>("get_title").then(setTitle).catch(() => {});
  }, []);

  const isPaused = state.state === "paused" || state.state === "idle";
  // Keep UI visible when paused (so user can always reach play/resume)
  const uiVisible = controlsVisible || isPaused || menusOpen;
  const playing = state.state === "playing" && !state.paused_for_cache;

  return (
    <div className="app-root">
      <TitleBar title={title} />
      <div className="player-area">
        <VideoArea state={state} />

        <div className={`center-play-btn ${uiVisible ? "visible" : "hidden"}`} onClick={actions.togglePlay}>
          <button className="play-btn-large">
            {playing ? <Pause size={56} /> : <Play size={56} />}
          </button>
        </div>

        <ControlsOverlay
          visible={uiVisible}
          state={state}
          tracks={tracks}
          onSeek={actions.seek}
          onSeekRelative={actions.seekRelative}
          onTogglePlay={actions.togglePlay}
          onSetVolume={actions.setVolume}
          onToggleMute={actions.toggleMute}
          onSetSpeed={actions.setSpeed}
          onSetSubtitle={actions.setSubtitleTrack}
          onSetAudio={actions.setAudioTrack}
          onMenuOpenChange={setMenusOpen}
        />
      </div>
    </div>
  );
}

export default App;

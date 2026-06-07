import { useRef, useEffect, useState } from "react";
import { Maximize, Minimize, FileText } from "lucide-react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { invoke } from "@tauri-apps/api/core";
import ProgressBar from "./ProgressBar";
import PlaybackControls from "./PlaybackControls";
import VolumeControl from "./VolumeControl";
import TrackSelector from "./TrackSelector";
import SettingsPanel from "./SettingsPanel";
import type { PlaybackState, TrackInfo } from "../types/player";

interface Props {
  visible: boolean;
  state: PlaybackState;
  tracks: TrackInfo[];
  onSeek: (s: number) => void;
  onSeekRelative: (s: number) => void;
  onTogglePlay: () => void;
  onSetVolume: (v: number) => void;
  onToggleMute: () => void;
  onSetSpeed: (s: number) => void;
  onSetSubtitle: (id: number) => void;
  onSetAudio: (id: number) => void;
  onMenuOpenChange?: (open: boolean) => void;
  onAudioClick?: () => void;
  onSettingsClick?: () => void;
}

export default function ControlsOverlay({
  visible,
  state,
  tracks,
  onSeek,
  onSeekRelative,
  onTogglePlay,
  onSetVolume,
  onToggleMute,
  onSetSpeed,
  onSetSubtitle,
  onSetAudio,
  onMenuOpenChange,
  onAudioClick,
  onSettingsClick,
}: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    if (ref.current) {
      ref.current.style.opacity = visible ? "1" : "0";
      ref.current.style.pointerEvents = visible ? "auto" : "none";
    }
  }, [visible]);

  const toggleFullscreen = async () => {
    const win = getCurrentWindow();
    const fs = await win.isFullscreen();
    await win.setFullscreen(!fs);
    setIsFullscreen(!fs);
  };

  // Track fullscreen changes via keyboard (F / Escape)
  useEffect(() => {
    const check = () => getCurrentWindow().isFullscreen().then(setIsFullscreen);
    const id = setInterval(check, 2000);
    check();
    return () => clearInterval(id);
  }, []);

  return (
    <div ref={ref} className="controls-overlay">
      <ProgressBar state={state} onSeek={onSeek} />
      <div className="controls-row">
        <PlaybackControls
          state={state}
          onTogglePlay={onTogglePlay}
          onSeekRelative={onSeekRelative}
          onSetSpeed={onSetSpeed}
        />
        <VolumeControl state={state} onSetVolume={onSetVolume} onToggleMute={onToggleMute} />
        <div className="controls-spacer" />
        <TrackSelector
          tracks={tracks}
          onSetSubtitle={onSetSubtitle}
          onSetAudio={onSetAudio}
          onAudioClick={onAudioClick}
        />
        <SettingsPanel
          state={state}
          onSetSpeed={onSetSpeed}
          onOpenChange={onMenuOpenChange}
          onSettingsClick={onSettingsClick}
        />
        <button className="ctrl-btn" onClick={() => invoke("open_mpv_config")} title="Edit mpv.conf">
          <FileText size={20} />
        </button>
        <button className="ctrl-btn" onClick={toggleFullscreen} title={isFullscreen ? "Exit fullscreen" : "Fullscreen"}>
          {isFullscreen ? <Minimize size={20} /> : <Maximize size={20} />}
        </button>
      </div>
    </div>
  );
}

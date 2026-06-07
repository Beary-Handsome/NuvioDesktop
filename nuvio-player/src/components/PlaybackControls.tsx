import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
} from "lucide-react";
import type { PlaybackState } from "../types/player";

const SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3, 4];

interface Props {
  state: PlaybackState;
  onTogglePlay: () => void;
  onSeekRelative: (sec: number) => void;
  onSetSpeed: (speed: number) => void;
}

export default function PlaybackControls({
  state,
  onTogglePlay,
  onSeekRelative,
  onSetSpeed,
}: Props) {
  const playing = state.state === "playing" && !state.paused_for_cache;

  return (
    <div className="playback-controls">
      <button className="ctrl-btn" onClick={() => onSeekRelative(-10)} title="Rewind 10s">
        <SkipBack size={20} />
      </button>

      <button className="ctrl-btn play-btn" onClick={onTogglePlay} title={playing ? "Pause" : "Play"}>
        {playing ? <Pause size={24} /> : <Play size={24} />}
      </button>

      <button className="ctrl-btn" onClick={() => onSeekRelative(10)} title="Forward 10s">
        <SkipForward size={20} />
      </button>

      <select
        className="speed-select"
        value={state.speed}
        onChange={(e) => onSetSpeed(Number(e.target.value))}
        title="Playback speed"
      >
        {SPEEDS.map((s) => (
          <option key={s} value={s}>
            {s === 1 ? "1x" : `${s}x`}
          </option>
        ))}
      </select>
    </div>
  );
}

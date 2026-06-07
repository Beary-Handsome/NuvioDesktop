import { Volume2, Volume1, VolumeX } from "lucide-react";
import type { PlaybackState } from "../types/player";

interface Props {
  state: PlaybackState;
  onSetVolume: (vol: number) => void;
  onToggleMute: () => void;
}

export default function VolumeControl({ state, onSetVolume, onToggleMute }: Props) {
  const vol = state.mute ? 0 : state.volume;
  const Icon = state.mute || vol === 0 ? VolumeX : vol < 50 ? Volume1 : Volume2;

  return (
    <div className="volume-control">
      <button className="ctrl-btn" onClick={onToggleMute} title={state.mute ? "Unmute" : "Mute"}>
        <Icon size={20} />
      </button>
      <input
        type="range"
        className="volume-slider"
        min={0}
        max={200}
        value={vol}
        onChange={(e) => onSetVolume(Number(e.target.value))}
        title={`Volume: ${vol}%`}
      />
    </div>
  );
}

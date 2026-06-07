import { Subtitles, Mic } from "lucide-react";
import type { TrackInfo } from "../types/player";

interface Props {
  tracks: TrackInfo[];
  onSetSubtitle: (id: number) => void;
  onSetAudio: (id: number) => void;
  onAudioClick?: () => void;
}

export default function TrackSelector({ tracks, onSetSubtitle, onSetAudio, onAudioClick }: Props) {
  const subs = tracks.filter((t) => t.type === "sub");
  const audios = tracks.filter((t) => t.type === "audio");

  if (subs.length === 0 && audios.length === 0) return null;

  return (
    <div className="track-selector">
      {audios.length > 1 && !onAudioClick && (
        <div className="track-group">
          <Mic size={16} />
          <select
            className="track-select"
            onChange={(e) => onSetAudio(Number(e.target.value))}
            title="Audio track"
          >
            {audios.map((t) => (
              <option key={t.id} value={t.id}>
                {t.title || t.lang || `Track ${t.id}`}
                {t.selected ? " ✓" : ""}
              </option>
            ))}
          </select>
        </div>
      )}

      {audios.length > 1 && onAudioClick && (
        <button className="ctrl-btn" onClick={onAudioClick} title="Audio tracks">
          <Mic size={20} />
        </button>
      )}

      {subs.length > 0 && (
        <div className="track-group">
          <Subtitles size={16} />
          <select
            className="track-select"
            defaultValue="off"
            onChange={(e) => {
              const val = e.target.value;
              if (val === "off") onSetSubtitle(-1);
              else onSetSubtitle(Number(val));
            }}
            title="Subtitles"
          >
            <option value="off">Off</option>
            {subs.map((t) => (
              <option key={t.id} value={t.id}>
                {t.title || t.lang || `Track ${t.id}`}
                {t.selected ? " ✓" : ""}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  );
}

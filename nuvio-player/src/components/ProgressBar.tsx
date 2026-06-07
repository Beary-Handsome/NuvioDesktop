import { useRef, useCallback, useState } from "react";
import type { PlaybackState } from "../types/player";

function fmt(sec: number): string {
  if (!isFinite(sec) || sec < 0) sec = 0;
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = Math.floor(sec % 60);
  const mm = String(m).padStart(2, "0");
  const ss = String(s).padStart(2, "0");
  if (h > 0) return `${h}:${mm}:${ss}`;
  return `${mm}:${ss}`;
}

interface Props {
  state: PlaybackState;
  onSeek: (seconds: number) => void;
}

export default function ProgressBar({ state, onSeek }: Props) {
  const barRef = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState(false);

  const seekFromEvent = useCallback(
    (clientX: number) => {
      const rect = barRef.current?.getBoundingClientRect();
      if (!rect || state.duration <= 0) return;
      const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
      onSeek(ratio * state.duration);
    },
    [state.duration, onSeek]
  );

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      setDragging(true);
      seekFromEvent(e.clientX);
    },
    [seekFromEvent]
  );

  const handleMouseMove = useCallback(
    (e: React.MouseEvent) => {
      if (dragging) seekFromEvent(e.clientX);
    },
    [dragging, seekFromEvent]
  );

  const handleMouseUp = useCallback(() => {
    setDragging(false);
  }, []);

  const ratio = state.duration > 0 ? state.position / state.duration : 0;
  const buffering = state.paused_for_cache;

  return (
    <div className="progress-bar-container">
      <span className="progress-time">{fmt(state.position)}</span>
      <div
        ref={barRef}
        className={`progress-bar ${buffering ? "buffering" : ""}`}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        <div className="progress-track">
          <div className="progress-fill" style={{ width: `${ratio * 100}%` }} />
          <div className="progress-thumb" style={{ left: `${ratio * 100}%` }} />
        </div>
      </div>
      <span className="progress-time">{fmt(state.duration)}</span>
    </div>
  );
}

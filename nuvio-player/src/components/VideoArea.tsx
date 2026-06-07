import type { PlaybackState } from "../types/player";

interface Props {
  state: PlaybackState;
}

export default function VideoArea({ state }: Props) {
  const idle = state.state === "idle";
  const buffering = state.paused_for_cache;

  return (
    <div className="video-area">
      {idle && (
        <div className="video-placeholder">
          <svg className="nuvio-logo-large" viewBox="0 0 24 24" width="64" height="64" fill="#E5383B">
            <circle cx="12" cy="12" r="10" />
            <polygon points="10,7 17,12 10,17" fill="#000" />
          </svg>
          <p className="placeholder-text">Nuvio Player</p>
        </div>
      )}
      {buffering && !idle && (
        <div className="buffering-indicator" />
      )}
    </div>
  );
}

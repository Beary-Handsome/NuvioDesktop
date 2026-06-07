export interface PlaybackState {
  position: number;
  duration: number;
  volume: number;
  speed: number;
  state: string;
  mute: boolean;
  paused_for_cache: boolean;
}

export interface TrackInfo {
  id: number;
  type: string;
  title: string;
  lang: string;
  selected: boolean;
}

export type PlayerEvent =
  | { type: "state"; state: PlaybackState }
  | { type: "position"; position: number; duration: number }
  | { type: "file-loaded" }
  | { type: "tracks-changed"; tracks: TrackInfo[] }
  | { type: "ended"; reason: string }
  | { type: "shutdown" }
  | { type: "error"; message: string };

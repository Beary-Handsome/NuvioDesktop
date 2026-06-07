import { useState, useEffect, useCallback, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen, UnlistenFn } from "@tauri-apps/api/event";

import type { PlaybackState, TrackInfo, PlayerEvent } from "../types/player";

export interface PlayerActions {
  play: () => Promise<void>;
  pause: () => Promise<void>;
  togglePlay: () => Promise<void>;
  seek: (seconds: number) => Promise<void>;
  seekRelative: (seconds: number) => Promise<void>;
  setVolume: (volume: number) => Promise<void>;
  setSpeed: (speed: number) => Promise<void>;
  toggleMute: () => Promise<void>;
  setSubtitleTrack: (id: number) => Promise<void>;
  setAudioTrack: (id: number) => Promise<void>;
}

export function usePlayer() {
  const [state, setState] = useState<PlaybackState>({
    position: 0,
    duration: 0,
    volume: 100,
    speed: 1.0,
    state: "idle",
    mute: false,
    paused_for_cache: false,
  });
  const [tracks, setTracks] = useState<TrackInfo[]>([]);
  const [fileLoaded, setFileLoaded] = useState(false);
  const stateRef = useRef(state);
  const lastToggleRef = useRef(0);
  const wakeLockRef = useRef<WakeLockSentinel | null>(null);
  const wakeLockDesiredRef = useRef(false);
  stateRef.current = state;

  useEffect(() => {
    invoke<PlaybackState>("get_state").then(setState).catch(console.error);
    invoke<TrackInfo[]>("get_tracks").then(setTracks).catch(console.error);
  }, []);

  useEffect(() => {
    let unlisten: UnlistenFn | undefined;
    let cancelled = false;

    listen<PlayerEvent>("player-event", (event) => {
      if (cancelled) return;
      const payload = event.payload;
      switch (payload.type) {
        case "state":
          setState(payload.state);
          break;
        case "position":
          setState((prev) => ({
            ...prev,
            position: payload.position,
            duration: payload.duration,
          }));
          break;
        case "file-loaded":
          setFileLoaded(true);
          break;
        case "tracks-changed":
          setTracks(payload.tracks);
          break;
        case "shutdown":
          break;
      }
    }).then((fn) => { unlisten = fn; });

    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, []);

  useEffect(() => {
    const isPlaying = state.state === "playing" && !state.paused_for_cache;
    wakeLockDesiredRef.current = isPlaying;

    if (isPlaying && !wakeLockRef.current) {
      navigator.wakeLock.request("screen").then((sentinel) => {
        sentinel.addEventListener("release", () => {
          wakeLockRef.current = null;
          if (wakeLockDesiredRef.current) {
            navigator.wakeLock.request("screen").then((s) => {
              s.addEventListener("release", () => { wakeLockRef.current = null; });
              wakeLockRef.current = s;
            }).catch(() => {});
          }
        });
        wakeLockRef.current = sentinel;
      }).catch(() => {});
    } else if (!isPlaying && wakeLockRef.current) {
      wakeLockRef.current.release().catch(() => {});
      wakeLockRef.current = null;
    }
  }, [state.state, state.paused_for_cache]);

  useEffect(() => {
    return () => {
      wakeLockRef.current?.release().catch(() => {});
    };
  }, []);

  const actions: PlayerActions = {
    play: useCallback(async () => {
      await invoke("play");
    }, []),

    pause: useCallback(async () => {
      await invoke("pause");
    }, []),

    togglePlay: useCallback(async () => {
      // Debounce: ignore clicks within 200ms to avoid flooding mpv
      const now = Date.now();
      if (lastToggleRef.current && now - lastToggleRef.current < 200) return;
      lastToggleRef.current = now;

      const cur = stateRef.current;
      const wasPlaying = cur.state === "playing" && !cur.paused_for_cache;
      setState((prev) => ({
        ...prev,
        state: wasPlaying ? "paused" : "playing",
      }));
      invoke("toggle_play").catch(console.error);
    }, []),

    seek: useCallback(async (seconds: number) => {
      await invoke("seek", { seconds });
    }, []),

    seekRelative: useCallback(async (seconds: number) => {
      await invoke("seek_relative", { seconds });
    }, []),

    setVolume: useCallback(async (volume: number) => {
      await invoke("set_volume", { volume: Math.round(volume) });
      setState((prev) => ({ ...prev, volume: Math.round(volume) }));
    }, []),

    setSpeed: useCallback(async (speed: number) => {
      await invoke("set_speed", { speed });
      setState((prev) => ({ ...prev, speed }));
    }, []),

    toggleMute: useCallback(async () => {
      const newMute = !stateRef.current.mute;
      await invoke("set_mute", { mute: newMute });
      setState((prev) => ({ ...prev, mute: newMute }));
    }, []),

    setSubtitleTrack: useCallback(async (id: number) => {
      await invoke("set_subtitle_track", { id });
    }, []),

    setAudioTrack: useCallback(async (id: number) => {
      await invoke("set_audio_track", { id });
    }, []),
  };

  return { state, tracks, fileLoaded, actions };
}

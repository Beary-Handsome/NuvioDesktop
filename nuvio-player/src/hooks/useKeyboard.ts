import { useEffect } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import type { PlayerActions } from "./usePlayer";

export function useKeyboard(actions: PlayerActions, state: { volume: number }) {
  useEffect(() => {
    const handleKeyDown = async (e: KeyboardEvent) => {
      const win = getCurrentWindow();
      const isFullscreen = await win.isFullscreen();

      switch (e.key) {
        case " ":
          e.preventDefault();
          await actions.togglePlay();
          break;

        case "f":
        case "F":
          await win.setFullscreen(!isFullscreen);
          break;

        case "m":
        case "M":
          await actions.toggleMute();
          break;

        case "ArrowLeft":
          e.preventDefault();
          await actions.seekRelative(e.shiftKey ? -60 : -5);
          break;

        case "ArrowRight":
          e.preventDefault();
          await actions.seekRelative(e.shiftKey ? 60 : 5);
          break;

        case "ArrowUp":
          e.preventDefault();
          await actions.setVolume(Math.min(state.volume + 10, 200));
          break;

        case "ArrowDown":
          e.preventDefault();
          await actions.setVolume(Math.max(state.volume - 10, 0));
          break;

        case "Escape":
          if (isFullscreen) {
            await win.setFullscreen(false);
          }
          break;
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [actions, state.volume]);
}

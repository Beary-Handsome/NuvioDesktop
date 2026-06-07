import { useState, useEffect, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Settings, Cpu } from "lucide-react";
import type { PlaybackState } from "../types/player";

const SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3, 4];

interface Props {
  state: PlaybackState;
  onSetSpeed: (speed: number) => void;
  onOpenChange?: (open: boolean) => void;
  onSettingsClick?: () => void;
}

export default function SettingsPanel({ state, onSetSpeed, onOpenChange, onSettingsClick }: Props) {
  const [open, setOpen] = useState(false);
  const [hwdec, setHwdec] = useState(true);
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    onOpenChange?.(open);
  }, [open, onOpenChange]);

  // Click outside to close
  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    requestAnimationFrame(() => document.addEventListener("click", handleClick));
    return () => document.removeEventListener("click", handleClick);
  }, [open]);

  const toggleHwdec = () => {
    const next = !hwdec;
    setHwdec(next);
    invoke("set_hwdec", { enabled: next });
  };

  // External handler mode: simple button that delegates to parent
  if (onSettingsClick) {
    return (
      <div className="settings-panel">
        <button className="ctrl-btn" onClick={onSettingsClick} title="Settings">
          <Settings size={20} />
        </button>
      </div>
    );
  }

  // Inline dropdown mode
  return (
    <div className="settings-panel" ref={panelRef}>
      <button className="ctrl-btn" onClick={() => setOpen((o) => !o)} title="Settings">
        <Settings size={20} />
      </button>

      {open && (
        <div className="settings-dropdown">
          <div className="settings-section-label">Speed</div>
          {SPEEDS.map((s) => (
            <button
              key={s}
              className={`settings-item ${s === state.speed ? "active" : ""}`}
              onClick={() => { onSetSpeed(s); setOpen(false); }}
            >
              {s === 1 ? "Normal (1x)" : `${s}x`}
            </button>
          ))}
          <div className="settings-divider" />
          <div className="settings-section-label">Hardware Acceleration</div>
          <button
            className={`settings-item ${hwdec ? "active" : ""}`}
            onClick={toggleHwdec}
          >
            <Cpu size={16} />
            {hwdec ? "Auto" : "Disabled"}
          </button>
          <div className="settings-divider" />
          <button className="settings-item dim" onClick={() => setOpen(false)}>
            Video settings...
          </button>
        </div>
      )}
    </div>
  );
}

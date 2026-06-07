import { getCurrentWindow } from "@tauri-apps/api/window";
import { Minus, X } from "lucide-react";

interface Props {
  title?: string;
}

export default function TitleBar({ title }: Props) {
  const handleMinimize = () => {
    getCurrentWindow().minimize();
  };

  const handleClose = () => {
    getCurrentWindow().close();
  };

  return (
    <div className="title-bar" data-tauri-drag-region>
      <div className="title-bar-left">
        <svg className="nuvio-logo" viewBox="0 0 24 24" width="22" height="22" fill="#E5383B">
          <circle cx="12" cy="12" r="10" />
          <polygon points="10,7 17,12 10,17" fill="#000" />
        </svg>
        <span className="title-text">{title || "Nuvio Player"}</span>
      </div>
      <div className="title-bar-right">
        <button className="title-btn" onClick={handleMinimize} title="Minimize">
          <Minus size={16} />
        </button>
        <button className="title-btn title-btn-close" onClick={handleClose} title="Close">
          <X size={16} />
        </button>
      </div>
    </div>
  );
}

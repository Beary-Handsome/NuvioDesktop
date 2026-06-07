import { useState, useEffect, useRef } from "react";

export function useMouseActivity(hideDelay = 3000) {
  const [visible, setVisible] = useState(true);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const hasMoved = useRef(false);

  useEffect(() => {
    const handleMouseMove = () => {
      // Match old player: don't start timer until first mouse move
      if (!hasMoved.current) {
        hasMoved.current = true;
      }
      setVisible(true);
      clearTimeout(timer.current);
      timer.current = setTimeout(() => setVisible(false), hideDelay);
    };

    const handleMouseLeave = () => {
      if (!hasMoved.current) return;
      clearTimeout(timer.current);
      timer.current = setTimeout(() => setVisible(false), 500);
    };

    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseleave", handleMouseLeave);

    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseleave", handleMouseLeave);
      clearTimeout(timer.current);
    };
  }, [hideDelay]);

  return visible;
}

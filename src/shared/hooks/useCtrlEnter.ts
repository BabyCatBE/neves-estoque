import { useEffect, useRef } from "react";

export function useCtrlEnter(action: () => void, enabled = true) {
  const actionRef = useRef(action);

  useEffect(() => {
    actionRef.current = action;
  }, [action]);

  useEffect(() => {
    if (!enabled) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.repeat) return;
      if (!(event.ctrlKey || event.metaKey) || event.key !== "Enter") return;

      event.preventDefault();
      event.stopPropagation();
      actionRef.current();
    };

    document.addEventListener("keydown", handleKeyDown, true);
    return () => document.removeEventListener("keydown", handleKeyDown, true);
  }, [enabled]);
}

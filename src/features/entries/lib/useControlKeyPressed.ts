import { useEffect, useRef } from "react";

export function useControlKeyPressed() {
  const pressed = useRef(false);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (
        event.key === "Control" ||
        event.code === "ControlLeft" ||
        event.code === "ControlRight"
      ) {
        pressed.current = true;
      }
    };

    const onKeyUp = (event: KeyboardEvent) => {
      if (
        event.key === "Control" ||
        event.code === "ControlLeft" ||
        event.code === "ControlRight"
      ) {
        pressed.current = false;
      }
    };

    const clear = () => {
      pressed.current = false;
    };

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    window.addEventListener("blur", clear);

    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("blur", clear);
    };
  }, []);

  return pressed;
}

import type { KeyboardEvent as ReactKeyboardEvent } from "react";

export function handleDialogButtonArrowNavigation(
  event: ReactKeyboardEvent<HTMLElement>
) {
  if (
    event.key !== "ArrowLeft" &&
    event.key !== "ArrowRight" &&
    event.key !== "ArrowUp" &&
    event.key !== "ArrowDown"
  ) {
    return;
  }

  const buttons = Array.from(
    event.currentTarget.querySelectorAll<HTMLButtonElement>("button:not(:disabled)")
  );
  if (!buttons.length) return;

  const currentIndex = buttons.findIndex((button) => button === document.activeElement);
  const direction = event.key === "ArrowLeft" || event.key === "ArrowUp" ? -1 : 1;
  const baseIndex = currentIndex >= 0 ? currentIndex : 0;
  const nextIndex = (baseIndex + direction + buttons.length) % buttons.length;

  event.preventDefault();
  event.stopPropagation();
  buttons[nextIndex]?.focus();
}

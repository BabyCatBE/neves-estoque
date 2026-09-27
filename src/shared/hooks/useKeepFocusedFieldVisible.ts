import { useEffect } from "react";

function isEditableControl(target: EventTarget | null): target is HTMLElement {
  if (
    !(target instanceof HTMLInputElement) &&
    !(target instanceof HTMLTextAreaElement) &&
    !(target instanceof HTMLSelectElement)
  ) {
    return false;
  }

  if (target instanceof HTMLInputElement) {
    const ignoredTypes = new Set([
      "button",
      "checkbox",
      "color",
      "file",
      "hidden",
      "radio",
      "range",
      "reset",
      "submit"
    ]);
    if (ignoredTypes.has(target.type)) return false;
  }

  return !target.hasAttribute("disabled") && !target.hasAttribute("readonly");
}

function revealIfNeeded(element: HTMLElement) {
  const rect = element.getBoundingClientRect();
  const viewportHeight = window.visualViewport?.height ?? window.innerHeight;
  const safeTop = 88;
  const safeBottom = Math.max(safeTop + 80, viewportHeight - 40);

  if (rect.top < safeTop || rect.bottom > safeBottom) {
    element.scrollIntoView({
      block: "center",
      inline: "nearest",
      behavior: "smooth"
    });
  }
}

export function useKeepFocusedFieldVisible() {
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | null = null;

    const onFocusIn = (event: FocusEvent) => {
      if (!isEditableControl(event.target)) return;
      if (timer) clearTimeout(timer);

      const element = event.target;
      timer = setTimeout(() => revealIfNeeded(element), 180);
    };

    document.addEventListener("focusin", onFocusIn);
    return () => {
      document.removeEventListener("focusin", onFocusIn);
      if (timer) clearTimeout(timer);
    };
  }, []);
}

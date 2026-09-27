export async function copyText(text: string) {
  if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch {
      // Em HTTP/LAN a Clipboard API pode existir, mas negar a operação.
      // Nesse caso, usamos o fallback compatível abaixo.
    }
  }

  if (typeof document === "undefined") {
    throw new Error("Cópia indisponível.");
  }

  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.setAttribute("readonly", "");
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  textarea.style.pointerEvents = "none";
  textarea.style.left = "-9999px";

  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  textarea.setSelectionRange(0, textarea.value.length);

  try {
    const copied = document.execCommand("copy");
    if (!copied) throw new Error("Cópia indisponível.");
  } finally {
    document.body.removeChild(textarea);
  }
}

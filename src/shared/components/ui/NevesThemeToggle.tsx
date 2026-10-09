import { useNevesTheme } from "../../theme/ThemeProvider";

/** Mesmo conceito do controle Android: sol à esquerda, lua à direita, seletor vermelho. */
export function NevesThemeToggle() {
  const { mode, setMode } = useNevesTheme();
  const dark = mode === "dark";

  return (
    <button
      type="button"
      role="switch"
      aria-label="Tema escuro"
      aria-checked={dark}
      title={dark ? "Alterar para tema claro" : "Alterar para tema escuro"}
      onClick={() => setMode(dark ? "light" : "dark")}
      className="neves-theme-toggle"
    >
      <span className="neves-theme-toggle__thumb" aria-hidden="true" />
      <svg className="neves-theme-toggle__sun" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42M17.65 6.35l1.42-1.42" />
      </svg>
      <svg className="neves-theme-toggle__moon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M20.5 14.1A8.7 8.7 0 0 1 9.9 3.5 8.7 8.7 0 1 0 20.5 14.1Z" />
      </svg>
    </button>
  );
}

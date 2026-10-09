/** Paridade com AppearanceMode do Android: apenas light e dark, padrão light. */
export type NevesThemeMode = "light" | "dark";

export const THEME_STORAGE_KEY = "neves_estoque_appearance.theme_mode_v1";

export function normalizeThemeMode(value: string | null | undefined): NevesThemeMode {
  return value === "dark" ? "dark" : "light";
}

export function readThemeMode(storage: Pick<Storage, "getItem"> | null): NevesThemeMode {
  try {
    return normalizeThemeMode(storage?.getItem(THEME_STORAGE_KEY));
  } catch {
    // O modo claro continua utilizável quando o armazenamento local está bloqueado.
    return "light";
  }
}

export function writeThemeMode(storage: Pick<Storage, "setItem"> | null, mode: NevesThemeMode): void {
  try {
    storage?.setItem(THEME_STORAGE_KEY, mode);
  } catch {
    // Preferência da sessão ainda funciona em navegadores com storage indisponível.
  }
}

export function readBrowserTheme(): NevesThemeMode {
  if (typeof window === "undefined") return "light";
  try {
    return readThemeMode(window.localStorage);
  } catch {
    return "light";
  }
}

export function persistBrowserTheme(mode: NevesThemeMode): void {
  if (typeof window === "undefined") return;
  try {
    writeThemeMode(window.localStorage, mode);
  } catch {
    // Não bloquear a interface se o navegador restringir localStorage.
  }
}

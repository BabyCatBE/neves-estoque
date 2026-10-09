import { createContext, useContext, useLayoutEffect, useMemo, useState, type PropsWithChildren } from "react";
import { persistBrowserTheme, readBrowserTheme, type NevesThemeMode } from "./appearance";

type ThemeContextValue = {
  mode: NevesThemeMode;
  setMode: (value: NevesThemeMode) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

/** Preferência manual por navegador, independente de login e do tema do SO. */
export function ThemeProvider({ children }: PropsWithChildren) {
  const [mode, setMode] = useState<NevesThemeMode>(readBrowserTheme);

  useLayoutEffect(() => {
    document.documentElement.dataset.nevesTheme = mode;
    persistBrowserTheme(mode);
  }, [mode]);

  const value = useMemo(() => ({ mode, setMode }), [mode]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useNevesTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useNevesTheme exige ThemeProvider.");
  return context;
}

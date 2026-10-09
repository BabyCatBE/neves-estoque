import { describe, expect, it } from "vitest";
import { normalizeThemeMode, readThemeMode, THEME_STORAGE_KEY, writeThemeMode } from "./appearance";

describe("preferência visual Neves Web", () => {
  it("usa claro como padrão para valor ausente ou desconhecido", () => {
    expect(normalizeThemeMode(undefined)).toBe("light");
    expect(normalizeThemeMode(null)).toBe("light");
    expect(normalizeThemeMode("auto")).toBe("light");
    expect(normalizeThemeMode("dark")).toBe("dark");
  });

  it("lê e grava somente claro ou escuro no armazenamento local", () => {
    const values = new Map<string, string>();
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => { values.set(key, value); }
    };
    expect(readThemeMode(storage)).toBe("light");
    writeThemeMode(storage, "dark");
    expect(values.get(THEME_STORAGE_KEY)).toBe("dark");
    expect(readThemeMode(storage)).toBe("dark");
  });

  it("não bloqueia a interface quando localStorage está indisponível", () => {
    expect(readThemeMode({ getItem: () => { throw new Error("blocked"); } })).toBe("light");
    expect(() => writeThemeMode({ setItem: () => { throw new Error("blocked"); } }, "dark")).not.toThrow();
  });
});

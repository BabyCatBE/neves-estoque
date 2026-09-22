import { describe, expect, it } from "vitest";
import {
  buildEffectiveAt,
  localDateInputValue,
  parseOptionalPrice,
  parsePositiveDecimal
} from "./entryValidation";

describe("entryValidation", () => {
  it("aceita quantidade com vírgula", () => {
    expect(parsePositiveDecimal("2,5", "Quantidade")).toBe(2.5);
  });

  it("bloqueia quantidade zero", () => {
    expect(() => parsePositiveDecimal("0", "Quantidade")).toThrow();
  });

  it("diferencia preço vazio de bonificação", () => {
    expect(parseOptionalPrice("")).toBeNull();
    expect(parseOptionalPrice("0")).toBe(0);
  });

  it("gera data local para o input", () => {
    expect(localDateInputValue(new Date(2026, 8, 22, 10, 30))).toBe("2026-09-22");
  });

  it("mantém a data escolhida ao gerar o instante efetivo", () => {
    const iso = buildEffectiveAt("2026-09-20", new Date(2026, 8, 22, 15, 45));
    const result = new Date(iso);
    expect(result.getFullYear()).toBe(2026);
    expect(result.getMonth()).toBe(8);
    expect(result.getDate()).toBe(20);
  });
});

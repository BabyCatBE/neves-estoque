import { describe, expect, it } from "vitest";
import {
  buildEffectiveAt,
  getFutureOperationalDateError,
  localDateInputValue,
  parseOptionalPrice,
  parsePositiveDecimal,
  resolveEntryLineTotal,
  resolveEntryUnitPrice,
  formatCalculatedUnitPrice,
  formatEntryPriceInput
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


  it("calcula preço unitário a partir do total sem cortar a precisão armazenada", () => {
    const unit = resolveEntryUnitPrice("3", "", "10,00", "total");
    expect(unit).toBeCloseTo(10/3, 12);
    expect(formatCalculatedUnitPrice(unit!)).toBe("3,33");
    expect(resolveEntryLineTotal("3", "", "10,00", "total")).toBe(10);
    expect(formatEntryPriceInput(8.5)).toBe("8,50");
  });

  it("preserva preço unitário digitado e total derivado", () => {
    expect(resolveEntryUnitPrice("12", "8,50", "", "unit")).toBe(8.5);
    expect(resolveEntryLineTotal("12", "8,50", "", "unit")).toBe(102);
  });

  it("distingue vazio, bonificação e valores inválidos ao informar preço total", () => {
    expect(resolveEntryUnitPrice("4", "", "", "total")).toBeNull();
    expect(resolveEntryUnitPrice("4", "", "0", "total")).toBe(0);
    expect(resolveEntryLineTotal("4", "", "0", "total")).toBe(0);
    expect(() => resolveEntryUnitPrice("0", "", "10", "total")).toThrow();
    expect(() => resolveEntryUnitPrice("4", "", "-2", "total")).toThrow("Preço total inválido.");
  });

  it("gera data local para o input", () => {
    expect(localDateInputValue(new Date(2026, 8, 22, 10, 30))).toBe("2026-09-22");
  });

  it("mantém a data escolhida ao gerar o instante efetivo", () => {
    const reference = new Date(2026, 8, 22, 15, 45);
    const iso = buildEffectiveAt("2026-09-20", reference, reference);
    const result = new Date(iso);
    expect(result.getFullYear()).toBe(2026);
    expect(result.getMonth()).toBe(8);
    expect(result.getDate()).toBe(20);
  });

  it("identifica data futura antes do salvamento", () => {
    const today = new Date(2026, 8, 22, 15, 45);
    expect(getFutureOperationalDateError("2026-09-23", today)).toBe(
      "A data não pode ser futura."
    );
    expect(getFutureOperationalDateError("2026-09-22", today)).toBeNull();
  });

  it("bloqueia data posterior a hoje", () => {
    const today = new Date(2026, 8, 22, 15, 45);
    expect(() => buildEffectiveAt("2026-09-23", today, today)).toThrow(
      "A data não pode ser futura."
    );
  });
});

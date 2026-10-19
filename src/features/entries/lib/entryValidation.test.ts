import { describe, expect, it } from "vitest";
import {
  buildEffectiveAt,
  entryUnitPriceFromFields,
  entryLineTotalFromFields,
  formatEntryUnitPriceDisplay,
  getFutureOperationalDateError,
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


  it("calcula preço unitário a partir do total da nota sem truncar o valor enviado", () => {
    const unit = entryUnitPriceFromFields("3", "", "100,00", "total");
    expect(unit).not.toBeNull();
    expect(unit! * 3).toBeCloseTo(100, 10);
    expect(formatEntryUnitPriceDisplay(unit!)).toBe("33,33");
    expect(entryLineTotalFromFields("3", "", "100,00", "total")).toBe(100);
  });

  it("recalcula quando a quantidade muda e mantém unidades decimais", () => {
    expect(entryUnitPriceFromFields("2,5", "", "15", "total")).toBe(6);
    expect(entryUnitPriceFromFields("3", "", "15", "total")).toBe(5);
  });

  it("preserva o preço unitário antigo, branco e bonificação", () => {
    expect(entryUnitPriceFromFields("3", "4,50", "", "unit")).toBe(4.5);
    expect(entryUnitPriceFromFields("3", "", "", "total")).toBeNull();
    expect(entryUnitPriceFromFields("3", "", "0", "total")).toBe(0);
    expect(entryUnitPriceFromFields("3", "", "0", "unit")).toBeNull();
  });

  it("não divide por zero nem aceita total inválido", () => {
    expect(() => entryUnitPriceFromFields("0", "", "100", "total")).toThrow();
    expect(() => entryUnitPriceFromFields("2", "", "-4", "total")).toThrow("Preço total inválido.");
    expect(() => entryUnitPriceFromFields("2", "", "abc", "total")).toThrow("Preço total inválido.");
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

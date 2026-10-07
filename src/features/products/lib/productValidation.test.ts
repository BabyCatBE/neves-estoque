import { describe, expect, it } from "vitest";
import {
  canEditInitialPrice,
  formatDecimalInput,
  parseOptionalNonNegativeDecimal,
  productNameSchema
} from "./productValidation";

describe("productNameSchema", () => {
  it("normaliza espaços no nome", () => {
    expect(productNameSchema.parse("  Farinha   de Trigo 1kg ")).toBe("Farinha de Trigo 1kg");
  });

  it("rejeita nome vazio", () => {
    expect(productNameSchema.safeParse("   ").success).toBe(false);
  });
});

describe("parseOptionalNonNegativeDecimal", () => {
  it("aceita vírgula decimal", () => {
    expect(parseOptionalNonNegativeDecimal("2,5", "Estoque inicial")).toBe(2.5);
  });

  it("mantém zero como valor informado", () => {
    expect(parseOptionalNonNegativeDecimal("0", "Estoque inicial")).toBe(0);
  });

  it("retorna null quando estiver em branco", () => {
    expect(parseOptionalNonNegativeDecimal(" ", "Preço inicial")).toBeNull();
  });

  it("rejeita valor negativo", () => {
    expect(() => parseOptionalNonNegativeDecimal("-1", "Preço inicial")).toThrow(
      "Preço inicial inválido."
    );
  });
});

describe("canEditInitialPrice", () => {
  it("permite editar sem Entradas ou com preço em branco/bonificação", () => {
    expect(canEditInitialPrice([])).toBe(true);
    expect(canEditInitialPrice([{ unitPrice: null }, { unitPrice: 0 }])).toBe(true);
  });

  it("bloqueia quando existe Entrada com preço real", () => {
    expect(canEditInitialPrice([{ unitPrice: 0 }, { unitPrice: 9.9 }])).toBe(false);
  });
});

describe("formatDecimalInput", () => {
  it("preserva vazio, zero e usa vírgula decimal", () => {
    expect(formatDecimalInput(null)).toBe("");
    expect(formatDecimalInput(0)).toBe("0");
    expect(formatDecimalInput(24.9)).toBe("24,9");
  });
});

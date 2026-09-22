import { describe, expect, it } from "vitest";
import {
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

import { describe, expect, it } from "vitest";
import {
  calculateUnitConversionFactor,
  convertPriceForUnit,
  convertQuantityForUnit,
  parsePositiveConversionQuantity
} from "./productUnitConversion";

describe("productUnitConversion", () => {
  it("aceita decimal com vírgula", () => {
    expect(parsePositiveConversionQuantity("12,5", "Quantidade")).toBe(12.5);
  });

  it("rejeita zero", () => {
    expect(() => parsePositiveConversionQuantity("0", "Quantidade")).toThrow(
      "Quantidade deve ser maior que zero."
    );
  });

  it("calcula equivalência 12 UN = 1 CX", () => {
    expect(calculateUnitConversionFactor(12, 1)).toBeCloseTo(1 / 12);
  });

  it("multiplica quantidades pela razão nova/antiga", () => {
    const factor = calculateUnitConversionFactor(1, 100);
    expect(convertQuantityForUnit(2.5, factor)).toBe(250);
  });

  it("divide o preço unitário pelo mesmo fator", () => {
    const factor = calculateUnitConversionFactor(12, 1);
    expect(convertPriceForUnit(10, factor)).toBeCloseTo(120);
  });
});

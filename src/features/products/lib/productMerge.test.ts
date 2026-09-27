import { describe, expect, it } from "vitest";
import type { ProductDetails } from "../api/products";
import { calculateMergeUnitFactors, determineMergePair } from "./productMerge";

function product(id: string, createdAt: string, unit = "UN"): ProductDetails {
  return {
    id,
    name: id,
    categoryId: "category",
    unit,
    sortOrder: 1,
    createdAt,
    initialStockQuantity: null,
    initialStockAt: null,
    initialPrice: null,
    initialPriceAt: null,
    currentQuantity: null,
    currentPrice: null,
    currentValue: null,
    stockRequiresConference: false,
    priceHistory: [],
    usageInsights: {} as ProductDetails["usageInsights"]
  };
}

describe("productMerge", () => {
  it("mantém o produto mais antigo como sobrevivente", () => {
    const older = product("a", "2026-09-01T10:00:00Z");
    const newer = product("b", "2026-09-02T10:00:00Z");
    expect(determineMergePair(newer, older).survivor.id).toBe("a");
  });

  it("desempata created_at pelo id", () => {
    const a = product("a", "2026-09-01T10:00:00Z");
    const b = product("b", "2026-09-01T10:00:00Z");
    expect(determineMergePair(b, a).survivor.id).toBe("a");
  });

  it("mantém fatores 1 quando as unidades já são iguais", () => {
    expect(calculateMergeUnitFactors("UN", "UN", "UN", null, null)).toEqual({
      survivorFactor: 1,
      absorbedFactor: 1
    });
  });

  it("converte o absorvido para a unidade do sobrevivente", () => {
    expect(calculateMergeUnitFactors("CX", "UN", "CX", 1, 12)).toEqual({
      survivorFactor: 1,
      absorbedFactor: 1 / 12
    });
  });

  it("converte o sobrevivente quando a unidade final é a do absorvido", () => {
    expect(calculateMergeUnitFactors("CX", "UN", "UN", 1, 12)).toEqual({
      survivorFactor: 12,
      absorbedFactor: 1
    });
  });
});

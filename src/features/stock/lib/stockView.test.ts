import { describe, expect, it } from "vitest";
import type { CurrentStockItem, StockCategory } from "../api/stock";
import {
  buildAlphabeticalStockView,
  buildCategoryStockView,
  calculateStockValueSummary
} from "./stockView";

const categories: StockCategory[] = [
  { id: "cat-b", name: "Boleria", sortOrder: 2 },
  { id: "cat-a", name: "Panificação", sortOrder: 1 }
];

const items: CurrentStockItem[] = [
  {
    productId: "p2",
    productName: "Farinha",
    categoryId: "cat-a",
    unit: "KG",
    currentQuantity: 3,
    currentPrice: 5,
    currentValue: 15,
    sortOrder: 2
  },
  {
    productId: "p1",
    productName: "Açúcar",
    categoryId: "cat-a",
    unit: "KG",
    currentQuantity: 1,
    currentPrice: 4,
    currentValue: 4,
    sortOrder: 1
  },
  {
    productId: "p3",
    productName: "Fermento",
    categoryId: null,
    unit: "PCT",
    currentQuantity: 2,
    currentPrice: null,
    currentValue: null,
    sortOrder: null
  }
];

describe("stockView", () => {
  it("respeita ordem de categoria e ordem manual dos produtos", () => {
    const result = buildCategoryStockView(categories, items);

    expect(result.groups.map((group) => group.name)).toEqual(["Panificação"]);
    expect(result.groups[0]?.items.map((item) => item.productName)).toEqual([
      "Açúcar",
      "Farinha"
    ]);
  });

  it("separa produto sem categoria como cadastro pendente", () => {
    const result = buildCategoryStockView(categories, items);
    expect(result.pending.map((item) => item.productName)).toEqual(["Fermento"]);
  });

  it("busca por trecho ignorando acentos", () => {
    const result = buildCategoryStockView(categories, items, "acu");
    expect(result.groups[0]?.items.map((item) => item.productName)).toEqual([
      "Açúcar"
    ]);
    expect(result.pending).toEqual([]);
  });

  it("ordena a visualização alfabética por nome", () => {
    expect(
      buildAlphabeticalStockView(items).map((item) => item.productName)
    ).toEqual(["Açúcar", "Farinha", "Fermento"]);
  });

  it("soma valores conhecidos e sinaliza estoque positivo sem preço", () => {
    expect(calculateStockValueSummary(items)).toEqual({
      totalKnown: 19,
      hasMissingPrice: true
    });
  });
});

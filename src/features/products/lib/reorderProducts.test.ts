import { describe, expect, it } from "vitest";
import type { ProductListItem } from "../api/products";
import {
  buildProductOrderDraft,
  changedProductOrders,
  moveProductInCategory
} from "./reorderProducts";

function product(id: string, categoryId: string, sortOrder: number): ProductListItem {
  return {
    id,
    name: id,
    categoryId,
    unit: "UN",
    sortOrder,
    currentQuantity: null,
    currentPrice: null
  };
}

describe("product reorder helpers", () => {
  it("agrupa e ordena produtos por categoria", () => {
    const draft = buildProductOrderDraft([
      product("b", "cat-1", 2),
      product("a", "cat-1", 1),
      product("c", "cat-2", 1)
    ]);

    expect(draft["cat-1"]?.map((item) => item.id)).toEqual(["a", "b"]);
    expect(draft["cat-2"]?.map((item) => item.id)).toEqual(["c"]);
  });

  it("move produto apenas dentro da categoria", () => {
    const draft = buildProductOrderDraft([
      product("a", "cat-1", 1),
      product("b", "cat-1", 2)
    ]);

    expect(
      moveProductInCategory(draft, "cat-1", "a", "b")["cat-1"]?.map((item) => item.id)
    ).toEqual(["b", "a"]);
  });

  it("retorna apenas categorias cuja ordem mudou", () => {
    const original = buildProductOrderDraft([
      product("a", "cat-1", 1),
      product("b", "cat-1", 2),
      product("c", "cat-2", 1)
    ]);
    const draft = moveProductInCategory(original, "cat-1", "a", "b");

    expect(changedProductOrders(original, draft)).toEqual([
      { categoryId: "cat-1", productIds: ["b", "a"] }
    ]);
  });
});

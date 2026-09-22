import { describe, expect, it } from "vitest";
import { moveItemById } from "./reorderCategories";

describe("moveItemById", () => {
  const items = [
    { id: "a", name: "A" },
    { id: "b", name: "B" },
    { id: "c", name: "C" }
  ];

  it("move um item para a posição de outro item", () => {
    expect(moveItemById(items, "a", "c").map((item) => item.id)).toEqual(["b", "c", "a"]);
  });

  it("mantém a lista quando os ids são iguais", () => {
    expect(moveItemById(items, "b", "b")).toBe(items);
  });

  it("mantém a lista quando um id não existe", () => {
    expect(moveItemById(items, "x", "b")).toBe(items);
  });
});

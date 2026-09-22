import type { ProductListItem } from "../api/products";

export type ProductOrderDraft = Record<string, ProductListItem[]>;

export function buildProductOrderDraft(products: ProductListItem[]): ProductOrderDraft {
  const draft: ProductOrderDraft = {};

  for (const product of products) {
    if (!product.categoryId) continue;
    (draft[product.categoryId] ??= []).push(product);
  }

  for (const categoryId of Object.keys(draft)) {
    const items = draft[categoryId];
    if (!items) continue;

    items.sort(
      (a, b) =>
        (a.sortOrder ?? Number.MAX_SAFE_INTEGER) -
          (b.sortOrder ?? Number.MAX_SAFE_INTEGER) ||
        a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" })
    );
  }

  return draft;
}

export function moveProductInCategory(
  draft: ProductOrderDraft,
  categoryId: string,
  productId: string,
  targetId: string
): ProductOrderDraft {
  const items = draft[categoryId];
  if (!items) return draft;

  const fromIndex = items.findIndex((item) => item.id === productId);
  const toIndex = items.findIndex((item) => item.id === targetId);

  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return draft;

  const nextItems = [...items];
  const moved = nextItems.splice(fromIndex, 1)[0];
  if (!moved) return draft;
  nextItems.splice(toIndex, 0, moved);

  return { ...draft, [categoryId]: nextItems };
}

export function changedProductOrders(
  original: ProductOrderDraft,
  draft: ProductOrderDraft
) {
  return Object.entries(draft)
    .filter(([categoryId, items]) => {
      const originalIds = (original[categoryId] ?? []).map((item) => item.id);
      const nextIds = items.map((item) => item.id);
      return originalIds.join("|") !== nextIds.join("|");
    })
    .map(([categoryId, items]) => ({
      categoryId,
      productIds: items.map((item) => item.id)
    }));
}

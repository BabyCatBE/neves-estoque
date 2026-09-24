import type {
  CurrentStockItem,
  StockCategory
} from "../api/stock";

export type StockCategoryGroup = {
  id: string;
  name: string;
  items: CurrentStockItem[];
};

export type StockCategoryView = {
  groups: StockCategoryGroup[];
  pending: CurrentStockItem[];
};

export function normalizeStockSearch(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase("pt-BR")
    .trim();
}

export function buildCategoryStockView(
  categories: StockCategory[],
  items: CurrentStockItem[],
  search = ""
): StockCategoryView {
  const term = normalizeStockSearch(search);
  const visibleItems = items.filter(
    (item) =>
      !term || normalizeStockSearch(item.productName).includes(term)
  );

  const byCategory = new Map<string, CurrentStockItem[]>();
  const pending: CurrentStockItem[] = [];

  for (const item of visibleItems) {
    if (!item.categoryId) {
      pending.push(item);
      continue;
    }

    const current = byCategory.get(item.categoryId) ?? [];
    current.push(item);
    byCategory.set(item.categoryId, current);
  }

  const sortItems = (values: CurrentStockItem[]) =>
    [...values].sort((a, b) => {
      const orderA = a.sortOrder ?? Number.MAX_SAFE_INTEGER;
      const orderB = b.sortOrder ?? Number.MAX_SAFE_INTEGER;
      return (
        orderA - orderB ||
        a.productName.localeCompare(b.productName, "pt-BR", {
          sensitivity: "base"
        })
      );
    });

  const groups = [...categories]
    .sort((a, b) => {
      const orderA = a.sortOrder ?? Number.MAX_SAFE_INTEGER;
      const orderB = b.sortOrder ?? Number.MAX_SAFE_INTEGER;
      return (
        orderA - orderB ||
        a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" })
      );
    })
    .map((category) => ({
      id: category.id,
      name: category.name,
      items: sortItems(byCategory.get(category.id) ?? [])
    }))
    .filter((group) => group.items.length > 0);

  return {
    groups,
    pending: sortItems(pending)
  };
}

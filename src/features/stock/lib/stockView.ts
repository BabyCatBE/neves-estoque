import { normalizeSearchText } from "../../../shared/lib/searchText";
import type {
  CurrentStockItem,
  StockCategory,
  StockSupplier
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

export type StockSupplierGroup = {
  id: string;
  name: string;
  items: CurrentStockItem[];
};

export type StockSupplierView = {
  groups: StockSupplierGroup[];
  withoutSupplier: CurrentStockItem[];
  unavailableSupplier: CurrentStockItem[];
};

export type StockValueSummary = {
  totalKnown: number;
  hasMissingPrice: boolean;
};

export function normalizeStockSearch(value: string) {
  return normalizeSearchText(value);
}

function matchesSearch(item: CurrentStockItem, search: string) {
  const term = normalizeStockSearch(search);
  return !term || normalizeStockSearch(item.productName).includes(term);
}

function sortByName(items: CurrentStockItem[]) {
  return [...items].sort((a, b) =>
    a.productName.localeCompare(b.productName, "pt-BR", {
      sensitivity: "base"
    })
  );
}

export function buildAlphabeticalStockView(
  items: CurrentStockItem[],
  search = ""
) {
  return sortByName(items.filter((item) => matchesSearch(item, search)));
}

export function buildCategoryStockView(
  categories: StockCategory[],
  items: CurrentStockItem[],
  search = ""
): StockCategoryView {
  const visibleItems = items.filter((item) => matchesSearch(item, search));
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

export function buildSupplierStockView(
  suppliers: StockSupplier[],
  items: CurrentStockItem[],
  search = ""
): StockSupplierView {
  const visibleItems = items.filter((item) => matchesSearch(item, search));
  const supplierById = new Map(suppliers.map((supplier) => [supplier.id, supplier]));
  const bySupplier = new Map<string, CurrentStockItem[]>();
  const withoutSupplier: CurrentStockItem[] = [];
  const unavailableSupplier: CurrentStockItem[] = [];

  for (const item of visibleItems) {
    if (!item.currentSupplierId) {
      withoutSupplier.push(item);
      continue;
    }

    if (!supplierById.has(item.currentSupplierId)) {
      unavailableSupplier.push(item);
      continue;
    }

    const current = bySupplier.get(item.currentSupplierId) ?? [];
    current.push(item);
    bySupplier.set(item.currentSupplierId, current);
  }

  const groups = [...suppliers]
    .sort((a, b) =>
      a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" })
    )
    .map((supplier) => ({
      id: supplier.id,
      name: supplier.name,
      items: sortByName(bySupplier.get(supplier.id) ?? [])
    }))
    .filter((group) => group.items.length > 0);

  return {
    groups,
    withoutSupplier: sortByName(withoutSupplier),
    unavailableSupplier: sortByName(unavailableSupplier)
  };
}

export function calculateStockValueSummary(
  items: CurrentStockItem[]
): StockValueSummary {
  return items.reduce<StockValueSummary>(
    (summary, item) => {
      if (item.currentValue !== null) {
        summary.totalKnown += Number(item.currentValue);
      }

      if (
        item.currentQuantity !== null &&
        Number(item.currentQuantity) > 0 &&
        item.currentPrice === null
      ) {
        summary.hasMissingPrice = true;
      }

      return summary;
    },
    { totalKnown: 0, hasMissingPrice: false }
  );
}

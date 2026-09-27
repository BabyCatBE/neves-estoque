import { supabase } from "../../../shared/lib/supabase";
import {
  OFFLINE_CACHE_KEYS,
  readThroughOfflineCache
} from "../../../shared/offline/offlineCache";

export type StockCategory = {
  id: string;
  name: string;
  sortOrder: number | null;
};

export type StockSupplier = {
  id: string;
  name: string;
};

export type CurrentStockItem = {
  productId: string;
  productName: string;
  categoryId: string | null;
  currentSupplierId: string | null;
  unit: string;
  currentQuantity: number | null;
  currentPrice: number | null;
  currentValue: number | null;
  sortOrder: number | null;
  stockRequiresConference: boolean;
};

export type CurrentStockData = {
  categories: StockCategory[];
  suppliers: StockSupplier[];
  items: CurrentStockItem[];
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listCurrentStock(): Promise<CurrentStockData> {
  return readThroughOfflineCache(
    OFFLINE_CACHE_KEYS.stockCurrent,
    listCurrentStockFromServer
  );
}

async function listCurrentStockFromServer(): Promise<CurrentStockData> {
  const client = requireClient();

  const [categoriesResult, suppliersResult, stockResult] = await Promise.all([
    client
      .from("categories")
      .select("id,name,sort_order")
      .is("deleted_at", null)
      .order("sort_order", { ascending: true, nullsFirst: false })
      .order("name", { ascending: true }),
    client
      .from("suppliers")
      .select("id,name")
      .order("name", { ascending: true }),
    client
      .from("stock_current")
      .select(
        "product_id,product_name,category_id,unit,current_quantity,current_supplier_id,current_price,current_value,sort_order,stock_requires_conference"
      )
  ]);

  if (categoriesResult.error) throw categoriesResult.error;
  if (suppliersResult.error) throw suppliersResult.error;
  if (stockResult.error) throw stockResult.error;

  return {
    categories: (categoriesResult.data ?? []).map((category) => ({
      id: category.id,
      name: category.name,
      sortOrder: category.sort_order
    })),
    suppliers: (suppliersResult.data ?? []).map((supplier) => ({
      id: supplier.id,
      name: supplier.name
    })),
    items: (stockResult.data ?? [])
      .filter(
        (row): row is typeof row & {
          product_id: string;
          product_name: string;
          unit: string;
        } => Boolean(row.product_id && row.product_name && row.unit)
      )
      .map((row) => ({
        productId: row.product_id,
        productName: row.product_name,
        categoryId: row.category_id,
        currentSupplierId: row.current_supplier_id,
        unit: row.unit,
        currentQuantity: row.current_quantity,
        currentPrice: row.current_price,
        currentValue: row.current_value,
        sortOrder: row.sort_order,
        stockRequiresConference: row.stock_requires_conference ?? false
      }))
  };
}

import { supabase } from "../../../shared/lib/supabase";

export type StockCategory = {
  id: string;
  name: string;
  sortOrder: number | null;
};

export type CurrentStockItem = {
  productId: string;
  productName: string;
  categoryId: string | null;
  unit: string;
  currentQuantity: number | null;
  currentPrice: number | null;
  currentValue: number | null;
  sortOrder: number | null;
};

export type CurrentStockData = {
  categories: StockCategory[];
  items: CurrentStockItem[];
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listCurrentStock(): Promise<CurrentStockData> {
  const client = requireClient();

  const [categoriesResult, stockResult] = await Promise.all([
    client
      .from("categories")
      .select("id,name,sort_order")
      .is("deleted_at", null)
      .order("sort_order", { ascending: true, nullsFirst: false })
      .order("name", { ascending: true }),
    client
      .from("stock_current")
      .select(
        "product_id,product_name,category_id,unit,current_quantity,current_price,current_value,sort_order"
      )
  ]);

  if (categoriesResult.error) throw categoriesResult.error;
  if (stockResult.error) throw stockResult.error;

  return {
    categories: (categoriesResult.data ?? []).map((category) => ({
      id: category.id,
      name: category.name,
      sortOrder: category.sort_order
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
        unit: row.unit,
        currentQuantity: row.current_quantity,
        currentPrice: row.current_price,
        currentValue: row.current_value,
        sortOrder: row.sort_order
      }))
  };
}

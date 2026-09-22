import { supabase } from "../../../shared/lib/supabase";
import type { Tables } from "../../../shared/types/database.types";
import type { ProductUnit } from "../lib/productValidation";

export type ProductCategoryOption = Pick<Tables<"categories">, "id" | "name" | "sort_order">;

export type ProductListItem = {
  id: string;
  name: string;
  categoryId: string | null;
  unit: string;
  sortOrder: number | null;
  currentQuantity: number | null;
  currentPrice: number | null;
};

export type CreateProductInput = {
  name: string;
  categoryId: string;
  unit: ProductUnit;
  initialStockQuantity: number | null;
  initialPrice: number | null;
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listProductCategories(): Promise<ProductCategoryOption[]> {
  const client = requireClient();
  const { data, error } = await client
    .from("categories")
    .select("id,name,sort_order")
    .is("deleted_at", null)
    .order("sort_order", { ascending: true, nullsFirst: false })
    .order("name", { ascending: true });

  if (error) throw error;
  return data ?? [];
}

export async function listActiveProducts(): Promise<ProductListItem[]> {
  const client = requireClient();

  const [productsResult, stockResult] = await Promise.all([
    client
      .from("products")
      .select("id,name,category_id,unit,sort_order")
      .is("deleted_at", null),
    client
      .from("stock_current")
      .select("product_id,current_quantity,current_price")
  ]);

  if (productsResult.error) throw productsResult.error;
  if (stockResult.error) throw stockResult.error;

  const stockByProduct = new Map(
    (stockResult.data ?? []).map((row) => [row.product_id, row] as const)
  );

  return (productsResult.data ?? []).map((product) => {
    const stock = stockByProduct.get(product.id);

    return {
      id: product.id,
      name: product.name,
      categoryId: product.category_id,
      unit: product.unit,
      sortOrder: product.sort_order,
      currentQuantity: stock?.current_quantity ?? null,
      currentPrice: stock?.current_price ?? null
    };
  });
}

export async function createProduct(input: CreateProductInput) {
  const client = requireClient();
  const { data, error } = await client.rpc("create_product", {
    p_name: input.name,
    p_category_id: input.categoryId,
    p_unit: input.unit,
    ...(input.initialStockQuantity === null
      ? {}
      : { p_initial_stock_quantity: input.initialStockQuantity }),
    ...(input.initialPrice === null ? {} : { p_initial_price: input.initialPrice })
  });

  if (error) throw error;
  return data;
}

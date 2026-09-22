import { supabase } from "../../../shared/lib/supabase";
import type { Tables } from "../../../shared/types/database.types";

type CategoryRow = Pick<Tables<"categories">, "id" | "name" | "sort_order">;

export type CategoryListItem = CategoryRow & {
  productCount: number;
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listActiveCategories(): Promise<CategoryListItem[]> {
  const client = requireClient();

  const [categoriesResult, productsResult] = await Promise.all([
    client
      .from("categories")
      .select("id,name,sort_order")
      .is("deleted_at", null)
      .order("sort_order", { ascending: true, nullsFirst: false })
      .order("name", { ascending: true }),
    client.from("products").select("category_id").is("deleted_at", null)
  ]);

  if (categoriesResult.error) throw categoriesResult.error;
  if (productsResult.error) throw productsResult.error;

  const counts = new Map<string, number>();
  for (const product of productsResult.data ?? []) {
    if (!product.category_id) continue;
    counts.set(product.category_id, (counts.get(product.category_id) ?? 0) + 1);
  }

  return (categoriesResult.data ?? []).map((category) => ({
    ...category,
    productCount: counts.get(category.id) ?? 0
  }));
}

export async function createCategory(name: string, sortOrder: number) {
  const client = requireClient();
  const { error } = await client.from("categories").insert({
    name,
    sort_order: sortOrder
  });

  if (error) throw error;
}

export async function renameCategory(id: string, name: string) {
  const client = requireClient();
  const { error } = await client.from("categories").update({ name }).eq("id", id);

  if (error) throw error;
}

export async function softDeleteCategory(id: string) {
  const client = requireClient();
  const { error } = await client
    .from("categories")
    .update({ deleted_at: new Date().toISOString() })
    .eq("id", id);

  if (error) throw error;
}

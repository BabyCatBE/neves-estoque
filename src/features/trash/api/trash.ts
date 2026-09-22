import { supabase } from "../../../shared/lib/supabase";
import { restoreProduct } from "../../products/api/products";
import { restoreCategory } from "../../categories/api/categories";

export type TrashItemType = "product" | "category";

export type TrashItem = {
  id: string;
  type: TrashItemType;
  name: string;
  detail: string | null;
  deletedAt: string;
  restoreUntil: string;
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listRestorableTrashItems(): Promise<TrashItem[]> {
  const client = requireClient();
  const now = new Date().toISOString();

  const [productsResult, categoriesResult] = await Promise.all([
    client
      .from("products")
      .select("id,name,unit,deleted_at,restore_until")
      .not("deleted_at", "is", null)
      .gt("restore_until", now),
    client
      .from("categories")
      .select("id,name,deleted_at,restore_until")
      .not("deleted_at", "is", null)
      .gt("restore_until", now)
  ]);

  if (productsResult.error) throw productsResult.error;
  if (categoriesResult.error) throw categoriesResult.error;

  const products: TrashItem[] = (productsResult.data ?? [])
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => ({
      id: item.id,
      type: "product",
      name: item.name,
      detail: item.unit,
      deletedAt: item.deleted_at as string,
      restoreUntil: item.restore_until as string
    }));

  const categories: TrashItem[] = (categoriesResult.data ?? [])
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => ({
      id: item.id,
      type: "category",
      name: item.name,
      detail: null,
      deletedAt: item.deleted_at as string,
      restoreUntil: item.restore_until as string
    }));

  return [...products, ...categories].sort(
    (a, b) => new Date(b.deletedAt).getTime() - new Date(a.deletedAt).getTime()
  );
}

export async function restoreTrashItem(item: Pick<TrashItem, "id" | "type">) {
  if (item.type === "product") {
    await restoreProduct(item.id);
    return;
  }

  await restoreCategory(item.id);
}

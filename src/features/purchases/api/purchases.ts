import { supabase } from "../../../shared/lib/supabase";

export type SupplierPurchaseProduct = {
  productId: string;
  productName: string;
  unit: string;
  currentQuantity: number | null;
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listSupplierPurchaseProducts(
  supplierId: string
): Promise<SupplierPurchaseProduct[]> {
  const client = requireClient();

  const entriesResult = await client
    .from("entries")
    .select("id")
    .eq("supplier_id", supplierId)
    .is("deleted_at", null)
    .lte("effective_at", new Date().toISOString());

  if (entriesResult.error) throw entriesResult.error;
  if (!entriesResult.data?.length) return [];

  const entryIds = entriesResult.data.map((entry) => entry.id);
  const itemsResult = await client
    .from("entry_items")
    .select("product_id")
    .in("entry_id", entryIds);

  if (itemsResult.error) throw itemsResult.error;

  const productIds = [...new Set((itemsResult.data ?? []).map((item) => item.product_id))];
  if (!productIds.length) return [];

  const [productsResult, stockResult] = await Promise.all([
    client
      .from("products")
      .select("id,name,unit")
      .in("id", productIds)
      .is("deleted_at", null),
    client
      .from("stock_current")
      .select("product_id,current_quantity")
      .in("product_id", productIds)
  ]);

  if (productsResult.error) throw productsResult.error;
  if (stockResult.error) throw stockResult.error;

  const stockByProduct = new Map(
    (stockResult.data ?? []).map((row) => [row.product_id, row.current_quantity] as const)
  );

  return (productsResult.data ?? [])
    .map((product) => ({
      productId: product.id,
      productName: product.name,
      unit: product.unit,
      currentQuantity: stockByProduct.get(product.id) ?? null
    }))
    .sort((a, b) =>
      a.productName.localeCompare(b.productName, "pt-BR", { sensitivity: "base" })
    );
}

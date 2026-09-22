import { supabase } from "../../../shared/lib/supabase";
import { restoreEntry } from "../../entries/api/entries";
import { restoreProduct } from "../../products/api/products";
import { restoreCategory } from "../../categories/api/categories";
import { restoreSupplier } from "../../suppliers/api/suppliers";

export type TrashItemType = "product" | "category" | "supplier" | "entry";

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

  const [productsResult, categoriesResult, suppliersResult, entriesResult] = await Promise.all([
    client.from("products").select("id,name,unit,deleted_at,restore_until").not("deleted_at", "is", null).gt("restore_until", now),
    client.from("categories").select("id,name,deleted_at,restore_until").not("deleted_at", "is", null).gt("restore_until", now),
    client.from("suppliers").select("id,name,company,phone,deleted_at,restore_until").not("deleted_at", "is", null).gt("restore_until", now),
    client.from("entries").select("id,supplier_id,effective_at,deleted_at,restore_until").not("deleted_at", "is", null).gt("restore_until", now)
  ]);

  if (productsResult.error) throw productsResult.error;
  if (categoriesResult.error) throw categoriesResult.error;
  if (suppliersResult.error) throw suppliersResult.error;
  if (entriesResult.error) throw entriesResult.error;

  const entrySupplierIds = [...new Set((entriesResult.data ?? []).map((entry) => entry.supplier_id))];
  const entrySuppliersResult = entrySupplierIds.length
    ? await client.from("suppliers").select("id,name").in("id", entrySupplierIds)
    : { data: [], error: null };

  if (entrySuppliersResult.error) throw entrySuppliersResult.error;

  const entrySupplierById = new Map(
    (entrySuppliersResult.data ?? []).map((supplier) => [supplier.id, supplier.name] as const)
  );

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

  const suppliers: TrashItem[] = (suppliersResult.data ?? [])
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => ({
      id: item.id,
      type: "supplier",
      name: item.name,
      detail: item.company ?? item.phone ?? "Cadastro pendente",
      deletedAt: item.deleted_at as string,
      restoreUntil: item.restore_until as string
    }));

  const entries: TrashItem[] = (entriesResult.data ?? [])
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => ({
      id: item.id,
      type: "entry",
      name: entrySupplierById.get(item.supplier_id) ?? "Fornecedor não disponível",
      detail: `Entrada de ${formatEntryDate(item.effective_at)}`,
      deletedAt: item.deleted_at as string,
      restoreUntil: item.restore_until as string
    }));

  return [...products, ...categories, ...suppliers, ...entries].sort(
    (a, b) => new Date(b.deletedAt).getTime() - new Date(a.deletedAt).getTime()
  );
}

export async function restoreTrashItem(
  item: Pick<TrashItem, "id" | "type">,
  deviceId: string | null
) {
  if (item.type === "product") return restoreProduct(item.id);
  if (item.type === "supplier") return restoreSupplier(item.id);
  if (item.type === "entry") {
    if (!deviceId) throw new Error("Dispositivo não autorizado.");
    return restoreEntry({ entryId: item.id, deviceId });
  }
  return restoreCategory(item.id);
}

function formatEntryDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(value));
}

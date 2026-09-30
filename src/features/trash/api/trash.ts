import {
  fetchAllByIdKeyset,
  fetchByIdChunks
} from "../../../shared/lib/keysetPagination";
import { supabase } from "../../../shared/lib/supabase";
import { restoreEntry } from "../../entries/api/entries";
import { restoreProduct } from "../../products/api/products";
import { restoreCategory } from "../../categories/api/categories";
import { restoreSupplier } from "../../suppliers/api/suppliers";
import { restoreConference } from "../../conferences/api/conferences";

export type TrashItemType = "product" | "category" | "supplier" | "entry" | "conference";

export type TrashItem = {
  id: string;
  type: TrashItemType;
  name: string;
  detail: string | null;
  deletedAt: string;
  restoreUntil: string;
};

export type EmptyTrashResult = {
  products: number;
  categories: number;
  suppliers: number;
  entries: number;
  conferences: number;
  total: number;
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listRestorableTrashItems(): Promise<TrashItem[]> {
  const client = requireClient();
  const now = new Date().toISOString();

  const [productRows, categoryRows, supplierRows, entryRows, conferenceRows] =
    await Promise.all([
      fetchAllByIdKeyset(({ afterId, limit }) => {
        let query = client
          .from("products")
          .select("id,name,unit,deleted_at,restore_until")
          .not("deleted_at", "is", null)
          .gt("restore_until", now)
          .is("permanently_deleted_at", null);
        if (afterId !== null) query = query.gt("id", afterId);
        return query.order("id", { ascending: true }).limit(limit);
      }),
      fetchAllByIdKeyset(({ afterId, limit }) => {
        let query = client
          .from("categories")
          .select("id,name,deleted_at,restore_until")
          .not("deleted_at", "is", null)
          .gt("restore_until", now)
          .is("permanently_deleted_at", null);
        if (afterId !== null) query = query.gt("id", afterId);
        return query.order("id", { ascending: true }).limit(limit);
      }),
      fetchAllByIdKeyset(({ afterId, limit }) => {
        let query = client
          .from("suppliers")
          .select("id,name,company,phone,deleted_at,restore_until")
          .not("deleted_at", "is", null)
          .gt("restore_until", now)
          .is("permanently_deleted_at", null);
        if (afterId !== null) query = query.gt("id", afterId);
        return query.order("id", { ascending: true }).limit(limit);
      }),
      fetchAllByIdKeyset(({ afterId, limit }) => {
        let query = client
          .from("entries")
          .select("id,supplier_id,effective_at,deleted_at,restore_until")
          .not("deleted_at", "is", null)
          .gt("restore_until", now)
          .is("permanently_deleted_at", null);
        if (afterId !== null) query = query.gt("id", afterId);
        return query.order("id", { ascending: true }).limit(limit);
      }),
      fetchAllByIdKeyset(({ afterId, limit }) => {
        let query = client
          .from("conferences")
          .select(
            "id,scope_type,category_id,scope_product_id,effective_at,physical_responsible,deleted_at,restore_until"
          )
          .not("deleted_at", "is", null)
          .gt("restore_until", now);
        if (afterId !== null) query = query.gt("id", afterId);
        return query.order("id", { ascending: true }).limit(limit);
      })
    ]);

  const entrySupplierIds = [...new Set(entryRows.map((entry) => entry.supplier_id))];
  const conferenceCategoryIds = [
    ...new Set(
      conferenceRows
        .map((conference) => conference.category_id)
        .filter((id): id is string => Boolean(id))
    )
  ];
  const conferenceProductIds = [
    ...new Set(
      conferenceRows
        .map((conference) => conference.scope_product_id)
        .filter((id): id is string => Boolean(id))
    )
  ];

  const [entrySuppliers, conferenceCategories, conferenceProducts] =
    await Promise.all([
      fetchByIdChunks(entrySupplierIds, (ids) =>
        client.from("suppliers").select("id,name").in("id", ids)
      ),
      fetchByIdChunks(conferenceCategoryIds, (ids) =>
        client.from("categories").select("id,name").in("id", ids)
      ),
      fetchByIdChunks(conferenceProductIds, (ids) =>
        client.from("products").select("id,name").in("id", ids)
      )
    ]);

  const entrySupplierById = new Map(
    entrySuppliers.map((supplier) => [supplier.id, supplier.name] as const)
  );
  const conferenceCategoryById = new Map(
    conferenceCategories.map((category) => [category.id, category.name] as const)
  );
  const conferenceProductById = new Map(
    conferenceProducts.map((product) => [product.id, product.name] as const)
  );

  const products: TrashItem[] = productRows
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => ({
      id: item.id,
      type: "product",
      name: item.name,
      detail: item.unit,
      deletedAt: item.deleted_at as string,
      restoreUntil: item.restore_until as string
    }));

  const categories: TrashItem[] = categoryRows
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => ({
      id: item.id,
      type: "category",
      name: item.name,
      detail: null,
      deletedAt: item.deleted_at as string,
      restoreUntil: item.restore_until as string
    }));

  const suppliers: TrashItem[] = supplierRows
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => ({
      id: item.id,
      type: "supplier",
      name: item.name,
      detail: item.company ?? item.phone ?? "Cadastro pendente",
      deletedAt: item.deleted_at as string,
      restoreUntil: item.restore_until as string
    }));

  const entries: TrashItem[] = entryRows
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => ({
      id: item.id,
      type: "entry",
      name: entrySupplierById.get(item.supplier_id) ?? "Fornecedor não disponível",
      detail: `Entrada de ${formatEntryDate(item.effective_at)}`,
      deletedAt: item.deleted_at as string,
      restoreUntil: item.restore_until as string
    }));

  const conferences: TrashItem[] = conferenceRows
    .filter((item) => item.deleted_at && item.restore_until)
    .map((item) => {
      const scopeName =
        item.scope_type === "category" && item.category_id
          ? conferenceCategoryById.get(item.category_id)
          : item.scope_type === "product" && item.scope_product_id
            ? conferenceProductById.get(item.scope_product_id)
            : null;

      return {
        id: item.id,
        type: "conference" as const,
        name: scopeName ?? "Conferência",
        detail: `Conferência de ${formatEntryDate(item.effective_at)} · Responsável: ${item.physical_responsible}`,
        deletedAt: item.deleted_at as string,
        restoreUntil: item.restore_until as string
      };
    });

  return [...products, ...categories, ...suppliers, ...entries, ...conferences].sort(
    (a, b) => new Date(b.deletedAt).getTime() - new Date(a.deletedAt).getTime()
  );
}

export async function permanentlyDeleteTrashItem(
  item: Pick<TrashItem, "id" | "type">,
  deviceId: string | null
) {
  if (!deviceId) throw new Error("Dispositivo não autorizado.");

  const client = requireClient();
  const { error } = await client.rpc("permanently_delete_trash_item", {
    p_item_type: item.type,
    p_item_id: item.id,
    p_device_id: deviceId
  });

  if (error) throw error;
}

export async function emptyTrash(deviceId: string | null): Promise<EmptyTrashResult> {
  if (!deviceId) throw new Error("Dispositivo não autorizado.");

  const client = requireClient();
  const { data, error } = await client.rpc("empty_trash", {
    p_device_id: deviceId
  });

  if (error) throw error;

  const result = data as Record<string, unknown> | null;
  return {
    products: Number(result?.products ?? 0),
    categories: Number(result?.categories ?? 0),
    suppliers: Number(result?.suppliers ?? 0),
    entries: Number(result?.entries ?? 0),
    conferences: Number(result?.conferences ?? 0),
    total: Number(result?.total ?? 0)
  };
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
  if (item.type === "conference") {
    if (!deviceId) throw new Error("Dispositivo não autorizado.");
    return restoreConference({ conferenceId: item.id, deviceId });
  }
  return restoreCategory(item.id);
}

function formatEntryDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(value));
}

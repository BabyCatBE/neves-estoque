import {
  fetchAllByIdKeyset,
  fetchAllByIdKeysetInChunks,
  fetchByIdChunks
} from "../../../shared/lib/keysetPagination";
import { supabase } from "../../../shared/lib/supabase";
import { localDateKey, localDayRange } from "../../conferences/lib/conferenceValidation";

export type EntryDraftSupplier = {
  name: string;
};

export type EntryDraftProduct = {
  clientId: string;
  name: string;
  unit: string;
  categoryId: string | null;
};

type EntryItemValues = {
  quantity: number;
  unitPrice: number | null;
};

export type EntryCreateItem = EntryItemValues &
  (
    | { productId: string; newProduct?: never }
    | { productId?: never; newProduct: EntryDraftProduct }
  );

export type EntryUpdateItem = EntryItemValues & {
  productId: string;
};

export type CreateEntryInput = {
  supplierId: string | null;
  newSupplier: EntryDraftSupplier | null;
  effectiveAt: string;
  deviceId: string;
  idempotencyKey: string;
  observation: string | null;
  items: EntryCreateItem[];
};

export type UpdateEntryInput = {
  entryId: string;
  supplierId: string;
  effectiveAt: string;
  deviceId: string;
  observation: string | null;
  items: EntryUpdateItem[];
};

export type EntryHistoryItem = {
  id: string;
  effectiveAt: string;
  supplierName: string;
  observation: string | null;
  totalKnown: number;
  hasMissingPrice: boolean;
  productNames: string[];
};

export type EntryDetailItem = {
  id: string;
  productId: string;
  productName: string;
  unit: string;
  quantity: number;
  unitPrice: number | null;
  position: number;
};

export type EntryConferenceConflictItem = {
  productId: string;
  productName: string;
  entryQuantity: number;
  conferenceQuantity: number;
};

export type EntryConferenceConflict = {
  conferenceId: string;
  effectiveAt: string;
  physicalResponsible: string;
  registeredByLabel: string;
  deviceLabel: string;
  items: EntryConferenceConflictItem[];
};

export type EntryDetails = {
  id: string;
  effectiveAt: string;
  supplierId: string;
  supplierName: string;
  supplierCompany: string | null;
  observation: string | null;
  totalKnown: number;
  hasMissingPrice: boolean;
  items: EntryDetailItem[];
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function createEntry(input: CreateEntryInput) {
  const client = requireClient();
  const commonArgs = {
    p_effective_at: input.effectiveAt,
    p_device_id: input.deviceId,
    p_idempotency_key: input.idempotencyKey,
    p_items: input.items.map((item) => ({
      ...(item.newProduct
        ? {
            new_product: {
              client_id: item.newProduct.clientId,
              name: item.newProduct.name,
              unit: item.newProduct.unit,
              ...(item.newProduct.categoryId === null
                ? {}
                : { category_id: item.newProduct.categoryId })
            }
          }
        : { product_id: item.productId }),
      quantity: item.quantity,
      ...(item.unitPrice === null ? {} : { unit_price: item.unitPrice })
    })),
    ...(input.observation === null ? {} : { p_observation: input.observation })
  };

  const result = input.newSupplier
    ? await client.rpc("create_entry_with_draft_supplier", {
        ...commonArgs,
        p_supplier_name: input.newSupplier.name
      })
    : input.supplierId
      ? await client.rpc("create_entry", {
          ...commonArgs,
          p_supplier_id: input.supplierId
        })
      : { data: null, error: new Error("Selecione um fornecedor.") };

  if (result.error) throw result.error;
  return result.data;
}



export async function findEntryConferenceConflicts(
  input: CreateEntryInput
): Promise<EntryConferenceConflict[]> {
  const client = requireClient();
  const entryQuantityByProduct = new Map(
    input.items.flatMap((item) =>
      item.productId ? [[item.productId, item.quantity] as const] : []
    )
  );
  const productIds = [...entryQuantityByProduct.keys()];
  if (!productIds.length) return [];

  const dateKey = localDateKey(input.effectiveAt);
  const { start, end } = localDayRange(dateKey);

  // Primeiro limita os cabeçalhos ao dia da Entrada; depois busca somente os
  // itens dessas Conferências, em blocos e com paginação por id. Assim um
  // histórico grande de conference_items não pode esconder um conflito do dia.
  const conferences = await fetchAllByIdKeyset(({ afterId, limit }) => {
    let query = client
      .from("conferences")
      .select("id,effective_at,created_at,physical_responsible,registered_by,device_id")
      .gte("effective_at", start)
      .lt("effective_at", end)
      .is("deleted_at", null);
    if (afterId !== null) query = query.gt("id", afterId);
    return query.order("id", { ascending: true }).limit(limit);
  });
  if (!conferences.length) return [];

  const conferenceIds = conferences.map((conference) => conference.id);
  const conflictItems = await fetchAllByIdKeysetInChunks(
    conferenceIds,
    (ids, { afterId, limit }) => {
      let query = client
        .from("conference_items")
        .select("id,conference_id,product_id,quantity")
        .in("conference_id", ids)
        .in("product_id", productIds);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }
  );
  if (!conflictItems.length) return [];

  const relevantConferenceIds = new Set(conflictItems.map((item) => item.conference_id));
  const relevantConferences = conferences
    .filter((conference) => relevantConferenceIds.has(conference.id))
    .sort(
      (left, right) =>
        Date.parse(left.effective_at) - Date.parse(right.effective_at) ||
        Date.parse(left.created_at) - Date.parse(right.created_at)
    );

  const userIds = [...new Set(relevantConferences.map((conference) => conference.registered_by))];
  const deviceIds = [...new Set(relevantConferences.map((conference) => conference.device_id))];

  const [products, users, devices] = await Promise.all([
    fetchByIdChunks(productIds, (ids) =>
      client.from("products").select("id,name").in("id", ids)
    ),
    fetchByIdChunks(userIds, (ids) =>
      client
        .from("app_users")
        .select("auth_user_id,display_name,username,email")
        .in("auth_user_id", ids)
    ),
    fetchByIdChunks(deviceIds, (ids) =>
      client.from("devices").select("id,friendly_name").in("id", ids)
    )
  ]);

  const productById = new Map(
    products.map((product) => [product.id, product.name] as const)
  );
  const userById = new Map(
    users.flatMap((user) =>
      user.auth_user_id
        ? [[
            user.auth_user_id,
            user.display_name ??
              (user.username ? `@${user.username}` : user.email ?? "Usuário autorizado")
          ] as const]
        : []
    )
  );
  const deviceById = new Map(
    devices.map((device) => [device.id, device.friendly_name] as const)
  );

  const itemsByConference = new Map<string, EntryConferenceConflictItem[]>();
  for (const item of conflictItems) {
    const entryQuantity = entryQuantityByProduct.get(item.product_id);
    if (entryQuantity === undefined) continue;
    const items = itemsByConference.get(item.conference_id) ?? [];
    items.push({
      productId: item.product_id,
      productName: productById.get(item.product_id) ?? "Produto",
      entryQuantity,
      conferenceQuantity: Number(item.quantity)
    });
    itemsByConference.set(item.conference_id, items);
  }

  return relevantConferences.map((conference) => ({
    conferenceId: conference.id,
    effectiveAt: conference.effective_at,
    physicalResponsible: conference.physical_responsible,
    registeredByLabel: userById.get(conference.registered_by) ?? "Usuário autorizado",
    deviceLabel: deviceById.get(conference.device_id) ?? "Dispositivo registrado",
    items: itemsByConference.get(conference.id) ?? []
  }));
}

export async function updateEntry(input: UpdateEntryInput) {
  const client = requireClient();
  const { error } = await client.rpc("update_entry", {
    p_entry_id: input.entryId,
    p_supplier_id: input.supplierId,
    p_effective_at: input.effectiveAt,
    p_device_id: input.deviceId,
    p_items: input.items.map((item) => ({
      product_id: item.productId,
      quantity: item.quantity,
      ...(item.unitPrice === null ? {} : { unit_price: item.unitPrice })
    })),
    ...(input.observation === null ? {} : { p_observation: input.observation })
  });

  if (error) throw error;
}

export async function listEntryHistory(): Promise<EntryHistoryItem[]> {
  const client = requireClient();

  const entries = await fetchAllByIdKeyset(({ afterId, limit }) => {
    let query = client
      .from("entries")
      .select("id,supplier_id,effective_at,created_at,observation")
      .is("deleted_at", null);
    if (afterId !== null) query = query.gt("id", afterId);
    return query.order("id", { ascending: true }).limit(limit);
  });
  if (!entries.length) return [];

  const entryIds = entries.map((entry) => entry.id);
  const supplierIds = [...new Set(entries.map((entry) => entry.supplier_id))];

  const [suppliers, items] = await Promise.all([
    fetchByIdChunks(supplierIds, (ids) =>
      client.from("suppliers").select("id,name").in("id", ids)
    ),
    fetchAllByIdKeysetInChunks(
      entryIds,
      (ids, { afterId, limit }) => {
        let query = client
          .from("entry_items")
          .select("id,entry_id,product_id,quantity,unit_price,position")
          .in("entry_id", ids);
        if (afterId !== null) query = query.gt("id", afterId);
        return query.order("id", { ascending: true }).limit(limit);
      }
    )
  ]);

  const productIds = [...new Set(items.map((item) => item.product_id))];
  const products = await fetchByIdChunks(productIds, (ids) =>
    client.from("products").select("id,name").in("id", ids)
  );

  const supplierById = new Map(suppliers.map((row) => [row.id, row.name] as const));
  const productById = new Map(products.map((row) => [row.id, row.name] as const));
  const itemsByEntry = new Map<string, typeof items>();
  for (const item of items) {
    const list = itemsByEntry.get(item.entry_id) ?? [];
    list.push(item);
    itemsByEntry.set(item.entry_id, list);
  }

  return [...entries]
    .sort(
      (left, right) =>
        Date.parse(right.effective_at) - Date.parse(left.effective_at) ||
        Date.parse(right.created_at) - Date.parse(left.created_at)
    )
    .map((entry) => {
      const entryItems = [...(itemsByEntry.get(entry.id) ?? [])].sort(
        (left, right) => left.position - right.position
      );
      return {
        id: entry.id,
        effectiveAt: entry.effective_at,
        supplierName: supplierById.get(entry.supplier_id) ?? "Fornecedor não disponível",
        observation: entry.observation,
        totalKnown: entryItems.reduce(
          (sum, item) =>
            sum +
            (item.unit_price === null
              ? 0
              : Number(item.quantity) * Number(item.unit_price)),
          0
        ),
        hasMissingPrice: entryItems.some((item) => item.unit_price === null),
        productNames: entryItems.map(
          (item) => productById.get(item.product_id) ?? "Produto não disponível"
        )
      };
    });
}

export async function getEntryDetails(entryId: string): Promise<EntryDetails> {
  const client = requireClient();
  const { data: entry, error: entryError } = await client
    .from("entries")
    .select("id,supplier_id,effective_at,observation")
    .eq("id", entryId)
    .is("deleted_at", null)
    .maybeSingle();

  if (entryError) throw entryError;
  if (!entry) throw new Error("Entrada não encontrada.");

  const [supplierResult, itemsResult] = await Promise.all([
    client.from("suppliers").select("id,name,company").eq("id", entry.supplier_id).maybeSingle(),
    client
      .from("entry_items")
      .select("id,entry_id,product_id,quantity,unit_price,position")
      .eq("entry_id", entry.id)
      .order("position", { ascending: true })
  ]);

  if (supplierResult.error) throw supplierResult.error;
  if (itemsResult.error) throw itemsResult.error;

  const productIds = [...new Set((itemsResult.data ?? []).map((item) => item.product_id))];
  const productsResult = productIds.length
    ? await client.from("products").select("id,name,unit").in("id", productIds)
    : { data: [], error: null };

  if (productsResult.error) throw productsResult.error;
  const productById = new Map((productsResult.data ?? []).map((row) => [row.id, row] as const));

  const items: EntryDetailItem[] = (itemsResult.data ?? []).map((item) => {
    const product = productById.get(item.product_id);
    return {
      id: item.id,
      productId: item.product_id,
      productName: product?.name ?? "Produto não disponível",
      unit: product?.unit ?? "—",
      quantity: Number(item.quantity),
      unitPrice: item.unit_price === null ? null : Number(item.unit_price),
      position: item.position
    };
  });

  return {
    id: entry.id,
    effectiveAt: entry.effective_at,
    supplierId: entry.supplier_id,
    supplierName: supplierResult.data?.name ?? "Fornecedor não disponível",
    supplierCompany: supplierResult.data?.company ?? null,
    observation: entry.observation,
    totalKnown: items.reduce(
      (sum, item) => sum + (item.unitPrice === null ? 0 : item.quantity * item.unitPrice),
      0
    ),
    hasMissingPrice: items.some((item) => item.unitPrice === null),
    items
  };
}


export async function softDeleteEntry(input: { entryId: string; deviceId: string }) {
  const client = requireClient();
  const { error } = await client.rpc("soft_delete_entry", {
    p_entry_id: input.entryId,
    p_device_id: input.deviceId
  });

  if (error) throw error;
}

export async function restoreEntry(input: { entryId: string; deviceId: string }) {
  const client = requireClient();
  const { error } = await client.rpc("restore_entry", {
    p_entry_id: input.entryId,
    p_device_id: input.deviceId
  });

  if (error) throw error;
}

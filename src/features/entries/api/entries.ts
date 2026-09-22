import { supabase } from "../../../shared/lib/supabase";

export type EntryCreateItem = {
  productId: string;
  quantity: number;
  unitPrice: number | null;
};

export type CreateEntryInput = {
  supplierId: string;
  effectiveAt: string;
  deviceId: string;
  idempotencyKey: string;
  observation: string | null;
  items: EntryCreateItem[];
};

export type UpdateEntryInput = Omit<CreateEntryInput, "idempotencyKey"> & {
  entryId: string;
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
  const { data, error } = await client.rpc("create_entry", {
    p_supplier_id: input.supplierId,
    p_effective_at: input.effectiveAt,
    p_device_id: input.deviceId,
    p_idempotency_key: input.idempotencyKey,
    p_items: input.items.map((item) => ({
      product_id: item.productId,
      quantity: item.quantity,
      ...(item.unitPrice === null ? {} : { unit_price: item.unitPrice })
    })),
    ...(input.observation === null ? {} : { p_observation: input.observation })
  });

  if (error) throw error;
  return data;
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
  const { data: entries, error: entriesError } = await client
    .from("entries")
    .select("id,supplier_id,effective_at,observation")
    .is("deleted_at", null)
    .order("effective_at", { ascending: false });

  if (entriesError) throw entriesError;
  if (!entries?.length) return [];

  const entryIds = entries.map((entry) => entry.id);
  const supplierIds = [...new Set(entries.map((entry) => entry.supplier_id))];

  const [suppliersResult, itemsResult] = await Promise.all([
    client.from("suppliers").select("id,name").in("id", supplierIds),
    client
      .from("entry_items")
      .select("id,entry_id,product_id,quantity,unit_price,position")
      .in("entry_id", entryIds)
      .order("position", { ascending: true })
  ]);

  if (suppliersResult.error) throw suppliersResult.error;
  if (itemsResult.error) throw itemsResult.error;

  const productIds = [...new Set((itemsResult.data ?? []).map((item) => item.product_id))];
  const productsResult = productIds.length
    ? await client.from("products").select("id,name").in("id", productIds)
    : { data: [], error: null };

  if (productsResult.error) throw productsResult.error;

  const supplierById = new Map((suppliersResult.data ?? []).map((row) => [row.id, row.name] as const));
  const productById = new Map((productsResult.data ?? []).map((row) => [row.id, row.name] as const));

  return entries.map((entry) => {
    const items = (itemsResult.data ?? []).filter((item) => item.entry_id === entry.id);
    return {
      id: entry.id,
      effectiveAt: entry.effective_at,
      supplierName: supplierById.get(entry.supplier_id) ?? "Fornecedor não disponível",
      observation: entry.observation,
      totalKnown: items.reduce(
        (sum, item) => sum + (item.unit_price === null ? 0 : Number(item.quantity) * Number(item.unit_price)),
        0
      ),
      hasMissingPrice: items.some((item) => item.unit_price === null),
      productNames: items.map((item) => productById.get(item.product_id) ?? "Produto não disponível")
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

import { supabase } from "../../../shared/lib/supabase";
import type { Tables } from "../../../shared/types/database.types";

export type SupplierRow = Tables<"suppliers">;

export type SupplierListItem = {
  id: string;
  name: string;
  company: string | null;
  phone: string | null;
  purchaseFrequencyDays: number | null;
  preferredOrderWeekday: number | null;
  averageDeliveryDays: number | null;
  safetyMarginDays: number | null;
  isPending: boolean;
};

export type SupplierDetails = SupplierListItem & {
  observation: string | null;
};

export type SupplierMutationInput = {
  name: string;
  company: string;
  phone: string;
  observation: string | null;
  purchaseFrequencyDays: number | null;
  preferredOrderWeekday: number | null;
  averageDeliveryDays: number | null;
  safetyMarginDays: number | null;
};

export type UpdateSupplierInput = SupplierMutationInput & { id: string };

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

function mapSupplier(row: SupplierRow): SupplierDetails {
  return {
    id: row.id,
    name: row.name,
    company: row.company,
    phone: row.phone,
    observation: row.observation,
    purchaseFrequencyDays: row.purchase_frequency_days,
    preferredOrderWeekday: row.preferred_order_weekday,
    averageDeliveryDays: row.average_delivery_days,
    safetyMarginDays: row.safety_margin_days,
    isPending: !row.company || !row.phone
  };
}

export async function listActiveSuppliers(): Promise<SupplierListItem[]> {
  const client = requireClient();
  const { data, error } = await client
    .from("suppliers")
    .select("*")
    .is("deleted_at", null)
    .order("name", { ascending: true });

  if (error) throw error;
  return (data ?? []).map(mapSupplier);
}

export async function getSupplierDetails(supplierId: string): Promise<SupplierDetails> {
  const client = requireClient();
  const { data, error } = await client
    .from("suppliers")
    .select("*")
    .eq("id", supplierId)
    .is("deleted_at", null)
    .maybeSingle();

  if (error) throw error;
  if (!data) throw new Error("Fornecedor não encontrado.");
  return mapSupplier(data);
}

export async function createSupplier(input: SupplierMutationInput) {
  const client = requireClient();
  const { data, error } = await client.rpc("create_supplier", {
    p_name: input.name,
    p_company: input.company,
    p_phone: input.phone,
    ...(input.observation === null ? {} : { p_observation: input.observation }),
    ...(input.purchaseFrequencyDays === null ? {} : { p_purchase_frequency_days: input.purchaseFrequencyDays }),
    ...(input.preferredOrderWeekday === null ? {} : { p_preferred_order_weekday: input.preferredOrderWeekday }),
    ...(input.averageDeliveryDays === null ? {} : { p_average_delivery_days: input.averageDeliveryDays }),
    ...(input.safetyMarginDays === null ? {} : { p_safety_margin_days: input.safetyMarginDays })
  });
  if (error) throw error;
  return data;
}

export async function updateSupplier(input: UpdateSupplierInput) {
  const client = requireClient();
  const { error } = await client.rpc("update_supplier", {
    p_supplier_id: input.id,
    p_name: input.name,
    p_company: input.company,
    p_phone: input.phone,
    ...(input.observation === null ? {} : { p_observation: input.observation }),
    ...(input.purchaseFrequencyDays === null ? {} : { p_purchase_frequency_days: input.purchaseFrequencyDays }),
    ...(input.preferredOrderWeekday === null ? {} : { p_preferred_order_weekday: input.preferredOrderWeekday }),
    ...(input.averageDeliveryDays === null ? {} : { p_average_delivery_days: input.averageDeliveryDays }),
    ...(input.safetyMarginDays === null ? {} : { p_safety_margin_days: input.safetyMarginDays })
  });
  if (error) throw error;
}

export async function softDeleteSupplier(supplierId: string) {
  const client = requireClient();
  const { error } = await client.rpc("soft_delete_supplier", { p_supplier_id: supplierId });
  if (error) throw error;
}

export async function restoreSupplier(supplierId: string) {
  const client = requireClient();
  const { error } = await client.rpc("restore_supplier", { p_supplier_id: supplierId });
  if (error) throw error;
}

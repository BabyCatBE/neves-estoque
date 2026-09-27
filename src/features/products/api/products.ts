import { supabase } from "../../../shared/lib/supabase";
import type { Tables } from "../../../shared/types/database.types";
import type { ProductUnit } from "../lib/productValidation";
import {
  calculateProductUsageInsights,
  type ProductUsageInsights
} from "../lib/productUsageInsights";

export type ProductCategoryOption = Pick<Tables<"categories">, "id" | "name" | "sort_order">;

export type ProductListItem = {
  id: string;
  name: string;
  categoryId: string | null;
  unit: string;
  sortOrder: number | null;
  createdAt: string;
  currentQuantity: number | null;
  currentPrice: number | null;
  stockRequiresConference: boolean;
};

export type CreateQuickEntryProductInput = {
  name: string;
  unit: ProductUnit;
  categoryId: string | null;
  entryIdempotencyKey: string;
};

export type CreateProductInput = {
  name: string;
  categoryId: string;
  unit: ProductUnit;
  initialStockQuantity: number | null;
  initialPrice: number | null;
};

export type ProductPriceHistoryItem = {
  id: string;
  entryId: string;
  effectiveAt: string;
  supplierName: string;
  quantity: number;
  unitPrice: number | null;
  position: number;
};

export type ProductDetails = {
  id: string;
  name: string;
  categoryId: string | null;
  unit: string;
  sortOrder: number | null;
  createdAt: string;
  initialStockQuantity: number | null;
  initialStockAt: string | null;
  initialPrice: number | null;
  initialPriceAt: string | null;
  currentQuantity: number | null;
  currentPrice: number | null;
  currentValue: number | null;
  stockRequiresConference: boolean;
  priceHistory: ProductPriceHistoryItem[];
  usageInsights: ProductUsageInsights;
};

export type UpdateProductDetailsInput = {
  id: string;
  name: string;
  categoryId: string;
};

export type ConvertProductUnitInput = {
  productId: string;
  newUnit: ProductUnit;
  oldQuantity: number;
  newQuantity: number;
  deviceId: string;
};

export type MergeInitialPriceSource = "survivor" | "absorbed" | "none";

export type MergeProductsInput = {
  productAId: string;
  productBId: string;
  finalName: string;
  finalCategoryId: string;
  finalUnit: ProductUnit;
  survivorEquivalentQuantity: number | null;
  absorbedEquivalentQuantity: number | null;
  initialPriceSource: MergeInitialPriceSource;
  deviceId: string;
};

export type MergeProductsResult = {
  survivorProductId: string;
  absorbedProductId: string;
  entryItemsCount: number;
  conferenceItemsCount: number;
  overlapConferenceCount: number;
};

export type ReorderProductCategory = {
  categoryId: string;
  productIds: string[];
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
      .select("id,name,category_id,unit,sort_order,created_at")
      .is("deleted_at", null),
    client
      .from("stock_current")
      .select("product_id,current_quantity,current_price,stock_requires_conference")
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
      createdAt: product.created_at,
      currentQuantity: stock?.current_quantity ?? null,
      currentPrice: stock?.current_price ?? null,
      stockRequiresConference: stock?.stock_requires_conference ?? false
    };
  });
}

export async function createQuickEntryProduct(input: CreateQuickEntryProductInput) {
  const client = requireClient();
  const { data, error } = await client.rpc("create_quick_entry_product", {
    p_name: input.name,
    p_unit: input.unit,
    p_entry_idempotency_key: input.entryIdempotencyKey,
    ...(input.categoryId ? { p_category_id: input.categoryId } : {})
  });

  if (error) throw error;
  return data;
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


export async function getProductDetails(productId: string): Promise<ProductDetails> {
  const client = requireClient();

  const [productResult, stockResult] = await Promise.all([
    client
      .from("products")
      .select(
        "id,name,category_id,unit,sort_order,created_at,initial_stock_quantity,initial_stock_at,initial_price,initial_price_at"
      )
      .eq("id", productId)
      .is("deleted_at", null)
      .maybeSingle(),
    client
      .from("stock_current")
      .select("current_quantity,current_price,current_value,stock_requires_conference")
      .eq("product_id", productId)
      .maybeSingle()
  ]);

  if (productResult.error) throw productResult.error;
  if (stockResult.error) throw stockResult.error;
  if (!productResult.data) throw new Error("Produto não encontrado.");

  const { data: priceItems, error: priceItemsError } = await client
    .from("entry_items")
    .select("id,entry_id,quantity,unit_price,position")
    .eq("product_id", productId);

  if (priceItemsError) throw priceItemsError;

  const entryIds = [...new Set((priceItems ?? []).map((item) => item.entry_id))];
  const entriesResult = entryIds.length
    ? await client
        .from("entries")
        .select("id,effective_at,created_at,supplier_id")
        .in("id", entryIds)
        .is("deleted_at", null)
        .order("effective_at", { ascending: false })
        .order("created_at", { ascending: false })
    : { data: [], error: null };

  if (entriesResult.error) throw entriesResult.error;

  const supplierIds = [...new Set((entriesResult.data ?? []).map((entry) => entry.supplier_id))];
  const suppliersResult = supplierIds.length
    ? await client.from("suppliers").select("id,name").in("id", supplierIds)
    : { data: [], error: null };

  if (suppliersResult.error) throw suppliersResult.error;

  const entryById = new Map(
    (entriesResult.data ?? []).map((entry, index) => [entry.id, { ...entry, order: index }] as const)
  );
  const supplierById = new Map(
    (suppliersResult.data ?? []).map((supplier) => [supplier.id, supplier.name] as const)
  );

  const { data: conferenceItems, error: conferenceItemsError } = await client
    .from("conference_items")
    .select("conference_id,quantity")
    .eq("product_id", productId);

  if (conferenceItemsError) throw conferenceItemsError;

  const conferenceIds = [
    ...new Set((conferenceItems ?? []).map((item) => item.conference_id))
  ];
  const conferencesResult = conferenceIds.length
    ? await client
        .from("conferences")
        .select("id,effective_at,created_at")
        .in("id", conferenceIds)
        .is("deleted_at", null)
        .order("effective_at", { ascending: true })
        .order("created_at", { ascending: true })
    : { data: [], error: null };

  if (conferencesResult.error) throw conferencesResult.error;

  const conferenceById = new Map(
    (conferencesResult.data ?? []).map((conference) => [conference.id, conference] as const)
  );

  const priceHistory: ProductPriceHistoryItem[] = (priceItems ?? [])
    .filter((item) => entryById.has(item.entry_id))
    .sort((a, b) => {
      const entryA = entryById.get(a.entry_id);
      const entryB = entryById.get(b.entry_id);
      const entryOrder =
        (entryA?.order ?? Number.MAX_SAFE_INTEGER) -
        (entryB?.order ?? Number.MAX_SAFE_INTEGER);
      return entryOrder || b.position - a.position;
    })
    .map((item) => {
      const entry = entryById.get(item.entry_id)!;
      return {
        id: item.id,
        entryId: item.entry_id,
        effectiveAt: entry.effective_at,
        supplierName: supplierById.get(entry.supplier_id) ?? "Fornecedor não disponível",
        quantity: Number(item.quantity),
        unitPrice: item.unit_price === null ? null : Number(item.unit_price),
        position: item.position
      };
    });

  const usageInsights = calculateProductUsageInsights({
    conferences: (conferenceItems ?? [])
      .filter((item) => conferenceById.has(item.conference_id))
      .map((item) => {
        const conference = conferenceById.get(item.conference_id)!;
        return {
          effectiveAt: conference.effective_at,
          createdAt: conference.created_at,
          quantity: Number(item.quantity)
        };
      }),
    entries: (priceItems ?? [])
      .filter((item) => entryById.has(item.entry_id))
      .map((item) => ({
        effectiveAt: entryById.get(item.entry_id)!.effective_at,
        quantity: Number(item.quantity)
      })),
    currentQuantity:
      stockResult.data?.current_quantity === null ||
      stockResult.data?.current_quantity === undefined
        ? null
        : Number(stockResult.data.current_quantity)
  });

  return {
    id: productResult.data.id,
    name: productResult.data.name,
    categoryId: productResult.data.category_id,
    unit: productResult.data.unit,
    sortOrder: productResult.data.sort_order,
    createdAt: productResult.data.created_at,
    initialStockQuantity: productResult.data.initial_stock_quantity,
    initialStockAt: productResult.data.initial_stock_at,
    initialPrice: productResult.data.initial_price,
    initialPriceAt: productResult.data.initial_price_at,
    currentQuantity: stockResult.data?.current_quantity ?? null,
    currentPrice: stockResult.data?.current_price ?? null,
    currentValue: stockResult.data?.current_value ?? null,
    stockRequiresConference: stockResult.data?.stock_requires_conference ?? false,
    priceHistory,
    usageInsights
  };
}

export async function updateProductDetails(input: UpdateProductDetailsInput) {
  const client = requireClient();
  const { error } = await client.rpc("update_product_details", {
    p_product_id: input.id,
    p_name: input.name,
    p_category_id: input.categoryId
  });

  if (error) throw error;
}

export async function convertProductUnit(input: ConvertProductUnitInput) {
  const client = requireClient();
  const { data, error } = await client.rpc("convert_product_unit", {
    p_product_id: input.productId,
    p_new_unit: input.newUnit,
    p_old_quantity: input.oldQuantity,
    p_new_quantity: input.newQuantity,
    p_device_id: input.deviceId
  });

  if (error) throw error;
  return data;
}

export async function mergeProducts(input: MergeProductsInput): Promise<MergeProductsResult> {
  const client = requireClient();
  const { data, error } = await client.rpc("merge_products", {
    p_product_a_id: input.productAId,
    p_product_b_id: input.productBId,
    p_final_name: input.finalName,
    p_final_category_id: input.finalCategoryId,
    p_final_unit: input.finalUnit,
    p_survivor_equivalent_quantity: input.survivorEquivalentQuantity,
    p_absorbed_equivalent_quantity: input.absorbedEquivalentQuantity,
    p_initial_price_source: input.initialPriceSource,
    p_device_id: input.deviceId
  });

  if (error) throw error;

  const result = data as Record<string, unknown> | null;
  const survivorProductId =
    typeof result?.survivor_product_id === "string" ? result.survivor_product_id : "";
  const absorbedProductId =
    typeof result?.absorbed_product_id === "string" ? result.absorbed_product_id : "";

  if (!survivorProductId || !absorbedProductId) {
    throw new Error("A mescla foi concluída, mas o resultado retornado é inválido.");
  }

  return {
    survivorProductId,
    absorbedProductId,
    entryItemsCount: Number(result?.entry_items_count ?? 0),
    conferenceItemsCount: Number(result?.conference_items_count ?? 0),
    overlapConferenceCount: Number(result?.overlap_conference_count ?? 0)
  };
}


export async function reorderProducts(orders: ReorderProductCategory[]) {
  const client = requireClient();
  const { error } = await client.rpc("reorder_products", {
    p_orders: orders.map((order) => ({
      category_id: order.categoryId,
      product_ids: order.productIds
    }))
  });

  if (error) throw error;
}


export async function softDeleteProduct(productId: string) {
  const client = requireClient();
  const { error } = await client.rpc("soft_delete_product", {
    p_product_id: productId
  });

  if (error) throw error;
}

export async function restoreProduct(productId: string) {
  const client = requireClient();
  const { error } = await client.rpc("restore_product", {
    p_product_id: productId
  });

  if (error) throw error;
}

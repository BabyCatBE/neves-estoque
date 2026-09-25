import { supabase } from "../../../shared/lib/supabase";
import {
  calculateProductUsageInsights,
  type ProductUsageInsights
} from "../../products/lib/productUsageInsights";
import {
  calculatePurchaseProjection,
  type PurchaseProjection
} from "../lib/purchaseProjection";

export type PurchaseIntelligenceProduct = {
  productId: string;
  productName: string;
  unit: string;
  categoryId: string | null;
  sortOrder: number | null;
  currentQuantity: number | null;
  currentSupplierId: string | null;
  historicalSupplierIds: string[];
  usageInsights: ProductUsageInsights;
  projection: PurchaseProjection;
};

export type SupplierPurchaseProduct = PurchaseIntelligenceProduct;

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listPurchaseIntelligenceProducts(): Promise<PurchaseIntelligenceProduct[]> {
  const client = requireClient();
  const now = new Date().toISOString();

  const [
    productsResult,
    stockResult,
    suppliersResult,
    conferencesResult,
    conferenceItemsResult,
    entriesResult,
    entryItemsResult
  ] = await Promise.all([
    client
      .from("products")
      .select("id,name,unit,category_id,sort_order")
      .is("deleted_at", null),
    client
      .from("stock_current")
      .select("product_id,current_quantity,current_supplier_id"),
    client
      .from("suppliers")
      .select(
        "id,deleted_at,purchase_frequency_days,preferred_order_weekday,average_delivery_days,safety_margin_days"
      ),
    client
      .from("conferences")
      .select("id,effective_at,created_at")
      .is("deleted_at", null)
      .lte("effective_at", now),
    client
      .from("conference_items")
      .select("conference_id,product_id,quantity"),
    client
      .from("entries")
      .select("id,supplier_id,effective_at")
      .is("deleted_at", null)
      .lte("effective_at", now),
    client
      .from("entry_items")
      .select("entry_id,product_id,quantity")
  ]);

  if (productsResult.error) throw productsResult.error;
  if (stockResult.error) throw stockResult.error;
  if (suppliersResult.error) throw suppliersResult.error;
  if (conferencesResult.error) throw conferencesResult.error;
  if (conferenceItemsResult.error) throw conferenceItemsResult.error;
  if (entriesResult.error) throw entriesResult.error;
  if (entryItemsResult.error) throw entryItemsResult.error;

  const stockByProduct = new Map(
    (stockResult.data ?? []).map((row) => [row.product_id, row] as const)
  );
  const supplierById = new Map(
    (suppliersResult.data ?? []).map((supplier) => [supplier.id, supplier] as const)
  );
  const conferenceById = new Map(
    (conferencesResult.data ?? []).map((conference) => [conference.id, conference] as const)
  );
  const entryById = new Map(
    (entriesResult.data ?? []).map((entry) => [entry.id, entry] as const)
  );

  const conferencesByProduct = new Map<
    string,
    Array<{ effectiveAt: string; createdAt: string; quantity: number }>
  >();
  for (const item of conferenceItemsResult.data ?? []) {
    const conference = conferenceById.get(item.conference_id);
    if (!conference) continue;
    const points = conferencesByProduct.get(item.product_id) ?? [];
    points.push({
      effectiveAt: conference.effective_at,
      createdAt: conference.created_at,
      quantity: Number(item.quantity)
    });
    conferencesByProduct.set(item.product_id, points);
  }

  const entriesByProduct = new Map<string, Array<{ effectiveAt: string; quantity: number }>>();
  const historicalSuppliersByProduct = new Map<string, Set<string>>();
  for (const item of entryItemsResult.data ?? []) {
    const entry = entryById.get(item.entry_id);
    if (!entry) continue;

    const points = entriesByProduct.get(item.product_id) ?? [];
    points.push({
      effectiveAt: entry.effective_at,
      quantity: Number(item.quantity)
    });
    entriesByProduct.set(item.product_id, points);

    const suppliers = historicalSuppliersByProduct.get(item.product_id) ?? new Set<string>();
    suppliers.add(entry.supplier_id);
    historicalSuppliersByProduct.set(item.product_id, suppliers);
  }

  const referenceDate = new Date();

  return (productsResult.data ?? []).map((product) => {
    const stock = stockByProduct.get(product.id);
    const currentQuantity =
      stock?.current_quantity === null || stock?.current_quantity === undefined
        ? null
        : Number(stock.current_quantity);
    const currentSupplierId = stock?.current_supplier_id ?? null;
    const supplier = currentSupplierId ? supplierById.get(currentSupplierId) : undefined;

    const usageInsights = calculateProductUsageInsights({
      conferences: conferencesByProduct.get(product.id) ?? [],
      entries: entriesByProduct.get(product.id) ?? [],
      currentQuantity,
      referenceAt: now
    });

    const projection = calculatePurchaseProjection({
      usageReady: usageInsights.status === "ready",
      dailyAverage: usageInsights.dailyAverage,
      currentQuantity,
      unit: product.unit,
      hasSupplier: currentSupplierId !== null,
      supplierActive: Boolean(supplier && supplier.deleted_at === null),
      purchaseFrequencyDays: supplier?.purchase_frequency_days ?? null,
      preferredOrderWeekday: supplier?.preferred_order_weekday ?? null,
      deliveryDays: supplier?.average_delivery_days ?? null,
      safetyMarginDays: supplier?.safety_margin_days ?? null,
      referenceDate
    });

    return {
      productId: product.id,
      productName: product.name,
      unit: product.unit,
      categoryId: product.category_id,
      sortOrder: product.sort_order,
      currentQuantity,
      currentSupplierId,
      historicalSupplierIds: [...(historicalSuppliersByProduct.get(product.id) ?? new Set<string>())],
      usageInsights,
      projection
    };
  });
}

export async function listSupplierPurchaseProducts(
  supplierId: string
): Promise<SupplierPurchaseProduct[]> {
  const products = await listPurchaseIntelligenceProducts();

  return products
    .filter((product) => product.historicalSupplierIds.includes(supplierId))
    .map((product) => {
      if (
        product.currentSupplierId === null ||
        product.currentSupplierId === supplierId
      ) {
        return product;
      }

      return {
        ...product,
        projection: {
          ...product.projection,
          status: "other_supplier" as const,
          suggestedQuantity: null,
          targetStock: null,
          cycleDays: null,
          daysUntilNextOrder: null
        }
      };
    });
}

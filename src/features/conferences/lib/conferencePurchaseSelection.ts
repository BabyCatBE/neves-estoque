import { offlineDb, type OfflineConferencePurchaseRecord } from "../../../shared/offline/offlineDb";

export type MarkedPurchaseProduct = OfflineConferencePurchaseRecord["products"][number];

export const CONFERENCE_PURCHASE_UPDATED_EVENT = "neves-conference-purchase-updated";

function requireOwner(ownerId: string) {
  if (!ownerId) throw new Error("Usuário indisponível para guardar os produtos marcados.");
}

function notifySelectionChanged() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(CONFERENCE_PURCHASE_UPDATED_EVENT));
}

export async function readMarkedProducts(ownerId: string): Promise<MarkedPurchaseProduct[]> {
  requireOwner(ownerId);
  const selection = await offlineDb.conferencePurchaseSelections.get(ownerId);
  return selection?.products ?? [];
}

export async function toggleMarkedProduct(ownerId: string, product: MarkedPurchaseProduct) {
  requireOwner(ownerId);
  const result = await offlineDb.transaction("rw", offlineDb.conferencePurchaseSelections, async () => {
    const existing = await offlineDb.conferencePurchaseSelections.get(ownerId);
    const products = existing?.products ?? [];
    const next = products.some((item) => item.productId === product.productId)
      ? products.filter((item) => item.productId !== product.productId)
      : [...products, product];
    await offlineDb.conferencePurchaseSelections.put({ ownerId, products: next, updatedAt: new Date().toISOString() });
    return next;
  });
  notifySelectionChanged();
  return result;
}

export async function clearMarkedProducts(ownerId: string) {
  requireOwner(ownerId);
  await offlineDb.conferencePurchaseSelections.put({
    ownerId,
    products: [],
    updatedAt: new Date().toISOString()
  });
  notifySelectionChanged();
}

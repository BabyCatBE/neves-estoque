import { offlineDb, type OfflineConferenceDraftRecord } from "../../../shared/offline/offlineDb";

export const CONFERENCE_DRAFTS_UPDATED_EVENT = "neves-conference-drafts-updated";
export type ConferenceDraft = OfflineConferenceDraftRecord;

export function conferenceDraftKey(ownerId: string, categoryId: string): string {
  if (!ownerId || !categoryId) throw new Error("Usuário ou Categoria indisponível.");
  return ownerId + "|" + categoryId;
}

/** Contagem parcial, inclusive vazios/zero, nunca é pendingOperation. */
export async function readConferenceDraft(ownerId: string, categoryId: string) {
  return offlineDb.conferenceDrafts.get(conferenceDraftKey(ownerId, categoryId));
}

export async function listConferenceDrafts(ownerId: string) {
  if (!ownerId) return [];
  return offlineDb.conferenceDrafts.where("ownerId").equals(ownerId).toArray();
}

export async function saveConferenceDraft(draft: Omit<ConferenceDraft, "id" | "updatedAt">) {
  await offlineDb.conferenceDrafts.put({
    ...draft,
    id: conferenceDraftKey(draft.ownerId, draft.categoryId),
    updatedAt: new Date().toISOString()
  });
  if (typeof window !== "undefined") window.dispatchEvent(new Event(CONFERENCE_DRAFTS_UPDATED_EVENT));
}

export async function discardConferenceDraft(ownerId: string, categoryId: string) {
  await offlineDb.conferenceDrafts.delete(conferenceDraftKey(ownerId, categoryId));
  if (typeof window !== "undefined") window.dispatchEvent(new Event(CONFERENCE_DRAFTS_UPDATED_EVENT));
}

export function conferenceDraftFilledCount(draft: ConferenceDraft, productIds: string[]): number {
  return productIds.filter((id) => (draft.quantities[id] ?? "").trim() !== "").length;
}

import { createBrowserUuid } from "../../../shared/lib/browserUuid";
import {
  offlineDb,
  type OfflinePendingOperationRecord
} from "../../../shared/offline/offlineDb";
import type { CreateEntryInput } from "../../entries/api/entries";

export const OFFLINE_PENDING_UPDATED_EVENT = "neves-offline-pending-updated";

export type OfflinePendingEntry = Omit<OfflinePendingOperationRecord, "kind" | "payload"> & {
  kind: "entry";
  payload: CreateEntryInput;
};

export type SavePendingEntryMetadata = {
  authUserId: string;
  appUserId: string | null;
  deviceId: string;
  actorLabel: string;
  supplierLabel: string;
};

function emitPendingUpdated() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(OFFLINE_PENDING_UPDATED_EVENT));
  }
}

export function buildPendingEntryRecord(
  input: CreateEntryInput,
  metadata: SavePendingEntryMetadata,
  localId = createBrowserUuid(),
  now = new Date().toISOString()
): OfflinePendingEntry {
  const supplier = metadata.supplierLabel.trim() || "Fornecedor não identificado";
  return {
    id: localId,
    kind: "entry",
    status: "pending_confirmation",
    createdAt: now,
    updatedAt: now,
    effectiveAt: input.effectiveAt,
    authUserId: metadata.authUserId,
    appUserId: metadata.appUserId,
    deviceId: metadata.deviceId,
    actorLabel: metadata.actorLabel,
    idempotencyKey: input.idempotencyKey,
    summaryTitle: supplier,
    summarySubtitle: input.observation,
    itemCount: input.items.length,
    payload: input
  };
}

export async function savePendingEntry(
  input: CreateEntryInput,
  metadata: SavePendingEntryMetadata
) {
  const record = buildPendingEntryRecord(input, metadata);
  await offlineDb.pendingOperations.put(record);
  emitPendingUpdated();
  return record.id;
}

export async function listOfflinePendingOperations(): Promise<OfflinePendingOperationRecord[]> {
  return offlineDb.pendingOperations.orderBy("createdAt").reverse().toArray();
}

export async function deleteOfflinePendingOperation(id: string) {
  await offlineDb.pendingOperations.delete(id);
  emitPendingUpdated();
}

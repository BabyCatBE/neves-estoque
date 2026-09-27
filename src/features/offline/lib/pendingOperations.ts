import { createBrowserUuid } from "../../../shared/lib/browserUuid";
import {
  offlineDb,
  type OfflinePendingOperationRecord
} from "../../../shared/offline/offlineDb";
import type { CreateEntryInput } from "../../entries/api/entries";
import type {
  CategoryConferenceWriteInput,
  ProductConferenceWriteInput
} from "../../conferences/api/conferences";

export const OFFLINE_PENDING_UPDATED_EVENT = "neves-offline-pending-updated";

export type OfflinePendingEntry = Omit<OfflinePendingOperationRecord, "kind" | "payload"> & {
  kind: "entry";
  payload: CreateEntryInput;
};

export type OfflinePendingConference = Omit<OfflinePendingOperationRecord, "kind" | "payload"> & {
  kind: "conference";
  payload: CategoryConferenceWriteInput;
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


export type SavePendingConferenceMetadata = {
  authUserId: string;
  appUserId: string | null;
  deviceId: string;
  actorLabel: string;
  categoryLabel: string;
};

export function buildPendingConferenceRecord(
  input: CategoryConferenceWriteInput,
  metadata: SavePendingConferenceMetadata,
  localId = createBrowserUuid(),
  now = new Date().toISOString()
): OfflinePendingConference {
  const category = metadata.categoryLabel.trim() || "Categoria não identificada";
  return {
    id: localId,
    kind: "conference",
    status: "pending_confirmation",
    createdAt: now,
    updatedAt: now,
    effectiveAt: input.effectiveAt,
    authUserId: metadata.authUserId,
    appUserId: metadata.appUserId,
    deviceId: metadata.deviceId,
    actorLabel: metadata.actorLabel,
    idempotencyKey: input.idempotencyKey,
    summaryTitle: category,
    summarySubtitle: `Responsável físico: ${input.physicalResponsible}`,
    itemCount: input.items.length,
    payload: input
  };
}

export async function savePendingConference(
  input: CategoryConferenceWriteInput,
  metadata: SavePendingConferenceMetadata
) {
  const record = buildPendingConferenceRecord(input, metadata);
  await offlineDb.pendingOperations.put(record);
  emitPendingUpdated();
  return record.id;
}


export type OfflinePendingProductConference = Omit<
  OfflinePendingOperationRecord,
  "kind" | "payload"
> & {
  kind: "conference";
  payload: ProductConferenceWriteInput;
};

export type SavePendingProductConferenceMetadata = {
  authUserId: string;
  appUserId: string | null;
  deviceId: string;
  actorLabel: string;
  productLabel: string;
};

export function buildPendingProductConferenceRecord(
  input: ProductConferenceWriteInput,
  metadata: SavePendingProductConferenceMetadata,
  localId = createBrowserUuid(),
  now = new Date().toISOString()
): OfflinePendingProductConference {
  const product = metadata.productLabel.trim() || "Produto não identificado";
  return {
    id: localId,
    kind: "conference",
    status: "pending_confirmation",
    createdAt: now,
    updatedAt: now,
    effectiveAt: input.effectiveAt,
    authUserId: metadata.authUserId,
    appUserId: metadata.appUserId,
    deviceId: metadata.deviceId,
    actorLabel: metadata.actorLabel,
    idempotencyKey: input.idempotencyKey,
    summaryTitle: product,
    summarySubtitle: `Conferência unitária · Responsável físico: ${input.physicalResponsible}`,
    itemCount: 1,
    payload: input
  };
}

export async function savePendingProductConference(
  input: ProductConferenceWriteInput,
  metadata: SavePendingProductConferenceMetadata
) {
  const record = buildPendingProductConferenceRecord(input, metadata);
  await offlineDb.pendingOperations.put(record);
  emitPendingUpdated();
  return record.id;
}

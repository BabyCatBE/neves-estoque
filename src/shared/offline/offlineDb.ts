import Dexie, { type Table } from "dexie";

export type OfflineSnapshotRecord = {
  key: string;
  data: unknown;
  updatedAt: string;
};

export type OfflineIllustrationRecord = {
  path: string;
  blob: Blob;
  updatedAt: string;
};

export type OfflinePendingOperationRecord = {
  id: string;
  kind: "entry" | "conference";
  status: "pending_confirmation";
  createdAt: string;
  updatedAt: string;
  effectiveAt: string;
  authUserId: string;
  appUserId: string | null;
  deviceId: string;
  actorLabel: string;
  idempotencyKey: string;
  summaryTitle: string;
  summarySubtitle: string | null;
  itemCount: number;
  payload: unknown;
};

/** Contagem incompleta: diferente de pendingOperations e nunca enviável. */
export type OfflineConferenceDraftRecord = {
  id: string;
  ownerId: string;
  categoryId: string;
  date: string;
  responsible: string;
  observation: string;
  quantities: Record<string, string>;
  updatedAt: string;
};

export type VerifiedOfflineAccessRecord = {
  authUserId: string;
  appUserId: string;
  roleName: string | null;
  deviceId: string;
  deviceKey: string;
  displayName: string | null;
  username: string | null;
  authMethod: string | null;
  verifiedAt: string;
};

class NevesOfflineDatabase extends Dexie {
  snapshots!: Table<OfflineSnapshotRecord, string>;
  illustrationBlobs!: Table<OfflineIllustrationRecord, string>;
  pendingOperations!: Table<OfflinePendingOperationRecord, string>;
  conferenceDrafts!: Table<OfflineConferenceDraftRecord, string>;
  verifiedAccess!: Table<VerifiedOfflineAccessRecord, string>;

  constructor() {
    super("neves-estoque-offline");

    this.version(1).stores({
      snapshots: "&key,updatedAt",
      verifiedAccess: "&authUserId,verifiedAt,deviceId"
    });

    this.version(2).stores({
      snapshots: "&key,updatedAt",
      illustrationBlobs: "&path,updatedAt",
      verifiedAccess: "&authUserId,verifiedAt,deviceId"
    });

    this.version(3).stores({
      snapshots: "&key,updatedAt",
      illustrationBlobs: "&path,updatedAt",
      pendingOperations: "&id,kind,status,createdAt,effectiveAt,authUserId",
      verifiedAccess: "&authUserId,verifiedAt,deviceId"
    });

    // Upgrade apenas da cópia local do navegador; preservar pendências e cache.
    this.version(4).stores({
      snapshots: "&key,updatedAt",
      illustrationBlobs: "&path,updatedAt",
      pendingOperations: "&id,kind,status,createdAt,effectiveAt,authUserId",
      conferenceDrafts: "&id,ownerId,categoryId,updatedAt",
      verifiedAccess: "&authUserId,verifiedAt,deviceId"
    });
  }
}

export const offlineDb = new NevesOfflineDatabase();

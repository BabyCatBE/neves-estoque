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
  }
}

export const offlineDb = new NevesOfflineDatabase();

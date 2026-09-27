import { offlineDb, type VerifiedOfflineAccessRecord } from "./offlineDb";

export const OFFLINE_CACHE_UPDATED_EVENT = "neves-offline-cache-updated";

export const OFFLINE_CACHE_KEYS = {
  categories: "categories.active",
  productCategories: "products.categories",
  products: "products.active",
  suppliers: "suppliers.active",
  stockCurrent: "stock.current"
} as const;

export class OfflineDataUnavailableError extends Error {
  constructor() {
    super("Os dados ainda não foram salvos neste aparelho para uso offline.");
    this.name = "OfflineDataUnavailableError";
  }
}

export function browserIsOnline() {
  if (typeof navigator === "undefined") return true;
  return navigator.onLine !== false;
}

export function isLikelyNetworkError(error: unknown) {
  if (!browserIsOnline()) return true;
  if (error instanceof TypeError) return true;

  const message =
    error instanceof Error
      ? error.message
      : typeof error === "object" && error !== null && "message" in error
        ? String((error as { message?: unknown }).message ?? "")
        : String(error ?? "");

  return /failed to fetch|networkerror|network error|load failed|fetch failed|offline|connection/i.test(
    message
  );
}

export async function readOfflineSnapshot<T>(key: string): Promise<T | null> {
  try {
    const record = await offlineDb.snapshots.get(key);
    return (record?.data as T | undefined) ?? null;
  } catch {
    return null;
  }
}

export async function writeOfflineSnapshot<T>(key: string, data: T) {
  const updatedAt = new Date().toISOString();

  try {
    await offlineDb.snapshots.put({ key, data, updatedAt });

    if (typeof window !== "undefined") {
      window.dispatchEvent(
        new CustomEvent(OFFLINE_CACHE_UPDATED_EVENT, { detail: { key, updatedAt } })
      );
    }

    return updatedAt;
  } catch {
    return null;
  }
}

export async function readThroughOfflineCache<T>(
  key: string,
  loadFromServer: () => Promise<T>
): Promise<T> {
  if (!browserIsOnline()) {
    const cached = await readOfflineSnapshot<T>(key);
    if (cached !== null) return cached;
    throw new OfflineDataUnavailableError();
  }

  try {
    const data = await loadFromServer();
    await writeOfflineSnapshot(key, data);
    return data;
  } catch (error) {
    if (!isLikelyNetworkError(error)) throw error;

    const cached = await readOfflineSnapshot<T>(key);
    if (cached !== null) return cached;
    throw error;
  }
}

export async function getLatestOfflineCacheUpdatedAt() {
  try {
    const latest = await offlineDb.snapshots.orderBy("updatedAt").last();
    return latest?.updatedAt ?? null;
  } catch {
    return null;
  }
}

export function canUseVerifiedOfflineAccess(
  record: VerifiedOfflineAccessRecord | null,
  authUserId: string,
  deviceKey: string
) {
  return Boolean(
    record &&
      record.authUserId === authUserId &&
      record.deviceKey === deviceKey &&
      record.deviceId
  );
}

export async function readVerifiedOfflineAccess(authUserId: string) {
  try {
    return (await offlineDb.verifiedAccess.get(authUserId)) ?? null;
  } catch {
    return null;
  }
}

export async function saveVerifiedOfflineAccess(
  record: Omit<VerifiedOfflineAccessRecord, "verifiedAt">
) {
  try {
    await offlineDb.verifiedAccess.put({
      ...record,
      verifiedAt: new Date().toISOString()
    });
  } catch {
    // O acesso online continua válido mesmo se o IndexedDB estiver indisponível.
  }
}


const illustrationObjectUrls = new Map<string, string>();

export async function ensureOfflineIllustration(path: string, remoteUrl: string) {
  try {
    const existing = await offlineDb.illustrationBlobs.get(path);
    if (existing) return;

    const response = await fetch(remoteUrl);
    if (!response.ok) return;

    const blob = await response.blob();
    await offlineDb.illustrationBlobs.put({
      path,
      blob,
      updatedAt: new Date().toISOString()
    });
  } catch {
    // Falha no cache visual não deve impedir o uso online da Categoria.
  }
}

export async function readOfflineIllustrationUrl(path: string) {
  try {
    const cachedUrl = illustrationObjectUrls.get(path);
    if (cachedUrl) return cachedUrl;

    const record = await offlineDb.illustrationBlobs.get(path);
    if (!record || typeof URL === "undefined" || typeof URL.createObjectURL !== "function") {
      return null;
    }

    const objectUrl = URL.createObjectURL(record.blob);
    illustrationObjectUrls.set(path, objectUrl);
    return objectUrl;
  } catch {
    return null;
  }
}

export async function removeOfflineIllustration(path: string) {
  try {
    await offlineDb.illustrationBlobs.delete(path);
  } catch {
    // A remoção no servidor continua sendo a autoridade.
  }

  const objectUrl = illustrationObjectUrls.get(path);
  if (objectUrl && typeof URL !== "undefined" && typeof URL.revokeObjectURL === "function") {
    URL.revokeObjectURL(objectUrl);
  }
  illustrationObjectUrls.delete(path);
}

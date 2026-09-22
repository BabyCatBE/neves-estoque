import { getRegisteredDeviceId } from "../../features/auth/lib/deviceIdentity";

type DeviceStorage = Pick<Storage, "getItem">;
type StorageProvider = () => DeviceStorage;

export function createDeviceAwareFetch(fetchImpl: typeof fetch, storageProvider: StorageProvider) {
  return async (input: RequestInfo | URL, init?: RequestInit) => {
    const requestHeaders =
      typeof Request !== "undefined" && input instanceof Request ? input.headers : undefined;
    const headers = new Headers(requestHeaders);

    if (init?.headers) {
      new Headers(init.headers).forEach((value, key) => headers.set(key, value));
    }

    try {
      const deviceId = getRegisteredDeviceId(storageProvider());
      if (deviceId) headers.set("x-device-id", deviceId);
    } catch {
      // Falha ao ler armazenamento local não deve impedir a requisição.
      // A auditoria simplesmente não receberá o device_id nesse request.
    }

    return fetchImpl(input, { ...init, headers });
  };
}

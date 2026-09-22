const DEVICE_KEY_STORAGE = "neves-estoque.device-key.v1";

type StorageLike = Pick<Storage, "getItem" | "setItem">;

export function isUuid(value: string | null): value is string {
  return Boolean(
    value &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
  );
}

export function getOrCreateDeviceKey(
  storage: StorageLike,
  createUuid: () => string = () => crypto.randomUUID()
) {
  const existing = storage.getItem(DEVICE_KEY_STORAGE);
  if (isUuid(existing)) return existing;

  const created = createUuid();
  if (!isUuid(created)) throw new Error("Não foi possível criar o identificador do dispositivo.");

  storage.setItem(DEVICE_KEY_STORAGE, created);
  return created;
}

export function inferFriendlyDeviceName(userAgent: string) {
  const device =
    /Android/i.test(userAgent)
      ? "Android"
      : /iPhone/i.test(userAgent)
        ? "iPhone"
        : /iPad/i.test(userAgent)
          ? "iPad"
          : /Windows/i.test(userAgent)
            ? "Windows"
            : /Macintosh|Mac OS X/i.test(userAgent)
              ? "Mac"
              : /Linux/i.test(userAgent)
                ? "Linux"
                : "Navegador";

  const browser =
    /Edg\//i.test(userAgent)
      ? "Edge"
      : /Chrome\//i.test(userAgent)
        ? "Chrome"
        : /Firefox\//i.test(userAgent)
          ? "Firefox"
          : /Safari\//i.test(userAgent)
            ? "Safari"
            : "Web";

  return `${device} · ${browser}`;
}

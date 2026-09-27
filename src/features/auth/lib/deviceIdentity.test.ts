import { describe, expect, it } from "vitest";
import {
  clearRegisteredDeviceId,
  createBrowserUuid,
  getOrCreateDeviceKey,
  getRegisteredDeviceId,
  inferFriendlyDeviceName,
  isUuid,
  setRegisteredDeviceId
} from "./deviceIdentity";

function memoryStorage() {
  const data = new Map<string, string>();
  return {
    getItem(key: string) {
      return data.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      data.set(key, value);
    },
    removeItem(key: string) {
      data.delete(key);
    }
  };
}

describe("deviceIdentity", () => {
  it("reutiliza um UUID válido já salvo", () => {
    const storage = memoryStorage();
    const existing = "550e8400-e29b-41d4-a716-446655440000";
    storage.setItem("neves-estoque.device-key.v1", existing);

    expect(getOrCreateDeviceKey(storage, () => "11111111-1111-4111-8111-111111111111")).toBe(existing);
  });

  it("substitui valor inválido por um novo UUID", () => {
    const storage = memoryStorage();
    storage.setItem("neves-estoque.device-key.v1", "inválido");
    const created = "11111111-1111-4111-8111-111111111111";

    expect(getOrCreateDeviceKey(storage, () => created)).toBe(created);
    expect(isUuid(storage.getItem("neves-estoque.device-key.v1"))).toBe(true);
  });

  it("usa randomUUID quando disponível", () => {
    const cryptoObject = {
      randomUUID: () => "33333333-3333-4333-8333-333333333333",
      getRandomValues: <T extends ArrayBufferView | null>(array: T) => array
    };

    expect(createBrowserUuid(cryptoObject)).toBe("33333333-3333-4333-8333-333333333333");
  });

  it("gera UUID v4 com getRandomValues quando randomUUID não existe", () => {
    const cryptoObject = {
      getRandomValues<T extends ArrayBufferView | null>(array: T) {
        const bytes = array as Uint8Array;
        bytes.fill(0xab);
        return array;
      }
    };

    const uuid = createBrowserUuid(cryptoObject);

    expect(uuid).toBe("abababab-abab-4bab-abab-abababababab");
    expect(isUuid(uuid)).toBe(true);
  });

  it("salva, recupera e remove o id registrado do dispositivo", () => {
    const storage = memoryStorage();
    const deviceId = "22222222-2222-4222-8222-222222222222";

    setRegisteredDeviceId(storage, deviceId);
    expect(getRegisteredDeviceId(storage)).toBe(deviceId);

    clearRegisteredDeviceId(storage);
    expect(getRegisteredDeviceId(storage)).toBeNull();
  });

  it("ignora um id registrado inválido", () => {
    const storage = memoryStorage();
    storage.setItem("neves-estoque.registered-device-id.v1", "inválido");

    expect(getRegisteredDeviceId(storage)).toBeNull();
  });

  it("gera um nome curto e legível para Android Chrome", () => {
    expect(
      inferFriendlyDeviceName(
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36"
      )
    ).toBe("Android · Chrome");
  });
});

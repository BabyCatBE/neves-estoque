import { describe, expect, it } from "vitest";
import { getOrCreateDeviceKey, inferFriendlyDeviceName, isUuid } from "./deviceIdentity";

function memoryStorage() {
  const data = new Map<string, string>();
  return {
    getItem(key: string) {
      return data.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      data.set(key, value);
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

  it("gera um nome curto e legível para Android Chrome", () => {
    expect(
      inferFriendlyDeviceName(
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36"
      )
    ).toBe("Android · Chrome");
  });
});

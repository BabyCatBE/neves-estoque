import { describe, expect, it, vi } from "vitest";
import { createDeviceAwareFetch } from "./deviceAwareFetch";

function storageWithDeviceId(value: string | null) {
  return {
    getItem(key: string) {
      return key === "neves-estoque.registered-device-id.v1" ? value : null;
    }
  };
}

describe("createDeviceAwareFetch", () => {
  it("envia x-device-id quando existe um dispositivo registrado válido", async () => {
    const fetchImpl = vi.fn(async () => new Response(null, { status: 204 }));
    const deviceId = "22222222-2222-4222-8222-222222222222";
    const deviceFetch = createDeviceAwareFetch(fetchImpl, () => storageWithDeviceId(deviceId));

    await deviceFetch("https://example.test/rest", {
      headers: { Authorization: "Bearer token" }
    });

    const init = fetchImpl.mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);

    expect(headers.get("x-device-id")).toBe(deviceId);
    expect(headers.get("authorization")).toBe("Bearer token");
  });

  it("não envia x-device-id quando o valor local é inválido", async () => {
    const fetchImpl = vi.fn(async () => new Response(null, { status: 204 }));
    const deviceFetch = createDeviceAwareFetch(fetchImpl, () => storageWithDeviceId("inválido"));

    await deviceFetch("https://example.test/rest");

    const init = fetchImpl.mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.has("x-device-id")).toBe(false);
  });
});

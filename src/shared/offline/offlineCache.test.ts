import { describe, expect, it } from "vitest";
import {
  canUseVerifiedOfflineAccess,
  isLikelyNetworkError
} from "./offlineCache";
import type { VerifiedOfflineAccessRecord } from "./offlineDb";

describe("offline policy", () => {
  it("reconhece falha típica de rede sem confundir erro funcional", () => {
    expect(isLikelyNetworkError(new TypeError("Failed to fetch"))).toBe(true);
    expect(isLikelyNetworkError(new Error("Produto não encontrado."))).toBe(false);
  });

  it("só reutiliza acesso offline do mesmo usuário e dispositivo", () => {
    const record: VerifiedOfflineAccessRecord = {
      authUserId: "auth-1",
      appUserId: "app-1",
      roleName: "admin",
      deviceId: "device-1",
      deviceKey: "device-key-1",
      displayName: "Teste",
      username: "teste",
      authMethod: "password",
      verifiedAt: "2026-09-27T20:00:00.000Z"
    };

    expect(canUseVerifiedOfflineAccess(record, "auth-1", "device-key-1")).toBe(true);
    expect(canUseVerifiedOfflineAccess(record, "auth-2", "device-key-1")).toBe(false);
    expect(canUseVerifiedOfflineAccess(record, "auth-1", "device-key-2")).toBe(false);
  });
});

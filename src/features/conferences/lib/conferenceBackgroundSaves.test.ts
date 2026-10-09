import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { ConferenceBackgroundSaves } from "./conferenceBackgroundSaves";
import type { CategoryConferenceWriteInput } from "../api/conferences";
import type { SavePendingConferenceMetadata } from "../../offline/lib/pendingOperations";

const payload: CategoryConferenceWriteInput = {
  categoryId: "categoria",
  effectiveAt: "2026-10-09T14:00:00.000Z",
  physicalResponsible: "Responsável",
  deviceId: "aparelho",
  idempotencyKey: "mesma-chave",
  observation: null,
  items: [{ productId: "produto", quantity: 0 }]
};

const metadata: SavePendingConferenceMetadata = {
  authUserId: "usuario-a",
  appUserId: "perfil-a",
  deviceId: "aparelho",
  actorLabel: "Teste",
  categoryLabel: "PANIFICACAO"
};

function createManager(options?: { rejectSend?: boolean }) {
  const stage = vi.fn(async () => "arquivo-local");
  const send = vi.fn(async () => {
    if (options?.rejectSend) throw new Error("Rede indisponível");
  });
  const reconcile = vi.fn(async () => null as string | null);
  const remove = vi.fn(async () => {});
  const manager = new ConferenceBackgroundSaves({ stage, send, reconcile, remove });
  return { manager, stage, send, reconcile, remove };
}

describe("conferência Web em segundo plano", () => {
  it("guarda antes de enviar e só marca salva após resposta oficial", async () => {
    const { manager, stage, send, remove } = createManager();
    const queryClient = new QueryClient();
    await manager.submit(payload, metadata, queryClient);
    expect(stage).toHaveBeenCalledOnce();
    expect(stage.mock.invocationCallOrder[0]).toBeLessThan(send.mock.invocationCallOrder[0]!);
    await vi.waitFor(() => {
      expect(manager.status("usuario-a", "categoria")?.phase).toBe("saved");
    });
    expect(remove).toHaveBeenCalledWith("arquivo-local");
  });

  it("preserva a cópia após falha, exige retry manual e usa a mesma chave", async () => {
    const { manager, send, remove, reconcile } = createManager({ rejectSend: true });
    await manager.submit(payload, metadata, new QueryClient());
    await vi.waitFor(() => expect(manager.status("usuario-a", "categoria")?.phase).toBe("failed"));
    expect(remove).not.toHaveBeenCalled();
    expect(reconcile).toHaveBeenCalledWith("mesma-chave");
    await expect(manager.submit(payload, metadata, new QueryClient())).rejects.toThrow();
    expect(send).toHaveBeenCalledTimes(1);
    manager.retry("usuario-a", "categoria");
    await vi.waitFor(() => expect(send).toHaveBeenCalledTimes(2));
    expect(send.mock.calls[1]?.[0]).toMatchObject({ idempotencyKey: "mesma-chave" });
    await vi.waitFor(() => expect(manager.status("usuario-a", "categoria")?.phase).toBe("failed"));
    await manager.discard("usuario-a", "categoria");
    expect(remove).toHaveBeenCalledWith("arquivo-local");
    expect(manager.status("usuario-a", "categoria")).toBeUndefined();
  });

  it("separa os status por usuário autenticado", async () => {
    const { manager } = createManager({ rejectSend: true });
    await manager.submit(payload, metadata, new QueryClient());
    await vi.waitFor(() => expect(manager.status("usuario-a", "categoria")?.phase).toBe("failed"));
    expect(manager.status("usuario-b", "categoria")).toBeUndefined();
  });
});

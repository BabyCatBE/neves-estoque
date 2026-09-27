import { describe, expect, it } from "vitest";
import type { CreateEntryInput } from "../../entries/api/entries";
import {
  buildPendingConferenceRecord,
  buildPendingEntryRecord
} from "./pendingOperations";

describe("pending offline entry", () => {
  it("preserva o payload oficial e marca como pendente de confirmação", () => {
    const payload: CreateEntryInput = {
      supplierId: "supplier-1",
      newSupplier: null,
      effectiveAt: "2026-09-27T10:00:00.000-03:00",
      deviceId: "device-1",
      idempotencyKey: "idem-1",
      observation: "teste",
      items: [{ productId: "product-1", quantity: 5, unitPrice: 10 }]
    };

    const record = buildPendingEntryRecord(
      payload,
      {
        authUserId: "auth-1",
        appUserId: "app-1",
        deviceId: "device-1",
        actorLabel: "Elias",
        supplierLabel: "Fornecedor Teste"
      },
      "local-1",
      "2026-09-27T13:00:00.000Z"
    );

    expect(record.id).toBe("local-1");
    expect(record.status).toBe("pending_confirmation");
    expect(record.kind).toBe("entry");
    expect(record.idempotencyKey).toBe("idem-1");
    expect(record.summaryTitle).toBe("Fornecedor Teste");
    expect(record.itemCount).toBe(1);
    expect(record.payload).toEqual(payload);
    it("guarda Conferência offline sem transformá-la em registro oficial", () => {
    const record = buildPendingConferenceRecord(
      {
        categoryId: "category-1",
        effectiveAt: "2026-09-27T11:00:00.000-03:00",
        physicalResponsible: "Responsável",
        deviceId: "device-1",
        idempotencyKey: "conference-idem-1",
        observation: null,
        items: [{ productId: "product-1", quantity: 0 }]
      },
      {
        authUserId: "auth-1",
        appUserId: "app-1",
        deviceId: "device-1",
        actorLabel: "Elias",
        categoryLabel: "Farinhas"
      },
      "local-conference-1",
      "2026-09-27T14:00:00.000Z"
    );

    expect(record.kind).toBe("conference");
    expect(record.status).toBe("pending_confirmation");
    expect(record.summaryTitle).toBe("Farinhas");
    expect(record.itemCount).toBe(1);
    expect(record.payload.items[0]?.quantity).toBe(0);
  });
});
});

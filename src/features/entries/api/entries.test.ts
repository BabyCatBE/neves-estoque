import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  createFakePostgrest,
  testUuid,
  type FakeRow
} from "../../../shared/testing/fakePostgrest";

const mocked = vi.hoisted(() => ({
  current: null as null | { from: (table: string) => unknown }
}));

vi.mock("../../../shared/lib/supabase", () => ({
  supabase: {
    from: (table: string) => {
      if (!mocked.current) throw new Error("cliente falso não configurado");
      return mocked.current.from(table);
    }
  }
}));

import {
  findEntryConferenceConflicts,
  listEntryHistory,
  type CreateEntryInput
} from "./entries";

const SUPPLIER = testUuid(1, 1);
const PRODUCT = testUuid(2, 1);
const USER = testUuid(3, 1);
const DEVICE = testUuid(4, 1);

function historyTables(): Record<string, FakeRow[]> {
  const entries = Array.from({ length: 5 }, (_, index) => ({
    id: testUuid(10, index + 1),
    supplier_id: SUPPLIER,
    effective_at: `2026-09-${String(20 + index).padStart(2, "0")}T12:00:00.000Z`,
    created_at: `2026-09-${String(20 + index).padStart(2, "0")}T12:01:00.000Z`,
    observation: null,
    deleted_at: null
  }));
  const entryItems = entries.map((entry, index) => ({
    id: testUuid(11, index + 1),
    entry_id: entry.id,
    product_id: PRODUCT,
    quantity: index + 1,
    unit_price: 10,
    position: 1
  }));

  return {
    entries,
    entry_items: entryItems,
    suppliers: [{ id: SUPPLIER, name: "Fornecedor" }],
    products: [{ id: PRODUCT, name: "Farinha" }]
  };
}

function conflictTables(): Record<string, FakeRow[]> {
  const oldConferences = Array.from({ length: 4 }, (_, index) => ({
    id: testUuid(20, index + 1),
    effective_at: `2026-09-${String(20 + index).padStart(2, "0")}T10:00:00.000Z`,
    created_at: `2026-09-${String(20 + index).padStart(2, "0")}T10:01:00.000Z`,
    physical_responsible: "Antigo",
    registered_by: USER,
    device_id: DEVICE,
    deleted_at: null
  }));
  const current = {
    id: testUuid(20, 5),
    effective_at: "2026-09-30T10:00:00.000Z",
    created_at: "2026-09-30T10:01:00.000Z",
    physical_responsible: "Elias",
    registered_by: USER,
    device_id: DEVICE,
    deleted_at: null
  };
  return {
    conferences: [...oldConferences, current],
    conference_items: [
      ...oldConferences.map((conference, index) => ({
        id: testUuid(21, index + 1),
        conference_id: conference.id,
        product_id: PRODUCT,
        quantity: 100 + index
      })),
      {
        id: testUuid(21, 5),
        conference_id: current.id,
        product_id: PRODUCT,
        quantity: 7
      }
    ],
    products: [{ id: PRODUCT, name: "Farinha" }],
    app_users: [{
      auth_user_id: USER,
      display_name: "Elias",
      username: null,
      email: null
    }],
    devices: [{ id: DEVICE, friendly_name: "Celular" }]
  };
}

describe("entries — leituras históricas completas", () => {
  beforeEach(() => {
    mocked.current = null;
  });

  it("histórico de Entradas continua lendo após resposta curta do servidor", async () => {
    const fake = createFakePostgrest({ tables: historyTables(), maxRows: 2 });
    mocked.current = fake.client;

    const history = await listEntryHistory();

    expect(history).toHaveLength(5);
    expect(history.map((item) => item.productNames)).toEqual([
      ["Farinha"],
      ["Farinha"],
      ["Farinha"],
      ["Farinha"],
      ["Farinha"]
    ]);
    expect(history.map((item) => item.totalKnown)).toEqual([50, 40, 30, 20, 10]);
    expect(fake.requestsFor("entries").map((request) => request.returned)).toEqual([2, 2, 1, 0]);
    expect(fake.requestsFor("entry_items").map((request) => request.returned)).toEqual([2, 2, 1, 0]);
  });

  it("conflito do dia não some atrás de itens históricos antigos do mesmo Produto", async () => {
    const fake = createFakePostgrest({ tables: conflictTables(), maxRows: 2 });
    mocked.current = fake.client;

    const input: CreateEntryInput = {
      supplierId: SUPPLIER,
      newSupplier: null,
      effectiveAt: "2026-09-30T12:00:00.000Z",
      deviceId: DEVICE,
      idempotencyKey: testUuid(30, 1),
      observation: null,
      items: [{ productId: PRODUCT, quantity: 3, unitPrice: 10 }]
    };

    const conflicts = await findEntryConferenceConflicts(input);

    expect(conflicts).toHaveLength(1);
    expect(conflicts[0]).toMatchObject({
      effectiveAt: "2026-09-30T10:00:00.000Z",
      physicalResponsible: "Elias",
      registeredByLabel: "Elias",
      deviceLabel: "Celular"
    });
    expect(conflicts[0]?.items).toEqual([
      {
        productId: PRODUCT,
        productName: "Farinha",
        entryQuantity: 3,
        conferenceQuantity: 7
      }
    ]);
    for (const request of fake.requestsFor("conference_items")) {
      expect(request.filters).toContain("conference_id.in.(1)");
      expect(request.filters).toContain("product_id.in.(1)");
    }
  });
});

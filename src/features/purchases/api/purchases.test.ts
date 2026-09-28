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
  listPurchaseIntelligenceProducts,
  listSupplierPurchaseProducts
} from "./purchases";

const DAY_MS = 24 * 60 * 60 * 1000;
// Base fixa: as duas montagens das tabelas precisam ter exatamente os mesmos fatos.
const BASE_TIME = Date.now();
const daysAgo = (days: number) => new Date(BASE_TIME - days * DAY_MS).toISOString();
const daysAhead = (days: number) => new Date(BASE_TIME + days * DAY_MS).toISOString();

const P1 = testUuid(10, 1);
const P2 = testUuid(10, 2);
const S1 = testUuid(20, 1);
const S2 = testUuid(20, 2);

function supplier(id: string) {
  return {
    id,
    deleted_at: null,
    purchase_frequency_days: 7,
    preferred_order_weekday: null,
    average_delivery_days: 2,
    safety_margin_days: 2
  };
}

/**
 * P1 tem 4 conferências válidas a cada 14 dias (consumo de 2/dia) e uma única
 * Entrada, do fornecedor S2, gravada por último (maior id): ela só aparece numa
 * página posterior quando o servidor corta as respostas em 2 linhas.
 * Com a Entrada: média ponderada = 168/70 = 2,4/dia. Sem ela: 2/dia.
 */
function buildTables(): Record<string, FakeRow[]> {
  const conferences = [
    { id: testUuid(30, 1), effective_at: daysAgo(50), created_at: daysAgo(50), deleted_at: null },
    { id: testUuid(30, 2), effective_at: daysAgo(36), created_at: daysAgo(36), deleted_at: null },
    { id: testUuid(30, 3), effective_at: daysAgo(22), created_at: daysAgo(22), deleted_at: null },
    { id: testUuid(30, 4), effective_at: daysAgo(8), created_at: daysAgo(8), deleted_at: null },
    // Na Lixeira: não pode entrar no cálculo.
    { id: testUuid(30, 5), effective_at: daysAgo(15), created_at: daysAgo(15), deleted_at: daysAgo(1) },
    // Data futura: não pode entrar no cálculo.
    { id: testUuid(30, 6), effective_at: daysAhead(3), created_at: daysAgo(1), deleted_at: null }
  ];
  const conferenceItems = [
    { id: testUuid(31, 1), conference_id: testUuid(30, 1), product_id: P1, quantity: 100 },
    { id: testUuid(31, 2), conference_id: testUuid(30, 2), product_id: P1, quantity: 72 },
    { id: testUuid(31, 3), conference_id: testUuid(30, 3), product_id: P1, quantity: 44 },
    { id: testUuid(31, 4), conference_id: testUuid(30, 4), product_id: P1, quantity: 16 },
    { id: testUuid(31, 5), conference_id: testUuid(30, 5), product_id: P1, quantity: 999 },
    { id: testUuid(31, 6), conference_id: testUuid(30, 6), product_id: P1, quantity: 0 }
  ];
  const entries = [
    { id: testUuid(40, 1), supplier_id: S1, effective_at: daysAgo(40), deleted_at: null },
    { id: testUuid(40, 2), supplier_id: S1, effective_at: daysAgo(30), deleted_at: null },
    { id: testUuid(40, 3), supplier_id: S2, effective_at: daysAgo(15), deleted_at: null }
  ];
  const entryItems = [
    { id: testUuid(41, 1), entry_id: testUuid(40, 1), product_id: P2, quantity: 5 },
    { id: testUuid(41, 2), entry_id: testUuid(40, 2), product_id: P2, quantity: 5 },
    { id: testUuid(41, 3), entry_id: testUuid(40, 3), product_id: P1, quantity: 14 }
  ];

  return {
    products: [
      { id: P1, name: "Farinha", unit: "KG", category_id: null, sort_order: 1, deleted_at: null },
      { id: P2, name: "Açúcar", unit: "KG", category_id: null, sort_order: 2, deleted_at: null }
    ],
    stock_current: [
      { product_id: P1, current_quantity: 16, current_supplier_id: S1 },
      { product_id: P2, current_quantity: 10, current_supplier_id: S1 }
    ],
    suppliers: [supplier(S1), supplier(S2)],
    conferences,
    conference_items: conferenceItems,
    entries,
    entry_items: entryItems
  };
}

function useFake(options: { maxRows?: number; failOn?: { table: string; request: number } } = {}) {
  const fake = createFakePostgrest({ tables: buildTables(), ...options });
  mocked.current = fake.client;
  return fake;
}

describe("listPurchaseIntelligenceProducts — leitura histórica completa", () => {
  beforeEach(() => {
    mocked.current = null;
  });

  it("registro que só existe numa página posterior altera o consumo e os fornecedores", async () => {
    const fake = useFake({ maxRows: 2 });
    const products = await listPurchaseIntelligenceProducts();
    const flour = products.find((product) => product.productId === P1)!;

    expect(flour.usageInsights.status).toBe("ready");
    expect(flour.usageInsights.validIntervals).toBe(3);
    expect(flour.usageInsights.dailyAverage).toBeCloseTo(2.4, 10);
    expect(flour.historicalSupplierIds).toEqual([S2]);

    // A Entrada de S2 estava na 2ª página de entry_items (servidor corta em 2).
    expect(fake.requestsFor("entry_items").map((request) => request.returned)).toEqual([2, 1, 0]);
    // A 4ª conferência válida também só chega depois da 1ª página.
    expect(
      fake.requestsFor("conference_items").map((request) => request.returned)
    ).toEqual([2, 2, 2, 0]);
  });

  it("com base pequena (uma página) o resultado é idêntico ao da leitura paginada", async () => {
    useFake();
    const singlePage = await listPurchaseIntelligenceProducts();
    useFake({ maxRows: 2 });
    const paged = await listPurchaseIntelligenceProducts();

    const strip = (list: typeof paged) =>
      list.map((product) => ({
        productId: product.productId,
        historicalSupplierIds: product.historicalSupplierIds,
        usage: product.usageInsights,
        suggested: product.projection.suggestedQuantity,
        status: product.projection.status
      }));
    expect(strip(paged)).toEqual(strip(singlePage));
  });

  it("preserva filtros: Lixeira e datas futuras continuam fora do cálculo", async () => {
    const fake = useFake({ maxRows: 2 });
    await listPurchaseIntelligenceProducts();

    for (const request of fake.requestsFor("conferences")) {
      expect(request.filters).toContain("deleted_at.is.null");
      expect(request.filters.some((filter) => filter.startsWith("effective_at.lte."))).toBe(true);
    }
    for (const request of fake.requestsFor("entries")) {
      expect(request.filters).toContain("deleted_at.is.null");
      expect(request.filters.some((filter) => filter.startsWith("effective_at.lte."))).toBe(true);
    }
  });

  it("fornecedor histórico visto só em página posterior aparece na lista do fornecedor", async () => {
    useFake({ maxRows: 2 });
    const forS2 = await listSupplierPurchaseProducts(S2);
    expect(forS2.map((product) => product.productId)).toEqual([P1]);
    // Fornecedor atual é S1: regra comercial preservada.
    expect(forS2[0]?.projection.status).toBe("other_supplier");
  });

  it("erro em página intermediária do histórico falha a leitura inteira", async () => {
    useFake({ maxRows: 2, failOn: { table: "conference_items", request: 2 } });
    await expect(listPurchaseIntelligenceProducts()).rejects.toMatchObject({
      message: "falha simulada"
    });
  });
});

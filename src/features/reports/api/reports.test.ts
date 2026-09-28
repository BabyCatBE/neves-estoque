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

import { loadMonthlyStockReportFacts } from "./reports";

const P = (index: number) => testUuid(10, index);
const E = (index: number) => testUuid(20, index);
const EI = (index: number) => testUuid(21, index);
const C = (index: number) => testUuid(30, index);
const CI = (index: number) => testUuid(31, index);

// Produtos criados fora da ordem de id: a saída deve seguir created_at, id.
const products: FakeRow[] = [
  product(P(1), "Farinha", "2026-01-03T10:00:00+00:00"),
  product(P(2), "Açúcar", "2026-01-01T10:00:00+00:00"),
  product(P(3), "Ovos", "2026-01-02T10:00:00.5+00:00"),
  product(P(4), "Leite", "2026-01-02T10:00:00.25+00:00"),
  { ...product(P(5), "Sal", "2026-01-02T10:00:00.25+00:00"), deleted_at: "2026-03-01T00:00:00+00:00" }
];

function product(id: string, name: string, createdAt: string): FakeRow {
  return {
    id,
    name,
    unit: "kg",
    created_at: createdAt,
    deleted_at: null,
    initial_stock_quantity: null,
    initial_stock_at: null,
    initial_price: null,
    initial_price_at: null
  };
}

const entries: FakeRow[] = [1, 2, 3, 4].map((index) => ({
  id: E(index),
  effective_at: `2026-02-0${index}T12:00:00+00:00`,
  created_at: `2026-02-0${index}T12:00:00+00:00`,
  deleted_at: null
}));
entries.push({ ...entries[0], id: E(9), deleted_at: "2026-02-10T00:00:00+00:00" });

const entryItems: FakeRow[] = [1, 2, 3, 4, 5].map((index) => ({
  id: EI(index),
  entry_id: E(Math.min(index, 4)),
  product_id: P(index),
  quantity: index,
  unit_price: index * 2,
  position: 0
}));
// Item de Entrada apagada: deve ficar de fora (Entrada não vem na leitura).
entryItems.push({ id: EI(9), entry_id: E(9), product_id: P(1), quantity: 99, unit_price: 1, position: 0 });

const conferences: FakeRow[] = [1, 2, 3].map((index) => ({
  id: C(index),
  effective_at: `2026-02-2${index}T12:00:00+00:00`,
  created_at: `2026-02-2${index}T12:00:00+00:00`,
  deleted_at: null
}));

const conferenceItems: FakeRow[] = [
  { id: CI(1), conference_id: C(3), product_id: P(1), quantity: 7, position: 0 },
  { id: CI(2), conference_id: C(1), product_id: P(2), quantity: 3, position: 0 },
  { id: CI(3), conference_id: C(2), product_id: P(3), quantity: 4, position: 0 },
  { id: CI(4), conference_id: C(2), product_id: P(4), quantity: 5, position: 0 },
  { id: CI(5), conference_id: C(1), product_id: P(5), quantity: 6, position: 0 }
];

const audits: FakeRow[] = [
  audit(1, P(5), "SOFT_DELETE", "2026-03-01T00:00:00+00:00"),
  audit(2, P(1), "UPDATE", "2026-03-02T00:00:00+00:00"),
  audit(3, P(2), "PRODUCT_MERGE", "2026-03-03T00:00:00+00:00"),
  audit(4, P(5), "RESTORE", "2026-03-04T00:00:00+00:00"),
  { ...audit(5, P(3), "PRODUCT_MERGE", "2026-03-05T00:00:00+00:00"), entity_type: "entries" },
  audit(6, P(5), "SOFT_DELETE", "2026-03-06T00:00:00+00:00")
];

function audit(id: number, entityId: string, action: string, createdAt: string): FakeRow {
  return { id, entity_type: "products", entity_id: entityId, action, created_at: createdAt };
}

function tables() {
  return {
    products,
    entries,
    entry_items: entryItems,
    conferences,
    conference_items: conferenceItems,
    audit_log: audits
  };
}

function useFake(options: { maxRows?: number; failOn?: { table: string; request: number } } = {}) {
  const fake = createFakePostgrest({ tables: tables(), ...options });
  mocked.current = fake.client as unknown as { from: (table: string) => unknown };
  return fake;
}

describe("loadMonthlyStockReportFacts — paginação por cursor", () => {
  beforeEach(() => {
    mocked.current = null;
  });

  it("servidor com teto menor que o pedido: lê todas as páginas de todas as tabelas", async () => {
    const reference = await (async () => {
      useFake();
      return loadMonthlyStockReportFacts();
    })();

    const fake = useFake({ maxRows: 2 });
    const capped = await loadMonthlyStockReportFacts();

    expect(capped).toEqual(reference);
    expect(capped.products).toHaveLength(5);
    expect(capped.entries).toHaveLength(5);
    expect(capped.conferences).toHaveLength(5);
    expect(capped.merges).toHaveLength(1);
    expect(capped.lifecycle).toHaveLength(3);

    // Várias páginas por tabela, fim só com página vazia.
    for (const table of ["products", "entry_items", "conference_items", "audit_log"]) {
      const returned = fake.requestsFor(table).map((request) => request.returned);
      expect(returned.length).toBeGreaterThan(2);
      expect(returned.at(-1)).toBe(0);
    }
  });

  it("preserva a ordem dos produtos por created_at e id", async () => {
    useFake({ maxRows: 2 });
    const facts = await loadMonthlyStockReportFacts();
    expect(facts.products.map((item) => item.name)).toEqual([
      "Açúcar",
      "Leite",
      "Sal",
      "Ovos",
      "Farinha"
    ]);
  });

  it("repete filtros em todas as páginas e exclui Entradas/Conferências apagadas", async () => {
    const fake = useFake({ maxRows: 1 });
    const facts = await loadMonthlyStockReportFacts();

    for (const request of fake.requestsFor("entries")) {
      expect(request.filters).toContain("deleted_at.is.null");
    }
    for (const request of fake.requestsFor("audit_log")) {
      expect(request.filters).toContain("entity_type.eq.products");
      expect(request.filters).toContain("action.in.(3)");
    }
    expect(facts.entries.some((entry) => entry.quantity === 99)).toBe(false);
    expect(facts.merges.map((merge) => merge.id)).toEqual([3]);
    expect(facts.lifecycle.map((event) => event.id)).toEqual([1, 4, 6]);
  });

  it("fato só em página posterior entra no relatório", async () => {
    useFake({ maxRows: 1 });
    const facts = await loadMonthlyStockReportFacts();
    // Último item de Conferência (maior id) só chega na 5ª página.
    expect(facts.conferences.find((fact) => fact.productId === P(5))?.quantity).toBe(6);
    expect(facts.lifecycle.at(-1)?.action).toBe("SOFT_DELETE");
  });

  it("erro em página intermediária falha a leitura inteira", async () => {
    useFake({ maxRows: 1, failOn: { table: "audit_log", request: 3 } });
    await expect(loadMonthlyStockReportFacts()).rejects.toMatchObject({
      message: "falha simulada"
    });
  });
});

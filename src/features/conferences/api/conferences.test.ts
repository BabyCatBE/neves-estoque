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

// Dependências do módulo que não participam da revisão de consumo.
vi.mock("../../../shared/offline/offlineCache", () => ({
  OFFLINE_CACHE_KEYS: { conferenceCategories: "conferences.categories" },
  readThroughOfflineCache: vi.fn()
}));
vi.mock("../../categories/api/categories", () => ({ listActiveCategories: vi.fn() }));
vi.mock("../../products/api/products", () => ({ listActiveProducts: vi.fn() }));

import { reviewConferenceConsumption } from "./conferences";

const DAY_MS = 24 * 60 * 60 * 1000;
const CANDIDATE_TIME = Date.parse("2026-09-20T12:00:00.000Z");
const at = (days: number) => new Date(CANDIDATE_TIME + days * DAY_MS).toISOString();

const P1 = testUuid(10, 1);
const P2 = testUuid(10, 2);
const EDITED_CONFERENCE = testUuid(30, 6);

/**
 * P1: 4 conferências anteriores a cada 14 dias (consumo de 2/dia). A última
 * conferência anterior (-8 dias, 16) e a Entrada de 30 (-4 dias) têm os maiores
 * ids: com o servidor cortando respostas em 2 linhas, só chegam em páginas
 * posteriores. Consumo esperado até a candidata: 2/dia × 8 dias = 16.
 */
function buildTables(): Record<string, FakeRow[]> {
  const conferences = [
    { id: testUuid(30, 1), effective_at: at(-50), created_at: at(-50), deleted_at: null },
    { id: testUuid(30, 2), effective_at: at(-36), created_at: at(-36), deleted_at: null },
    { id: testUuid(30, 3), effective_at: at(-22), created_at: at(-22), deleted_at: null },
    { id: testUuid(30, 4), effective_at: at(-8), created_at: at(-8), deleted_at: null },
    // Na Lixeira: ignorada.
    { id: testUuid(30, 5), effective_at: at(-6), created_at: at(-6), deleted_at: at(-1) },
    // A própria conferência em edição: ignorada via excludeConferenceId.
    { id: EDITED_CONFERENCE, effective_at: at(-2), created_at: at(-2), deleted_at: null },
    // Posterior à candidata: fora do limite de data.
    { id: testUuid(30, 7), effective_at: at(1), created_at: at(1), deleted_at: null }
  ];
  const conferenceItems = [
    { id: testUuid(31, 1), conference_id: testUuid(30, 1), product_id: P1, quantity: 100 },
    { id: testUuid(31, 2), conference_id: testUuid(30, 2), product_id: P1, quantity: 72 },
    { id: testUuid(31, 3), conference_id: testUuid(30, 3), product_id: P1, quantity: 44 },
    { id: testUuid(31, 4), conference_id: testUuid(30, 4), product_id: P1, quantity: 16 },
    { id: testUuid(31, 5), conference_id: testUuid(30, 5), product_id: P1, quantity: 999 },
    { id: testUuid(31, 6), conference_id: EDITED_CONFERENCE, product_id: P1, quantity: 500 },
    { id: testUuid(31, 7), conference_id: testUuid(30, 7), product_id: P1, quantity: 1 },
    // Outro produto: não entra na revisão de P1.
    { id: testUuid(31, 8), conference_id: testUuid(30, 4), product_id: P2, quantity: 3 }
  ];
  const entries = [
    { id: testUuid(40, 1), effective_at: at(-60), deleted_at: null },
    { id: testUuid(40, 2), effective_at: at(-55), deleted_at: null },
    { id: testUuid(40, 3), effective_at: at(-4), deleted_at: null },
    // Na Lixeira e posterior à candidata: ignoradas.
    { id: testUuid(40, 4), effective_at: at(-3), deleted_at: at(-1) },
    { id: testUuid(40, 5), effective_at: at(2), deleted_at: null }
  ];
  const entryItems = [
    { id: testUuid(41, 1), entry_id: testUuid(40, 1), product_id: P1, quantity: 5 },
    { id: testUuid(41, 2), entry_id: testUuid(40, 2), product_id: P1, quantity: 5 },
    { id: testUuid(41, 3), entry_id: testUuid(40, 3), product_id: P1, quantity: 30 },
    { id: testUuid(41, 4), entry_id: testUuid(40, 4), product_id: P1, quantity: 700 },
    { id: testUuid(41, 5), entry_id: testUuid(40, 5), product_id: P1, quantity: 700 }
  ];

  return {
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

function review(candidateQuantity: number, excludeConferenceId?: string) {
  return reviewConferenceConsumption({
    effectiveAt: at(0),
    items: [{ productId: P1, quantity: candidateQuantity }],
    excludeConferenceId
  });
}

describe("reviewConferenceConsumption — leitura histórica completa", () => {
  beforeEach(() => {
    mocked.current = null;
  });

  it("Entrada só em página posterior evita alerta falso de inconsistência", async () => {
    // 16 (anterior) + 30 (Entrada) − 30 (candidata) = 16, igual ao esperado.
    const fake = useFake({ maxRows: 2 });
    await expect(review(30, EDITED_CONFERENCE)).resolves.toEqual([]);
    expect(fake.requestsFor("entry_items").map((request) => request.returned)).toEqual([2, 2, 1, 0]);
  });

  it("conferência anterior e Entrada em páginas posteriores produzem o alerta correto", async () => {
    // 16 + 30 − 46 = 0 consumido; esperado 16 → abaixo do esperado (−100%).
    useFake({ maxRows: 2 });
    const warnings = await review(46, EDITED_CONFERENCE);
    expect(warnings).toHaveLength(1);
    expect(warnings[0]).toMatchObject({
      productId: P1,
      kind: "below",
      expectedConsumption: 16,
      actualConsumption: 0,
      intervalDays: 8
    });
  });

  it("preserva excludeConferenceId mesmo quando a conferência editada está numa página posterior", async () => {
    useFake({ maxRows: 2 });
    const excluded = await review(46, EDITED_CONFERENCE);
    useFake({ maxRows: 2 });
    const notExcluded = await review(46);

    // Sem excluir, a "anterior" vira a própria conferência em edição (−2 dias, 500).
    expect(notExcluded).toHaveLength(1);
    expect(notExcluded[0]?.intervalDays).toBe(2);
    expect(excluded[0]?.intervalDays).toBe(8);
  });

  it("preserva filtros de produto e de data em todas as páginas", async () => {
    const fake = useFake({ maxRows: 2 });
    await review(30, EDITED_CONFERENCE);

    for (const request of fake.requestsFor("conference_items")) {
      expect(request.filters).toContain("product_id.in.(1)");
    }
    for (const request of fake.requestsFor("entry_items")) {
      expect(request.filters).toContain("product_id.in.(1)");
    }
    for (const request of fake.requestsFor("conferences")) {
      expect(request.filters).toContain("deleted_at.is.null");
      expect(request.filters).toContain(`effective_at.lt.${at(0)}`);
    }
    for (const request of fake.requestsFor("entries")) {
      expect(request.filters).toContain("deleted_at.is.null");
      expect(request.filters).toContain(`effective_at.lte.${at(0)}`);
    }
  });

  it("com base pequena (uma página) o resultado é idêntico", async () => {
    useFake();
    const singlePage = await review(46, EDITED_CONFERENCE);
    useFake({ maxRows: 2 });
    const paged = await review(46, EDITED_CONFERENCE);
    expect(paged).toEqual(singlePage);
  });

  it("erro em página intermediária falha a revisão inteira", async () => {
    useFake({ maxRows: 2, failOn: { table: "entries", request: 2 } });
    await expect(review(30, EDITED_CONFERENCE)).rejects.toMatchObject({
      message: "falha simulada"
    });
  });

  it("sem produtos não consulta o servidor", async () => {
    const fake = useFake();
    await expect(
      reviewConferenceConsumption({ effectiveAt: at(0), items: [] })
    ).resolves.toEqual([]);
    expect(fake.requests).toHaveLength(0);
  });
});

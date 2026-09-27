import { describe, expect, it } from "vitest";
import {
  calculateProductStockSnapshot,
  historicalDateKey,
  type HistoricalConferenceFact,
  type HistoricalEntryFact,
  type ProductHistoricalBasis
} from "./monthlyStockSnapshot";

const emptyProduct: ProductHistoricalBasis = {
  initialStockQuantity: null,
  initialStockAt: null,
  initialPrice: null,
  initialPriceAt: null
};

function conference(
  id: string,
  effectiveAt: string,
  quantity: number,
  createdAt = effectiveAt
): HistoricalConferenceFact {
  return { id, effectiveAt, createdAt, quantity };
}

function entry(
  id: string,
  effectiveAt: string,
  quantity: number,
  unitPrice: number | null = null,
  createdAt = effectiveAt,
  position = 1
): HistoricalEntryFact {
  return { id, effectiveAt, createdAt, quantity, unitPrice, position };
}

describe("monthlyStockSnapshot", () => {
  it("calcula conferência + Entradas posteriores sem inventar consumo", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-25T12:00:00-03:00", 10)],
      entries: [entry("e1", "2026-09-28T12:00:00-03:00", 5, 8.5)]
    });

    expect(result.quantity).toBe(15);
    expect(result.quantitySource).toBe("conference_plus_entries");
    expect(result.entriesAfterCheckpointQuantity).toBe(5);
    expect(result.value).toBe(127.5);
    expect(result.hasExactPhysicalClose).toBe(false);
  });

  it("não soma novamente Entrada anterior à última Conferência", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-25T12:00:00-03:00", 10)],
      entries: [
        entry("e-old", "2026-09-20T12:00:00-03:00", 7),
        entry("e-new", "2026-09-28T12:00:00-03:00", 5)
      ]
    });

    expect(result.quantity).toBe(15);
    expect(result.contributingEntryItemIds).toEqual(["e-new"]);
  });

  it("marca fechamento físico exato quando a última Conferência é no dia de referência", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-30T10:00:00-03:00", 12)],
      entries: []
    });

    expect(result.quantity).toBe(12);
    expect(result.hasExactPhysicalClose).toBe(true);
  });

  it("soma Entrada realmente posterior a uma Conferência feita no próprio fechamento", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-30T10:00:00-03:00", 12)],
      entries: [entry("e1", "2026-09-30T15:00:00-03:00", 3)]
    });

    expect(result.quantity).toBe(15);
    expect(result.hasExactPhysicalClose).toBe(true);
  });

  it("desempata Conferências por effective_at, created_at e id como o estoque atual", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [
        conference(
          "00000000-0000-0000-0000-000000000001",
          "2026-09-30T10:00:00-03:00",
          10,
          "2026-09-30T12:00:00-03:00"
        ),
        conference(
          "00000000-0000-0000-0000-000000000002",
          "2026-09-30T10:00:00-03:00",
          14,
          "2026-09-30T12:00:00-03:00"
        )
      ],
      entries: []
    });

    expect(result.quantity).toBe(14);
    expect(result.checkpointConferenceId).toBe(
      "00000000-0000-0000-0000-000000000002"
    );
  });

  it("usa estoque inicial como checkpoint quando não há Conferência", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: {
        ...emptyProduct,
        initialStockQuantity: 20,
        initialStockAt: "2026-09-10T09:00:00-03:00"
      },
      conferences: [],
      entries: [entry("e1", "2026-09-20T09:00:00-03:00", 4)]
    });

    expect(result.quantity).toBe(24);
    expect(result.quantitySource).toBe("initial_stock_plus_entries");
  });

  it("permite que Entradas estabeleçam o estoque quando não existe checkpoint anterior", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [],
      entries: [
        entry("e1", "2026-09-10T09:00:00-03:00", 4),
        entry("e2", "2026-09-20T09:00:00-03:00", 6)
      ]
    });

    expect(result.quantity).toBe(10);
    expect(result.quantitySource).toBe("entries_only");
    expect(result.hasStockData).toBe(true);
  });

  it("retorna Sem dados como null quando não há qualquer fato de estoque", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [],
      entries: []
    });

    expect(result.quantity).toBeNull();
    expect(result.quantitySource).toBe("none");
    expect(result.hasStockData).toBe(false);
    expect(result.value).toBeNull();
  });

  it("ignora preço em branco e mantém o último preço real válido", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-25T12:00:00-03:00", 10)],
      entries: [
        entry("priced", "2026-09-20T12:00:00-03:00", 2, 7),
        entry("blank", "2026-09-28T12:00:00-03:00", 2, null)
      ]
    });

    expect(result.price).toBe(7);
    expect(result.priceEntryItemId).toBe("priced");
  });

  it("ignora bonificação R$ 0 como substituta do preço de referência", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-25T12:00:00-03:00", 10)],
      entries: [
        entry("priced", "2026-09-20T12:00:00-03:00", 2, 7),
        entry("bonus", "2026-09-28T12:00:00-03:00", 2, 0)
      ]
    });

    expect(result.price).toBe(7);
    expect(result.priceEntryItemId).toBe("priced");
  });

  it("não usa preço futuro retroativamente", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-08-31",
      product: emptyProduct,
      conferences: [conference("c1", "2026-08-30T12:00:00-03:00", 10)],
      entries: [entry("future", "2026-09-01T09:00:00-03:00", 2, 9)]
    });

    expect(result.price).toBeNull();
    expect(result.value).toBeNull();
    expect(result.hasMissingPrice).toBe(true);
  });

  it("usa preço inicial somente quando sua referência já existia na data", () => {
    const beforeInitialPrice = calculateProductStockSnapshot({
      referenceDate: "2026-08-31",
      product: {
        ...emptyProduct,
        initialPrice: 5,
        initialPriceAt: "2026-09-01T09:00:00-03:00"
      },
      conferences: [conference("c1", "2026-08-30T12:00:00-03:00", 10)],
      entries: []
    });

    const afterInitialPrice = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: {
        ...emptyProduct,
        initialPrice: 5,
        initialPriceAt: "2026-09-01T09:00:00-03:00"
      },
      conferences: [conference("c1", "2026-09-30T12:00:00-03:00", 10)],
      entries: []
    });

    expect(beforeInitialPrice.price).toBeNull();
    expect(afterInitialPrice.price).toBe(5);
    expect(afterInitialPrice.priceSource).toBe("initial");
  });

  it("atribui R$ 0 a quantidade zero mesmo sem preço conhecido", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-30T12:00:00-03:00", 0)],
      entries: []
    });

    expect(result.quantity).toBe(0);
    expect(result.price).toBeNull();
    expect(result.value).toBe(0);
    expect(result.hasMissingPrice).toBe(false);
  });

  it("sinaliza quantidade positiva sem preço válido em vez de inventar valor", () => {
    const result = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-30T12:00:00-03:00", 10)],
      entries: []
    });

    expect(result.quantity).toBe(10);
    expect(result.price).toBeNull();
    expect(result.value).toBeNull();
    expect(result.hasMissingPrice).toBe(true);
  });

  it("recalcula diretamente a partir dos fatos corrigidos recebidos", () => {
    const original = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-25T12:00:00-03:00", 10)],
      entries: [entry("e1", "2026-09-28T12:00:00-03:00", 5, 8)]
    });
    const corrected = calculateProductStockSnapshot({
      referenceDate: "2026-09-30",
      product: emptyProduct,
      conferences: [conference("c1", "2026-09-25T12:00:00-03:00", 12)],
      entries: [entry("e1", "2026-09-28T12:00:00-03:00", 6, 9)]
    });

    expect(original).toMatchObject({ quantity: 15, price: 8, value: 120 });
    expect(corrected).toMatchObject({ quantity: 18, price: 9, value: 162 });
  });

  it("interpreta corretamente a data operacional em America/Bahia", () => {
    expect(historicalDateKey("2026-10-01T01:30:00Z")).toBe("2026-09-30");
    expect(historicalDateKey("2026-10-01T03:30:00Z")).toBe("2026-10-01");
  });
});

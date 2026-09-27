import { describe, expect, it } from "vitest";
import type {
  HistoricalProductConferenceFact,
  HistoricalProductEntryFact,
  HistoricalReportProduct,
  MonthlyStockReportFacts
} from "./monthlyStockReport";
import {
  calculateMonthlyStockValueSeries,
  firstHistoricalMonth,
  lastDayOfMonth,
  reportCurrentDateKey
} from "./monthlyStockSeries";

const product = (
  overrides: Partial<HistoricalReportProduct> = {}
): HistoricalReportProduct => ({
  id: "p1",
  name: "Produto",
  unit: "UN",
  createdAt: "2026-11-10T12:00:00-03:00",
  deletedAt: null,
  initialStockQuantity: 10,
  initialStockAt: "2026-11-10T12:00:00-03:00",
  initialPrice: 2,
  initialPriceAt: "2026-11-10T12:00:00-03:00",
  ...overrides
});

const entry = (
  overrides: Partial<HistoricalProductEntryFact> = {}
): HistoricalProductEntryFact => ({
  id: "ei1",
  productId: "p1",
  effectiveAt: "2026-11-20T12:00:00-03:00",
  createdAt: "2026-11-20T12:00:00-03:00",
  quantity: 5,
  unitPrice: 3,
  position: 1,
  ...overrides
});

const conference = (
  overrides: Partial<HistoricalProductConferenceFact> = {}
): HistoricalProductConferenceFact => ({
  id: "c1",
  productId: "p1",
  effectiveAt: "2026-11-30T12:00:00-03:00",
  createdAt: "2026-11-30T12:00:00-03:00",
  quantity: 12,
  ...overrides
});

const facts = (
  overrides: Partial<MonthlyStockReportFacts> = {}
): MonthlyStockReportFacts => ({
  products: [product()],
  entries: [entry()],
  conferences: [conference()],
  merges: [],
  lifecycle: [],
  ...overrides
});

describe("monthlyStockSeries", () => {
  it("gera meses fechados até o mês atual provisório", () => {
    const series = calculateMonthlyStockValueSeries(
      "2027-01-15",
      facts()
    );

    expect(series.startMonth).toBe("2026-11");
    expect(series.endMonth).toBe("2027-01");
    expect(series.points.map((point) => ({
      month: point.month,
      referenceDate: point.referenceDate,
      isProvisional: point.isProvisional
    }))).toEqual([
      {
        month: "2026-11",
        referenceDate: "2026-11-30",
        isProvisional: false
      },
      {
        month: "2026-12",
        referenceDate: "2026-12-31",
        isProvisional: false
      },
      {
        month: "2027-01",
        referenceDate: "2027-01-15",
        isProvisional: true
      }
    ]);
  });

  it("mantém o valor em mês sem fatos novos sem inventar consumo", () => {
    const series = calculateMonthlyStockValueSeries(
      "2027-01-15",
      facts()
    );

    expect(series.points[0]?.report.totalKnown).toBe(36);
    expect(series.points[1]?.report.totalKnown).toBe(36);
    expect(series.points[2]?.report.totalKnown).toBe(36);
  });

  it("recalcula meses afetados quando um fato histórico é corrigido", () => {
    const original = calculateMonthlyStockValueSeries(
      "2027-01-15",
      facts()
    );
    const corrected = calculateMonthlyStockValueSeries(
      "2027-01-15",
      facts({
        conferences: [conference({ quantity: 20 })]
      })
    );

    expect(original.points.map((point) => point.report.totalKnown)).toEqual([
      36,
      36,
      36
    ]);
    expect(corrected.points.map((point) => point.report.totalKnown)).toEqual([
      60,
      60,
      60
    ]);
  });

  it("propaga preço ausente em cada mês enquanto não surge preço válido", () => {
    const series = calculateMonthlyStockValueSeries(
      "2027-01-15",
      facts({
        products: [
          product({
            initialPrice: null,
            initialPriceAt: null
          })
        ],
        entries: [entry({ unitPrice: null })]
      })
    );

    expect(
      series.points.map((point) => point.report.missingPriceProductCount)
    ).toEqual([1, 1, 1]);
    expect(
      series.points.map((point) => point.report.totalKnown)
    ).toEqual([0, 0, 0]);
  });

  it("respeita exclusão e restauração entre fechamentos mensais", () => {
    const series = calculateMonthlyStockValueSeries(
      "2027-01-15",
      facts({
        lifecycle: [
          {
            id: 10,
            productId: "p1",
            action: "SOFT_DELETE",
            createdAt: "2026-12-10T12:00:00-03:00"
          },
          {
            id: 11,
            productId: "p1",
            action: "RESTORE",
            createdAt: "2027-01-05T12:00:00-03:00"
          }
        ]
      })
    );

    expect(series.points.map((point) => point.report.productCount)).toEqual([
      1,
      0,
      1
    ]);
    expect(series.points.map((point) => point.report.totalKnown)).toEqual([
      36,
      0,
      36
    ]);
  });

  it("usa fato operacional retroativo para determinar o primeiro mês disponível", () => {
    expect(
      firstHistoricalMonth(
        facts({
          products: [
            product({
              createdAt: "2026-11-10T12:00:00-03:00",
              initialStockQuantity: null,
              initialStockAt: null,
              initialPrice: null,
              initialPriceAt: null
            })
          ],
          entries: [
            entry({
              effectiveAt: "2026-09-20T12:00:00-03:00",
              createdAt: "2026-11-12T12:00:00-03:00"
            })
          ],
          conferences: []
        })
      )
    ).toBe("2026-09");
  });

  it("trata corretamente fevereiro bissexto e virada de ano", () => {
    expect(lastDayOfMonth("2028-02")).toBe("2028-02-29");
    expect(lastDayOfMonth("2027-02")).toBe("2027-02-28");

    const series = calculateMonthlyStockValueSeries(
      "2027-01-02",
      facts({
        products: [
          product({
            createdAt: "2026-12-01T12:00:00-03:00",
            initialStockAt: "2026-12-01T12:00:00-03:00",
            initialPriceAt: "2026-12-01T12:00:00-03:00"
          })
        ],
        entries: [],
        conferences: []
      })
    );

    expect(series.points.map((point) => point.month)).toEqual([
      "2026-12",
      "2027-01"
    ]);
  });

  it("deriva a data corrente usando America/Bahia", () => {
    expect(reportCurrentDateKey(new Date("2027-01-01T01:30:00Z"))).toBe(
      "2026-12-31"
    );
    expect(reportCurrentDateKey(new Date("2027-01-01T03:30:00Z"))).toBe(
      "2027-01-01"
    );
  });

  it("retorna série vazia quando ainda não existem fatos históricos", () => {
    expect(
      calculateMonthlyStockValueSeries("2026-09-27", {
        products: [],
        entries: [],
        conferences: [],
        merges: [],
        lifecycle: []
      })
    ).toEqual({
      startMonth: null,
      endMonth: null,
      points: []
    });
  });
});

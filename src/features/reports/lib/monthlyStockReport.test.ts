import { describe, expect, it } from "vitest";
import {
  calculateMonthlyStockValueReport,
  isProductIncludedAtReference,
  requiresMergeReconfirmationAtReference,
  type HistoricalProductConferenceFact,
  type HistoricalProductEntryFact,
  type HistoricalProductLifecycleFact,
  type HistoricalReportProduct,
  type MonthlyStockReportFacts
} from "./monthlyStockReport";

const product = (
  overrides: Partial<HistoricalReportProduct> = {}
): HistoricalReportProduct => ({
  id: "p1",
  name: "Produto",
  unit: "UN",
  createdAt: "2026-08-01T12:00:00-03:00",
  deletedAt: null,
  initialStockQuantity: null,
  initialStockAt: null,
  initialPrice: null,
  initialPriceAt: null,
  ...overrides
});

const entry = (
  overrides: Partial<HistoricalProductEntryFact> = {}
): HistoricalProductEntryFact => ({
  id: "ei1",
  productId: "p1",
  effectiveAt: "2026-09-28T12:00:00-03:00",
  createdAt: "2026-09-28T12:00:00-03:00",
  quantity: 5,
  unitPrice: 8,
  position: 1,
  ...overrides
});

const conference = (
  overrides: Partial<HistoricalProductConferenceFact> = {}
): HistoricalProductConferenceFact => ({
  id: "c1",
  productId: "p1",
  effectiveAt: "2026-09-25T12:00:00-03:00",
  createdAt: "2026-09-25T12:00:00-03:00",
  quantity: 10,
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

describe("monthlyStockReport", () => {
  it("inclui Produto em meses anteriores à exclusão atual", () => {
    const deleted = product({
      deletedAt: "2026-09-15T12:00:00-03:00"
    });

    expect(
      isProductIncludedAtReference(deleted, [], [], "2026-08-31")
    ).toBe(true);
    expect(
      isProductIncludedAtReference(deleted, [], [], "2026-09-30")
    ).toBe(false);
  });

  it("reconstrói corretamente o intervalo entre exclusão e restauração", () => {
    const restored = product({ deletedAt: null });
    const lifecycle: HistoricalProductLifecycleFact[] = [
      {
        id: 10,
        productId: "p1",
        action: "SOFT_DELETE",
        createdAt: "2026-09-10T12:00:00-03:00"
      },
      {
        id: 11,
        productId: "p1",
        action: "RESTORE",
        createdAt: "2026-10-05T12:00:00-03:00"
      }
    ];

    expect(
      isProductIncludedAtReference(
        restored,
        [],
        [],
        "2026-09-30",
        lifecycle
      )
    ).toBe(false);
    expect(
      isProductIncludedAtReference(
        restored,
        [],
        [],
        "2026-10-31",
        lifecycle
      )
    ).toBe(true);
  });

  it("usa o último evento quando exclusão e restauração acontecem no mesmo dia", () => {
    const restored = product({ deletedAt: null });
    const lifecycle: HistoricalProductLifecycleFact[] = [
      {
        id: 20,
        productId: "p1",
        action: "SOFT_DELETE",
        createdAt: "2026-09-27T10:00:00-03:00"
      },
      {
        id: 21,
        productId: "p1",
        action: "RESTORE",
        createdAt: "2026-09-27T10:05:00-03:00"
      }
    ];

    expect(
      isProductIncludedAtReference(
        restored,
        [],
        [],
        "2026-09-27",
        lifecycle
      )
    ).toBe(true);
  });

  it("aceita fato operacional retroativo anterior ao createdAt do cadastro", () => {
    const laterCreated = product({
      createdAt: "2026-10-10T12:00:00-03:00"
    });
    const backdatedEntry = entry({
      effectiveAt: "2026-09-20T12:00:00-03:00"
    });

    expect(
      isProductIncludedAtReference(
        laterCreated,
        [backdatedEntry],
        [],
        "2026-09-30"
      )
    ).toBe(true);
  });

  it("não inclui Produto sem presença histórica até a referência", () => {
    expect(
      isProductIncludedAtReference(
        product({ createdAt: "2026-10-01T12:00:00-03:00" }),
        [],
        [],
        "2026-09-30"
      )
    ).toBe(false);
  });

  it("exige reconfirmação depois de uma mescla sem nova Conferência física", () => {
    expect(
      requiresMergeReconfirmationAtReference(
        "p1",
        "2026-09-30",
        [
          {
            id: 10,
            productId: "p1",
            createdAt: "2026-09-27T10:00:00-03:00"
          }
        ],
        [conference()]
      )
    ).toBe(true);
  });

  it("libera reconfirmação quando existe Conferência criada depois da mescla", () => {
    expect(
      requiresMergeReconfirmationAtReference(
        "p1",
        "2026-09-30",
        [
          {
            id: 10,
            productId: "p1",
            createdAt: "2026-09-27T10:00:00-03:00"
          }
        ],
        [
          conference({
            effectiveAt: "2026-09-27T10:05:00-03:00",
            createdAt: "2026-09-27T10:05:10-03:00"
          })
        ]
      )
    ).toBe(false);
  });

  it("não deixa mescla futura invalidar um fechamento histórico anterior", () => {
    expect(
      requiresMergeReconfirmationAtReference(
        "p1",
        "2026-08-31",
        [
          {
            id: 10,
            productId: "p1",
            createdAt: "2026-09-27T10:00:00-03:00"
          }
        ],
        []
      )
    ).toBe(false);
  });

  it("agrega valor conhecido usando a regra Conferência + Entradas", () => {
    const report = calculateMonthlyStockValueReport("2026-09-30", facts());

    expect(report).toMatchObject({
      totalKnown: 120,
      productCount: 1,
      valuedProductCount: 1,
      missingPriceProductCount: 0,
      unknownStockProductCount: 0,
      hasMissingPrice: false,
      hasUnknownStock: false,
      hasExactPhysicalClose: false,
      isEstimatedFromAvailableRecords: true
    });
  });

  it("não soma Produto que ainda aguarda Conferência pós-mescla", () => {
    const report = calculateMonthlyStockValueReport(
      "2026-09-30",
      facts({
        merges: [
          {
            id: 10,
            productId: "p1",
            createdAt: "2026-09-27T10:00:00-03:00"
          }
        ]
      })
    );

    expect(report.totalKnown).toBe(0);
    expect(report.unknownStockProductCount).toBe(1);
    expect(report.hasUnknownStock).toBe(true);
  });

  it("explicita preço ausente sem inventar valor no total", () => {
    const report = calculateMonthlyStockValueReport(
      "2026-09-30",
      facts({
        entries: [entry({ unitPrice: null })]
      })
    );

    expect(report.totalKnown).toBe(0);
    expect(report.missingPriceProductCount).toBe(1);
    expect(report.hasMissingPrice).toBe(true);
  });

  it("trata quantidade zero como valor conhecido de R$ 0", () => {
    const report = calculateMonthlyStockValueReport(
      "2026-09-30",
      facts({
        entries: [],
        conferences: [
          conference({
            effectiveAt: "2026-09-30T12:00:00-03:00",
            quantity: 0
          })
        ]
      })
    );

    expect(report.totalKnown).toBe(0);
    expect(report.valuedProductCount).toBe(1);
    expect(report.missingPriceProductCount).toBe(0);
    expect(report.hasExactPhysicalClose).toBe(true);
  });

  it("sinaliza Produto sem qualquer dado de estoque", () => {
    const report = calculateMonthlyStockValueReport(
      "2026-09-30",
      facts({
        entries: [],
        conferences: []
      })
    );

    expect(report.noStockDataProductCount).toBe(1);
    expect(report.hasMissingStockData).toBe(true);
    expect(report.hasExactPhysicalClose).toBe(false);
  });

  it("soma apenas Produtos válidos na data e mantém rastreabilidade individual", () => {
    const report = calculateMonthlyStockValueReport(
      "2026-09-30",
      facts({
        products: [
          product(),
          product({
            id: "p2",
            name: "Excluído antes",
            deletedAt: "2026-09-20T12:00:00-03:00"
          })
        ],
        entries: [
          entry(),
          entry({
            id: "ei2",
            productId: "p2",
            quantity: 50,
            unitPrice: 100
          })
        ],
        conferences: [
          conference(),
          conference({
            id: "c2",
            productId: "p2",
            quantity: 100
          })
        ]
      })
    );

    expect(report.totalKnown).toBe(120);
    expect(report.productCount).toBe(1);
    expect(report.products[0]?.productId).toBe("p1");
    expect(report.products[0]?.snapshot.contributingEntryItemIds).toEqual([
      "ei1"
    ]);
  });
});

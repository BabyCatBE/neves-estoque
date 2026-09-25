import { describe, expect, it } from "vitest";
import { calculateProductUsageInsights } from "./productUsageInsights";

const referenceAt = "2026-09-25T12:00:00Z";

function conference(day: string, quantity: number) {
  return {
    effectiveAt: `${day}T12:00:00Z`,
    createdAt: `${day}T12:05:00Z`,
    quantity
  };
}

describe("calculateProductUsageInsights", () => {
  it("exige duas Conferências válidas", () => {
    const result = calculateProductUsageInsights({
      conferences: [conference("2026-09-20", 100)],
      entries: [],
      currentQuantity: 90,
      referenceAt
    });

    expect(result.status).toBe("insufficient");
    expect(result.reason).toBe("needs_two_conferences");
  });

  it("exige 28 dias e 3 intervalos válidos antes de liberar recomendação", () => {
    const result = calculateProductUsageInsights({
      conferences: [
        conference("2026-09-01", 100),
        conference("2026-09-08", 86),
        conference("2026-09-15", 72),
        conference("2026-09-22", 58)
      ],
      entries: [],
      currentQuantity: 58,
      referenceAt
    });

    expect(result.validIntervals).toBe(3);
    expect(result.historyDays).toBe(21);
    expect(result.dailyAverage).toBeCloseTo(2, 6);
    expect(result.status).toBe("insufficient");
    expect(result.reason).toBe("needs_more_history");
  });

  it("calcula média ponderada dando peso 2 às últimas 4 semanas", () => {
    const conferences = [
      conference("2026-07-03", 500),
      conference("2026-07-10", 486),
      conference("2026-07-17", 472),
      conference("2026-07-24", 458),
      conference("2026-07-31", 444),
      conference("2026-08-07", 430),
      conference("2026-08-14", 416),
      conference("2026-08-21", 402),
      conference("2026-08-28", 388),
      conference("2026-09-04", 360),
      conference("2026-09-11", 332),
      conference("2026-09-18", 304),
      conference("2026-09-25", 276)
    ];

    const result = calculateProductUsageInsights({
      conferences,
      entries: [],
      currentQuantity: 60,
      referenceAt
    });

    expect(result.status).toBe("ready");
    expect(result.validIntervals).toBe(12);
    expect(result.historyDays).toBe(84);
    expect(result.dailyAverage).toBeCloseTo(3, 6);
    expect(result.weeklyAverage).toBeCloseTo(21, 6);
    expect(result.coverageDays).toBeCloseTo(20, 6);
  });

  it("soma Entradas duplicadas no mesmo intervalo", () => {
    const result = calculateProductUsageInsights({
      conferences: [
        conference("2026-08-25", 100),
        conference("2026-09-04", 90),
        conference("2026-09-14", 80),
        conference("2026-09-24", 70)
      ],
      entries: [
        { effectiveAt: "2026-09-10T12:00:00Z", quantity: 5 },
        { effectiveAt: "2026-09-10T12:00:00Z", quantity: 7 }
      ],
      currentQuantity: 70,
      referenceAt
    });

    expect(result.status).toBe("ready");
    expect(result.entriesDuringInterval).toBe(12);
    expect(result.estimatedConsumption).toBe(42);
  });

  it("ignora intervalo negativo sem apagar os intervalos válidos", () => {
    const result = calculateProductUsageInsights({
      conferences: [
        conference("2026-08-16", 100),
        conference("2026-08-26", 80),
        conference("2026-09-05", 100),
        conference("2026-09-15", 80),
        conference("2026-09-25", 60)
      ],
      entries: [],
      currentQuantity: 60,
      referenceAt
    });

    expect(result.ignoredNegativeIntervals).toBe(1);
    expect(result.validIntervals).toBe(3);
    expect(result.historyDays).toBe(30);
    expect(result.status).toBe("ready");
    expect(result.dailyAverage).toBeCloseTo(2, 6);
  });

  it("limita o histórico a 12 semanas sem fatiar intervalos antigos", () => {
    const result = calculateProductUsageInsights({
      conferences: [
        conference("2026-06-01", 200),
        conference("2026-06-20", 190),
        conference("2026-07-03", 180),
        conference("2026-08-01", 160),
        conference("2026-09-01", 140),
        conference("2026-09-25", 120)
      ],
      entries: [],
      currentQuantity: 120,
      referenceAt
    });

    expect(result.intervalStart).toBe("2026-07-03T12:00:00Z");
    expect(result.intervalEnd).toBe("2026-09-25T12:00:00Z");
  });
});

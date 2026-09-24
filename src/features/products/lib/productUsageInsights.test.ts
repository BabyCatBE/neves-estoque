import { describe, expect, it } from "vitest";
import { calculateProductUsageInsights } from "./productUsageInsights";

describe("calculateProductUsageInsights", () => {
  it("exige duas conferências válidas", () => {
    const result = calculateProductUsageInsights({
      conferences: [
        {
          effectiveAt: "2026-09-01T12:00:00Z",
          createdAt: "2026-09-01T12:10:00Z",
          quantity: 100
        }
      ],
      entries: [],
      currentQuantity: 90
    });

    expect(result.status).toBe("insufficient");
    expect(result.reason).toBe("needs_two_conferences");
  });

  it("calcula consumo, médias e cobertura pelo intervalo entre conferências", () => {
    const result = calculateProductUsageInsights({
      conferences: [
        {
          effectiveAt: "2026-09-01T12:00:00Z",
          createdAt: "2026-09-01T12:10:00Z",
          quantity: 100
        },
        {
          effectiveAt: "2026-09-08T12:00:00Z",
          createdAt: "2026-09-08T12:10:00Z",
          quantity: 70
        }
      ],
      entries: [
        { effectiveAt: "2026-09-03T12:00:00Z", quantity: 40 },
        { effectiveAt: "2026-09-08T12:00:00Z", quantity: 5 }
      ],
      currentQuantity: 70
    });

    expect(result.status).toBe("ready");
    expect(result.intervalDays).toBe(7);
    expect(result.entriesDuringInterval).toBe(45);
    expect(result.estimatedConsumption).toBe(75);
    expect(result.dailyAverage).toBeCloseTo(75 / 7, 6);
    expect(result.weeklyAverage).toBeCloseTo(75, 6);
    expect(result.coverageDays).toBeCloseTo(70 / (75 / 7), 6);
  });

  it("soma duplicidades de Entrada e usa somente o último intervalo válido", () => {
    const result = calculateProductUsageInsights({
      conferences: [
        {
          effectiveAt: "2026-08-20T00:00:00Z",
          createdAt: "2026-08-20T00:01:00Z",
          quantity: 80
        },
        {
          effectiveAt: "2026-09-01T00:00:00Z",
          createdAt: "2026-09-01T00:01:00Z",
          quantity: 50
        },
        {
          effectiveAt: "2026-09-08T00:00:00Z",
          createdAt: "2026-09-08T00:01:00Z",
          quantity: 30
        }
      ],
      entries: [
        { effectiveAt: "2026-08-25T00:00:00Z", quantity: 100 },
        { effectiveAt: "2026-09-04T00:00:00Z", quantity: 10 },
        { effectiveAt: "2026-09-04T00:00:00Z", quantity: 15 }
      ],
      currentQuantity: 30
    });

    expect(result.status).toBe("ready");
    expect(result.entriesDuringInterval).toBe(25);
    expect(result.estimatedConsumption).toBe(45);
    expect(result.weeklyAverage).toBeCloseTo(45, 6);
  });

  it("não inventa consumo quando o intervalo produz valor negativo", () => {
    const result = calculateProductUsageInsights({
      conferences: [
        {
          effectiveAt: "2026-09-01T00:00:00Z",
          createdAt: "2026-09-01T00:01:00Z",
          quantity: 10
        },
        {
          effectiveAt: "2026-09-08T00:00:00Z",
          createdAt: "2026-09-08T00:01:00Z",
          quantity: 30
        }
      ],
      entries: [],
      currentQuantity: 30
    });

    expect(result.status).toBe("insufficient");
    expect(result.reason).toBe("negative_consumption");
  });
});

import { describe, expect, it } from "vitest";
import { evaluateConferenceConsumption } from "./conferenceConsumptionReview";

describe("evaluateConferenceConsumption", () => {
  it("avisa quando o consumo fica 50% ou mais acima do esperado", () => {
    const warning = evaluateConferenceConsumption({
      productId: "p1",
      expectedDailyAverage: 2,
      intervalDays: 10,
      previousQuantity: 50,
      entriesQuantity: 4,
      candidateQuantity: 20
    });

    expect(warning?.kind).toBe("above");
    expect(warning?.expectedConsumption).toBe(20);
    expect(warning?.actualConsumption).toBe(34);
    expect(warning?.differencePercent).toBeCloseTo(70, 6);
  });

  it("avisa exatamente no limite de 50% abaixo", () => {
    const warning = evaluateConferenceConsumption({
      productId: "p1",
      expectedDailyAverage: 2,
      intervalDays: 10,
      previousQuantity: 30,
      entriesQuantity: 0,
      candidateQuantity: 20
    });

    expect(warning?.kind).toBe("below");
    expect(warning?.differencePercent).toBeCloseTo(-50, 6);
  });

  it("não avisa quando a diferença fica abaixo de 50%", () => {
    const warning = evaluateConferenceConsumption({
      productId: "p1",
      expectedDailyAverage: 2,
      intervalDays: 10,
      previousQuantity: 40,
      entriesQuantity: 0,
      candidateQuantity: 11
    });

    expect(warning).toBeNull();
  });

  it("avisa quando a contagem cria consumo negativo inconsistente", () => {
    const warning = evaluateConferenceConsumption({
      productId: "p1",
      expectedDailyAverage: 2,
      intervalDays: 10,
      previousQuantity: 20,
      entriesQuantity: 0,
      candidateQuantity: 25
    });

    expect(warning?.kind).toBe("inconsistent");
    expect(warning?.actualConsumption).toBe(-5);
  });

  it("avisa consumo positivo quando o esperado histórico é zero", () => {
    const warning = evaluateConferenceConsumption({
      productId: "p1",
      expectedDailyAverage: 0,
      intervalDays: 10,
      previousQuantity: 20,
      entriesQuantity: 0,
      candidateQuantity: 15
    });

    expect(warning?.kind).toBe("above");
    expect(warning?.differencePercent).toBeNull();
  });
});

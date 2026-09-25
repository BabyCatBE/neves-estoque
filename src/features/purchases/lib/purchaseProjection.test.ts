import { describe, expect, it } from "vitest";
import {
  calculatePurchaseProjection,
  classifyPurchaseRisk,
  daysUntilNextOrder,
  roundPurchaseSuggestion
} from "./purchaseProjection";

describe("purchaseProjection", () => {
  it("calcula estoque-alvo até o próximo pedido chegar, com margem", () => {
    const result = calculatePurchaseProjection({
      usageReady: true,
      dailyAverage: 3,
      currentQuantity: 30,
      unit: "UN",
      hasSupplier: true,
      supplierActive: true,
      purchaseFrequencyDays: 7,
      preferredOrderWeekday: null,
      deliveryDays: 2,
      safetyMarginDays: 2,
      referenceDate: new Date("2026-09-25T12:00:00")
    });

    expect(result.cycleDays).toBe(11);
    expect(result.targetStock).toBe(33);
    expect(result.suggestedQuantity).toBe(3);
    expect(result.status).toBe("recommended");
  });

  it("usa a próxima ocorrência real do dia fixo quando a frequência é semanal", () => {
    const thursday = new Date("2026-09-24T12:00:00");
    expect(daysUntilNextOrder(7, 2, thursday)).toBe(5);
  });

  it("quando hoje é o dia do pedido, considera a próxima semana", () => {
    const tuesday = new Date("2026-09-22T12:00:00");
    expect(daysUntilNextOrder(7, 2, tuesday)).toBe(7);
  });

  it("classifica protegido, vulnerável e risco de falta", () => {
    expect(classifyPurchaseRisk(10, 5, 3)).toBe("protected");
    expect(classifyPurchaseRisk(6, 5, 3)).toBe("vulnerable");
    expect(classifyPurchaseRisk(4, 5, 3)).toBe("risk");
  });

  it("arredonda embalagem inteira para cima e KG até 2 casas para cima", () => {
    expect(roundPurchaseSuggestion(12.01, "UN")).toBe(13);
    expect(roundPurchaseSuggestion(12.341, "KG")).toBe(12.35);
  });

  it("não recomenda automaticamente sem histórico mínimo", () => {
    const result = calculatePurchaseProjection({
      usageReady: false,
      dailyAverage: 3,
      currentQuantity: 10,
      unit: "UN",
      hasSupplier: true,
      supplierActive: true,
      purchaseFrequencyDays: 7,
      preferredOrderWeekday: null,
      deliveryDays: 2,
      safetyMarginDays: 2
    });

    expect(result.status).toBe("insufficient_history");
    expect(result.suggestedQuantity).toBeNull();
  });
});

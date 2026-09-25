export type PurchaseRisk =
  | "protected"
  | "vulnerable"
  | "risk"
  | "unknown";

export type PurchaseProjectionStatus =
  | "recommended"
  | "stock_sufficient"
  | "insufficient_history"
  | "missing_stock"
  | "missing_supplier"
  | "missing_supplier_config"
  | "no_consumption"
  | "other_supplier";

export type PurchaseProjection = {
  status: PurchaseProjectionStatus;
  suggestedQuantity: number | null;
  targetStock: number | null;
  cycleDays: number | null;
  daysUntilNextOrder: number | null;
  coverageDays: number | null;
  risk: PurchaseRisk;
};

export type PurchaseProjectionInput = {
  usageReady: boolean;
  dailyAverage: number | null;
  currentQuantity: number | null;
  unit: string;
  hasSupplier: boolean;
  supplierActive: boolean;
  purchaseFrequencyDays: number | null;
  preferredOrderWeekday: number | null;
  deliveryDays: number | null;
  safetyMarginDays: number | null;
  referenceDate?: Date;
};

export function daysUntilNextOrder(
  purchaseFrequencyDays: number,
  preferredOrderWeekday: number | null,
  referenceDate = new Date()
) {
  if (
    purchaseFrequencyDays === 7 &&
    preferredOrderWeekday !== null &&
    preferredOrderWeekday >= 1 &&
    preferredOrderWeekday <= 7
  ) {
    const jsDay = referenceDate.getDay();
    const currentWeekday = jsDay === 0 ? 7 : jsDay;
    const difference = (preferredOrderWeekday - currentWeekday + 7) % 7;
    return difference === 0 ? 7 : difference;
  }

  return purchaseFrequencyDays;
}

export function classifyPurchaseRisk(
  coverageDays: number | null,
  deliveryDays: number | null,
  safetyMarginDays: number | null
): PurchaseRisk {
  if (
    coverageDays === null ||
    deliveryDays === null ||
    safetyMarginDays === null
  ) {
    return "unknown";
  }

  if (coverageDays < deliveryDays) return "risk";
  if (coverageDays < deliveryDays + safetyMarginDays) return "vulnerable";
  return "protected";
}

export function roundPurchaseSuggestion(value: number, unit: string) {
  if (value <= 0) return 0;
  if (unit === "KG") return Math.ceil((value - Number.EPSILON) * 100) / 100;
  return Math.ceil(value - Number.EPSILON);
}

export function calculatePurchaseProjection({
  usageReady,
  dailyAverage,
  currentQuantity,
  unit,
  hasSupplier,
  supplierActive,
  purchaseFrequencyDays,
  preferredOrderWeekday,
  deliveryDays,
  safetyMarginDays,
  referenceDate
}: PurchaseProjectionInput): PurchaseProjection {
  if (!usageReady) {
    return emptyProjection("insufficient_history");
  }

  if (dailyAverage === null || dailyAverage <= 0) {
    return emptyProjection("no_consumption");
  }

  if (currentQuantity === null || currentQuantity < 0) {
    return emptyProjection("missing_stock");
  }

  const coverageDays = currentQuantity / dailyAverage;
  const risk = classifyPurchaseRisk(coverageDays, deliveryDays, safetyMarginDays);

  if (!hasSupplier || !supplierActive) {
    return {
      ...emptyProjection("missing_supplier"),
      coverageDays,
      risk
    };
  }

  if (
    purchaseFrequencyDays === null ||
    deliveryDays === null ||
    safetyMarginDays === null
  ) {
    return {
      ...emptyProjection("missing_supplier_config"),
      coverageDays,
      risk
    };
  }

  const untilNextOrder = daysUntilNextOrder(
    purchaseFrequencyDays,
    preferredOrderWeekday,
    referenceDate
  );
  const cycleDays = untilNextOrder + deliveryDays + safetyMarginDays;
  const targetStock = dailyAverage * cycleDays;
  const rawSuggestion = Math.max(0, targetStock - currentQuantity);
  const suggestedQuantity = roundPurchaseSuggestion(rawSuggestion, unit);

  return {
    status: suggestedQuantity > 0 ? "recommended" : "stock_sufficient",
    suggestedQuantity,
    targetStock,
    cycleDays,
    daysUntilNextOrder: untilNextOrder,
    coverageDays,
    risk
  };
}

function emptyProjection(status: PurchaseProjectionStatus): PurchaseProjection {
  return {
    status,
    suggestedQuantity: null,
    targetStock: null,
    cycleDays: null,
    daysUntilNextOrder: null,
    coverageDays: null,
    risk: "unknown"
  };
}

export type ConferenceConsumptionWarning = {
  productId: string;
  kind: "above" | "below" | "inconsistent";
  expectedConsumption: number;
  actualConsumption: number;
  difference: number;
  differencePercent: number | null;
  intervalDays: number;
};

type EvaluateInput = {
  productId: string;
  expectedDailyAverage: number;
  intervalDays: number;
  previousQuantity: number;
  entriesQuantity: number;
  candidateQuantity: number;
};

export function evaluateConferenceConsumption({
  productId,
  expectedDailyAverage,
  intervalDays,
  previousQuantity,
  entriesQuantity,
  candidateQuantity
}: EvaluateInput): ConferenceConsumptionWarning | null {
  if (
    !Number.isFinite(expectedDailyAverage) ||
    expectedDailyAverage < 0 ||
    !Number.isFinite(intervalDays) ||
    intervalDays <= 0
  ) {
    return null;
  }

  const expectedConsumption = expectedDailyAverage * intervalDays;
  const actualConsumption = previousQuantity + entriesQuantity - candidateQuantity;
  const difference = actualConsumption - expectedConsumption;

  if (actualConsumption < 0) {
    return {
      productId,
      kind: "inconsistent",
      expectedConsumption,
      actualConsumption,
      difference,
      differencePercent:
        expectedConsumption > 0 ? (difference / expectedConsumption) * 100 : null,
      intervalDays
    };
  }

  if (expectedConsumption === 0) {
    if (actualConsumption === 0) return null;
    return {
      productId,
      kind: "above",
      expectedConsumption,
      actualConsumption,
      difference,
      differencePercent: null,
      intervalDays
    };
  }

  const differencePercent = (difference / expectedConsumption) * 100;
  if (Math.abs(differencePercent) < 50) return null;

  return {
    productId,
    kind: differencePercent > 0 ? "above" : "below",
    expectedConsumption,
    actualConsumption,
    difference,
    differencePercent,
    intervalDays
  };
}

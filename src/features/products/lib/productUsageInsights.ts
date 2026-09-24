const DAY_MS = 24 * 60 * 60 * 1000;

export type UsageConferencePoint = {
  effectiveAt: string;
  createdAt: string;
  quantity: number;
};

export type UsageEntryPoint = {
  effectiveAt: string;
  quantity: number;
};

export type ProductUsageInsights = {
  status: "ready" | "insufficient";
  reason:
    | "needs_two_conferences"
    | "invalid_interval"
    | "negative_consumption"
    | null;
  conferencesUsed: number;
  intervalStart: string | null;
  intervalEnd: string | null;
  intervalDays: number | null;
  entriesDuringInterval: number | null;
  estimatedConsumption: number | null;
  dailyAverage: number | null;
  weeklyAverage: number | null;
  coverageDays: number | null;
  coverageWeeks: number | null;
};

type CalculateUsageInput = {
  conferences: UsageConferencePoint[];
  entries: UsageEntryPoint[];
  currentQuantity: number | null;
};

function insufficient(
  reason: ProductUsageInsights["reason"],
  conferencesUsed: number
): ProductUsageInsights {
  return {
    status: "insufficient",
    reason,
    conferencesUsed,
    intervalStart: null,
    intervalEnd: null,
    intervalDays: null,
    entriesDuringInterval: null,
    estimatedConsumption: null,
    dailyAverage: null,
    weeklyAverage: null,
    coverageDays: null,
    coverageWeeks: null
  };
}

export function calculateProductUsageInsights({
  conferences,
  entries,
  currentQuantity
}: CalculateUsageInput): ProductUsageInsights {
  const validConferences = conferences
    .filter(
      (point) =>
        Number.isFinite(point.quantity) &&
        Number.isFinite(new Date(point.effectiveAt).getTime()) &&
        Number.isFinite(new Date(point.createdAt).getTime())
    )
    .sort((a, b) => {
      const effectiveDiff =
        new Date(a.effectiveAt).getTime() - new Date(b.effectiveAt).getTime();
      if (effectiveDiff !== 0) return effectiveDiff;
      return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
    });

  if (validConferences.length < 2) {
    return insufficient("needs_two_conferences", validConferences.length);
  }

  const end = validConferences.at(-1)!;
  const endTime = new Date(end.effectiveAt).getTime();
  const start = [...validConferences]
    .slice(0, -1)
    .reverse()
    .find((point) => new Date(point.effectiveAt).getTime() < endTime);

  if (!start) {
    return insufficient("invalid_interval", validConferences.length);
  }

  const startTime = new Date(start.effectiveAt).getTime();
  const intervalDays = (endTime - startTime) / DAY_MS;

  if (!Number.isFinite(intervalDays) || intervalDays <= 0) {
    return insufficient("invalid_interval", validConferences.length);
  }

  const entriesDuringInterval = entries
    .filter((entry) => {
      const entryTime = new Date(entry.effectiveAt).getTime();
      return (
        Number.isFinite(entry.quantity) &&
        Number.isFinite(entryTime) &&
        entryTime > startTime &&
        entryTime <= endTime
      );
    })
    .reduce((total, entry) => total + entry.quantity, 0);

  const estimatedConsumption =
    start.quantity + entriesDuringInterval - end.quantity;

  if (estimatedConsumption < 0) {
    return insufficient("negative_consumption", validConferences.length);
  }

  const dailyAverage = estimatedConsumption / intervalDays;
  const weeklyAverage = dailyAverage * 7;
  const coverageDays =
    dailyAverage > 0 && currentQuantity !== null && currentQuantity >= 0
      ? currentQuantity / dailyAverage
      : null;

  return {
    status: "ready",
    reason: null,
    conferencesUsed: 2,
    intervalStart: start.effectiveAt,
    intervalEnd: end.effectiveAt,
    intervalDays,
    entriesDuringInterval,
    estimatedConsumption,
    dailyAverage,
    weeklyAverage,
    coverageDays,
    coverageWeeks: coverageDays === null ? null : coverageDays / 7
  };
}

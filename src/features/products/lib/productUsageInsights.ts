const DAY_MS = 24 * 60 * 60 * 1000;
const HISTORY_WINDOW_DAYS = 84;
const RECENT_WINDOW_DAYS = 28;
const MIN_HISTORY_DAYS = 28;
const MIN_VALID_INTERVALS = 3;

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
    | "needs_more_history"
    | null;
  conferencesUsed: number;
  validIntervals: number;
  ignoredNegativeIntervals: number;
  historyDays: number | null;
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
  referenceAt?: string;
};

type UsageInterval = {
  start: UsageConferencePoint;
  end: UsageConferencePoint;
  days: number;
  entries: number;
  consumption: number;
  weight: number;
};

function parseTime(value: string) {
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : null;
}

function emptyInsights(
  reason: ProductUsageInsights["reason"],
  conferencesUsed: number,
  ignoredNegativeIntervals = 0
): ProductUsageInsights {
  return {
    status: "insufficient",
    reason,
    conferencesUsed,
    validIntervals: 0,
    ignoredNegativeIntervals,
    historyDays: null,
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
  currentQuantity,
  referenceAt
}: CalculateUsageInput): ProductUsageInsights {
  const requestedReferenceTime = referenceAt ? parseTime(referenceAt) : Date.now();
  const referenceTime = requestedReferenceTime ?? Date.now();
  const cutoffTime = referenceTime - HISTORY_WINDOW_DAYS * DAY_MS;
  const recentCutoffTime = referenceTime - RECENT_WINDOW_DAYS * DAY_MS;

  const validConferences = conferences
    .filter((point) => {
      const effectiveTime = parseTime(point.effectiveAt);
      const createdTime = parseTime(point.createdAt);
      return (
        Number.isFinite(point.quantity) &&
        effectiveTime !== null &&
        createdTime !== null &&
        effectiveTime <= referenceTime
      );
    })
    .sort((a, b) => {
      const effectiveDiff = parseTime(a.effectiveAt)! - parseTime(b.effectiveAt)!;
      if (effectiveDiff !== 0) return effectiveDiff;
      return parseTime(a.createdAt)! - parseTime(b.createdAt)!;
    });

  if (validConferences.length < 2) {
    return emptyInsights("needs_two_conferences", validConferences.length);
  }

  const validEntries = entries
    .map((entry) => ({
      ...entry,
      time: parseTime(entry.effectiveAt)
    }))
    .filter(
      (entry): entry is UsageEntryPoint & { time: number } =>
        Number.isFinite(entry.quantity) &&
        entry.time !== null &&
        entry.time <= referenceTime
    );

  const intervals: UsageInterval[] = [];
  let ignoredNegativeIntervals = 0;
  let positiveDurationIntervals = 0;

  for (let index = 0; index < validConferences.length - 1; index += 1) {
    const start = validConferences[index]!;
    const end = validConferences[index + 1]!;
    const startTime = parseTime(start.effectiveAt)!;
    const endTime = parseTime(end.effectiveAt)!;

    if (startTime < cutoffTime || endTime > referenceTime || endTime <= startTime) {
      continue;
    }

    positiveDurationIntervals += 1;
    const days = (endTime - startTime) / DAY_MS;
    const intervalEntries = validEntries
      .filter((entry) => entry.time > startTime && entry.time <= endTime)
      .reduce((total, entry) => total + entry.quantity, 0);
    const consumption = start.quantity + intervalEntries - end.quantity;

    if (consumption < 0) {
      ignoredNegativeIntervals += 1;
      continue;
    }

    intervals.push({
      start,
      end,
      days,
      entries: intervalEntries,
      consumption,
      weight: endTime >= recentCutoffTime ? 2 : 1
    });
  }

  if (!intervals.length) {
    return emptyInsights(
      ignoredNegativeIntervals > 0 ? "negative_consumption" : "invalid_interval",
      validConferences.length,
      ignoredNegativeIntervals
    );
  }

  const historyDays = intervals.reduce((total, interval) => total + interval.days, 0);
  const weightedConsumption = intervals.reduce(
    (total, interval) => total + interval.consumption * interval.weight,
    0
  );
  const weightedDays = intervals.reduce(
    (total, interval) => total + interval.days * interval.weight,
    0
  );
  const dailyAverage = weightedDays > 0 ? weightedConsumption / weightedDays : 0;
  const weeklyAverage = dailyAverage * 7;
  const coverageDays =
    dailyAverage > 0 && currentQuantity !== null && currentQuantity >= 0
      ? currentQuantity / dailyAverage
      : null;
  const ready =
    intervals.length >= MIN_VALID_INTERVALS &&
    historyDays >= MIN_HISTORY_DAYS;

  const firstInterval = intervals[0]!;
  const lastInterval = intervals.at(-1)!;

  return {
    status: ready ? "ready" : "insufficient",
    reason: ready ? null : "needs_more_history",
    conferencesUsed: intervals.length + 1,
    validIntervals: intervals.length,
    ignoredNegativeIntervals,
    historyDays,
    intervalStart: firstInterval.start.effectiveAt,
    intervalEnd: lastInterval.end.effectiveAt,
    intervalDays: historyDays,
    entriesDuringInterval: intervals.reduce((total, interval) => total + interval.entries, 0),
    estimatedConsumption: intervals.reduce(
      (total, interval) => total + interval.consumption,
      0
    ),
    dailyAverage,
    weeklyAverage,
    coverageDays,
    coverageWeeks: coverageDays === null ? null : coverageDays / 7
  };
}

export const PRODUCT_USAGE_RULES = {
  historyWindowDays: HISTORY_WINDOW_DAYS,
  recentWindowDays: RECENT_WINDOW_DAYS,
  minHistoryDays: MIN_HISTORY_DAYS,
  minValidIntervals: MIN_VALID_INTERVALS
} as const;

import {
  historicalDateKey
} from "./monthlyStockSnapshot";
import {
  calculateMonthlyStockValueReport,
  type MonthlyStockReportFacts,
  type MonthlyStockValueReport
} from "./monthlyStockReport";

export type MonthlyStockSeriesPoint = {
  month: string;
  referenceDate: string;
  isProvisional: boolean;
  report: MonthlyStockValueReport;
};

export type MonthlyStockValueSeries = {
  startMonth: string | null;
  endMonth: string | null;
  points: MonthlyStockSeriesPoint[];
};

function assertDateKey(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) throw new Error("Data atual inválida.");

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const parsed = new Date(Date.UTC(year, month - 1, day));

  if (
    parsed.getUTCFullYear() !== year ||
    parsed.getUTCMonth() !== month - 1 ||
    parsed.getUTCDate() !== day
  ) {
    throw new Error("Data atual inválida.");
  }
}

function parseMonth(value: string) {
  const match = /^(\d{4})-(\d{2})$/.exec(value);
  if (!match) throw new Error("Mês inválido.");

  const year = Number(match[1]);
  const month = Number(match[2]);
  if (month < 1 || month > 12) throw new Error("Mês inválido.");

  return { year, month };
}

function formatMonth(year: number, month: number) {
  return `${year}-${String(month).padStart(2, "0")}`;
}

function monthFromDateKey(dateKey: string) {
  return dateKey.slice(0, 7);
}

function nextMonth(monthKey: string) {
  const { year, month } = parseMonth(monthKey);
  return month === 12
    ? formatMonth(year + 1, 1)
    : formatMonth(year, month + 1);
}

export function lastDayOfMonth(monthKey: string) {
  const { year, month } = parseMonth(monthKey);
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return `${monthKey}-${String(lastDay).padStart(2, "0")}`;
}

function historicalPresenceDates(facts: MonthlyStockReportFacts) {
  return [
    ...facts.products.flatMap((product) => [
      product.createdAt,
      product.initialStockAt,
      product.initialPriceAt
    ]),
    ...facts.entries.map((entry) => entry.effectiveAt),
    ...facts.conferences.map((conference) => conference.effectiveAt)
  ].filter((value): value is string => Boolean(value));
}

export function firstHistoricalMonth(facts: MonthlyStockReportFacts) {
  const dates = historicalPresenceDates(facts);
  if (!dates.length) return null;

  const firstDate = dates
    .map((value) => historicalDateKey(value))
    .sort()[0];

  return firstDate ? firstDate.slice(0, 7) : null;
}

export function reportCurrentDateKey(now = new Date()) {
  return historicalDateKey(now.toISOString());
}

export function calculateMonthlyStockValueSeries(
  currentDate: string,
  facts: MonthlyStockReportFacts
): MonthlyStockValueSeries {
  assertDateKey(currentDate);

  const startMonth = firstHistoricalMonth(facts);
  const endMonth = monthFromDateKey(currentDate);

  if (!startMonth || startMonth > endMonth) {
    return {
      startMonth: null,
      endMonth: null,
      points: []
    };
  }

  const points: MonthlyStockSeriesPoint[] = [];

  for (
    let month = startMonth;
    month <= endMonth;
    month = nextMonth(month)
  ) {
    const isProvisional = month === endMonth;
    const referenceDate = isProvisional
      ? currentDate
      : lastDayOfMonth(month);

    points.push({
      month,
      referenceDate,
      isProvisional,
      report: calculateMonthlyStockValueReport(referenceDate, facts)
    });
  }

  return {
    startMonth,
    endMonth,
    points
  };
}

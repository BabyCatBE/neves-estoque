export const REPORT_TIME_ZONE = "America/Bahia";

export type HistoricalConferenceFact = {
  id: string;
  effectiveAt: string;
  createdAt: string;
  quantity: number;
};

export type HistoricalEntryFact = {
  id: string;
  effectiveAt: string;
  createdAt: string;
  quantity: number;
  unitPrice: number | null;
  position: number;
};

export type ProductHistoricalBasis = {
  initialStockQuantity: number | null;
  initialStockAt: string | null;
  initialPrice: number | null;
  initialPriceAt: string | null;
};

export type HistoricalQuantitySource =
  | "conference"
  | "conference_plus_entries"
  | "initial_stock"
  | "initial_stock_plus_entries"
  | "entries_only"
  | "none";

export type HistoricalPriceSource = "entry" | "initial" | "none";

export type ProductStockSnapshot = {
  referenceDate: string;
  quantity: number | null;
  quantitySource: HistoricalQuantitySource;
  checkpointConferenceId: string | null;
  checkpointAt: string | null;
  checkpointQuantity: number | null;
  entriesAfterCheckpointQuantity: number;
  contributingEntryItemIds: string[];
  price: number | null;
  priceSource: HistoricalPriceSource;
  priceAt: string | null;
  priceEntryItemId: string | null;
  value: number | null;
  hasStockData: boolean;
  hasMissingPrice: boolean;
  hasExactPhysicalClose: boolean;
};

type CalculateProductStockSnapshotInput = {
  referenceDate: string;
  product: ProductHistoricalBasis;
  conferences: HistoricalConferenceFact[];
  entries: HistoricalEntryFact[];
};

const dateFormatter = new Intl.DateTimeFormat("en-US", {
  timeZone: REPORT_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit"
});

function assertReferenceDate(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) throw new Error("Data de referência inválida.");

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));

  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    throw new Error("Data de referência inválida.");
  }
}

function timestamp(value: string) {
  const parsed = new Date(value).getTime();
  if (!Number.isFinite(parsed)) throw new Error("Data histórica inválida.");
  return parsed;
}

export function historicalDateKey(value: string) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) throw new Error("Data histórica inválida.");

  const parts = dateFormatter.formatToParts(date);
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;

  if (!year || !month || !day) throw new Error("Não foi possível interpretar a data histórica.");
  return `${year}-${month}-${day}`;
}

function isVisibleAtReference(effectiveAt: string, referenceDate: string) {
  return historicalDateKey(effectiveAt) <= referenceDate;
}

function compareTextDesc(a: string, b: string) {
  if (a === b) return 0;
  return a > b ? -1 : 1;
}

function newestConference(
  conferences: HistoricalConferenceFact[],
  referenceDate: string
) {
  return [...conferences]
    .filter((conference) => isVisibleAtReference(conference.effectiveAt, referenceDate))
    .sort(
      (a, b) =>
        timestamp(b.effectiveAt) - timestamp(a.effectiveAt) ||
        timestamp(b.createdAt) - timestamp(a.createdAt) ||
        compareTextDesc(a.id, b.id)
    )[0] ?? null;
}

function newestValidEntryPrice(
  entries: HistoricalEntryFact[],
  referenceDate: string
) {
  return [...entries]
    .filter(
      (entry) =>
        entry.unitPrice !== null &&
        entry.unitPrice > 0 &&
        isVisibleAtReference(entry.effectiveAt, referenceDate)
    )
    .sort(
      (a, b) =>
        timestamp(b.effectiveAt) - timestamp(a.effectiveAt) ||
        timestamp(b.createdAt) - timestamp(a.createdAt) ||
        b.position - a.position ||
        compareTextDesc(a.id, b.id)
    )[0] ?? null;
}

function roundMoney(value: number) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

export function calculateProductStockSnapshot({
  referenceDate,
  product,
  conferences,
  entries
}: CalculateProductStockSnapshotInput): ProductStockSnapshot {
  assertReferenceDate(referenceDate);

  const eligibleEntries = entries.filter((entry) =>
    isVisibleAtReference(entry.effectiveAt, referenceDate)
  );
  const lastConference = newestConference(conferences, referenceDate);

  const initialStockIsAvailable =
    product.initialStockQuantity !== null &&
    product.initialStockAt !== null &&
    isVisibleAtReference(product.initialStockAt, referenceDate);

  const checkpointAt = lastConference?.effectiveAt ??
    (initialStockIsAvailable ? product.initialStockAt : null);
  const checkpointQuantity = lastConference?.quantity ??
    (initialStockIsAvailable ? product.initialStockQuantity : null);

  const entriesAfterCheckpoint = eligibleEntries.filter((entry) =>
    checkpointAt === null ? true : timestamp(entry.effectiveAt) > timestamp(checkpointAt)
  );
  const entriesAfterCheckpointQuantity = entriesAfterCheckpoint.reduce(
    (total, entry) => total + entry.quantity,
    0
  );

  let quantity: number | null;
  let quantitySource: HistoricalQuantitySource;

  if (lastConference) {
    quantity = lastConference.quantity + entriesAfterCheckpointQuantity;
    quantitySource = entriesAfterCheckpoint.length
      ? "conference_plus_entries"
      : "conference";
  } else if (initialStockIsAvailable && product.initialStockQuantity !== null) {
    quantity = product.initialStockQuantity + entriesAfterCheckpointQuantity;
    quantitySource = entriesAfterCheckpoint.length
      ? "initial_stock_plus_entries"
      : "initial_stock";
  } else if (entriesAfterCheckpoint.length) {
    quantity = entriesAfterCheckpointQuantity;
    quantitySource = "entries_only";
  } else {
    quantity = null;
    quantitySource = "none";
  }

  const lastEntryPrice = newestValidEntryPrice(entries, referenceDate);
  const initialPriceIsAvailable =
    product.initialPrice !== null &&
    product.initialPriceAt !== null &&
    isVisibleAtReference(product.initialPriceAt, referenceDate);

  const price = lastEntryPrice?.unitPrice ??
    (initialPriceIsAvailable ? product.initialPrice : null);
  const priceSource: HistoricalPriceSource = lastEntryPrice
    ? "entry"
    : initialPriceIsAvailable
      ? "initial"
      : "none";
  const priceAt = lastEntryPrice?.effectiveAt ??
    (initialPriceIsAvailable ? product.initialPriceAt : null);

  const hasStockData = quantity !== null;
  const hasMissingPrice = quantity !== null && quantity > 0 && price === null;
  const value =
    quantity === null
      ? null
      : quantity === 0
        ? 0
        : price === null
          ? null
          : roundMoney(quantity * price);

  return {
    referenceDate,
    quantity,
    quantitySource,
    checkpointConferenceId: lastConference?.id ?? null,
    checkpointAt,
    checkpointQuantity,
    entriesAfterCheckpointQuantity,
    contributingEntryItemIds: entriesAfterCheckpoint.map((entry) => entry.id),
    price,
    priceSource,
    priceAt,
    priceEntryItemId: lastEntryPrice?.id ?? null,
    value,
    hasStockData,
    hasMissingPrice,
    hasExactPhysicalClose:
      lastConference !== null &&
      historicalDateKey(lastConference.effectiveAt) === referenceDate
  };
}

import {
  calculateProductStockSnapshot,
  historicalDateKey,
  type HistoricalConferenceFact,
  type HistoricalEntryFact,
  type ProductHistoricalBasis,
  type ProductStockSnapshot
} from "./monthlyStockSnapshot";

export type HistoricalReportProduct = ProductHistoricalBasis & {
  id: string;
  name: string;
  unit: string;
  createdAt: string;
  deletedAt: string | null;
};

export type HistoricalProductConferenceFact = HistoricalConferenceFact & {
  productId: string;
};

export type HistoricalProductEntryFact = HistoricalEntryFact & {
  productId: string;
};

export type HistoricalProductMergeFact = {
  id: number;
  productId: string;
  createdAt: string;
};

export type HistoricalProductLifecycleFact = {
  id: number;
  productId: string;
  action: "SOFT_DELETE" | "RESTORE";
  createdAt: string;
};

export type MonthlyStockReportFacts = {
  products: HistoricalReportProduct[];
  conferences: HistoricalProductConferenceFact[];
  entries: HistoricalProductEntryFact[];
  merges: HistoricalProductMergeFact[];
  lifecycle: HistoricalProductLifecycleFact[];
};

export type MonthlyStockProductItem = {
  productId: string;
  productName: string;
  unit: string;
  stockRequiresConference: boolean;
  snapshot: ProductStockSnapshot;
};

export type MonthlyStockValueReport = {
  referenceDate: string;
  totalKnown: number;
  productCount: number;
  valuedProductCount: number;
  missingPriceProductCount: number;
  unknownStockProductCount: number;
  noStockDataProductCount: number;
  hasMissingPrice: boolean;
  hasUnknownStock: boolean;
  hasMissingStockData: boolean;
  hasExactPhysicalClose: boolean;
  isEstimatedFromAvailableRecords: boolean;
  products: MonthlyStockProductItem[];
};

function timestamp(value: string) {
  const parsed = new Date(value).getTime();
  if (!Number.isFinite(parsed)) throw new Error("Data histórica inválida.");
  return parsed;
}

function hasReachedReference(value: string, referenceDate: string) {
  return historicalDateKey(value) <= referenceDate;
}

function productHasHistoricalPresence(
  product: HistoricalReportProduct,
  entries: HistoricalProductEntryFact[],
  conferences: HistoricalProductConferenceFact[],
  referenceDate: string
) {
  const candidateDates = [
    product.createdAt,
    product.initialStockAt,
    product.initialPriceAt,
    ...entries.map((entry) => entry.effectiveAt),
    ...conferences.map((conference) => conference.effectiveAt)
  ].filter((value): value is string => Boolean(value));

  return candidateDates.some((value) => hasReachedReference(value, referenceDate));
}

export function isProductIncludedAtReference(
  product: HistoricalReportProduct,
  entries: HistoricalProductEntryFact[],
  conferences: HistoricalProductConferenceFact[],
  referenceDate: string,
  lifecycle: HistoricalProductLifecycleFact[] = []
) {
  if (!productHasHistoricalPresence(product, entries, conferences, referenceDate)) {
    return false;
  }

  const latestLifecycle = lifecycle
    .filter(
      (event) =>
        event.productId === product.id &&
        hasReachedReference(event.createdAt, referenceDate)
    )
    .sort(
      (a, b) =>
        timestamp(b.createdAt) - timestamp(a.createdAt) || b.id - a.id
    )[0];

  if (latestLifecycle) {
    return latestLifecycle.action === "RESTORE";
  }

  if (product.deletedAt && hasReachedReference(product.deletedAt, referenceDate)) {
    return false;
  }

  return true;
}

export function requiresMergeReconfirmationAtReference(
  productId: string,
  referenceDate: string,
  merges: HistoricalProductMergeFact[],
  conferences: HistoricalProductConferenceFact[]
) {
  const latestMerge = merges
    .filter(
      (merge) =>
        merge.productId === productId &&
        hasReachedReference(merge.createdAt, referenceDate)
    )
    .sort(
      (a, b) =>
        timestamp(b.createdAt) - timestamp(a.createdAt) || b.id - a.id
    )[0];

  if (!latestMerge) return false;

  const mergeAt = timestamp(latestMerge.createdAt);
  const hasQualifyingConference = conferences.some(
    (conference) =>
      conference.productId === productId &&
      timestamp(conference.createdAt) >= mergeAt &&
      hasReachedReference(conference.effectiveAt, referenceDate)
  );

  return !hasQualifyingConference;
}

function roundMoney(value: number) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

export function calculateMonthlyStockValueReport(
  referenceDate: string,
  facts: MonthlyStockReportFacts
): MonthlyStockValueReport {
  const entriesByProduct = new Map<string, HistoricalProductEntryFact[]>();
  for (const entry of facts.entries) {
    const current = entriesByProduct.get(entry.productId) ?? [];
    current.push(entry);
    entriesByProduct.set(entry.productId, current);
  }

  const conferencesByProduct = new Map<
    string,
    HistoricalProductConferenceFact[]
  >();
  for (const conference of facts.conferences) {
    const current = conferencesByProduct.get(conference.productId) ?? [];
    current.push(conference);
    conferencesByProduct.set(conference.productId, current);
  }

  const products: MonthlyStockProductItem[] = [];

  for (const product of facts.products) {
    const productEntries = entriesByProduct.get(product.id) ?? [];
    const productConferences = conferencesByProduct.get(product.id) ?? [];

    if (
      !isProductIncludedAtReference(
        product,
        productEntries,
        productConferences,
        referenceDate,
        facts.lifecycle
      )
    ) {
      continue;
    }

    const snapshot = calculateProductStockSnapshot({
      referenceDate,
      product,
      conferences: productConferences,
      entries: productEntries
    });

    const stockRequiresConference = requiresMergeReconfirmationAtReference(
      product.id,
      referenceDate,
      facts.merges,
      productConferences
    );

    products.push({
      productId: product.id,
      productName: product.name,
      unit: product.unit,
      stockRequiresConference,
      snapshot
    });
  }

  let totalKnown = 0;
  let valuedProductCount = 0;
  let missingPriceProductCount = 0;
  let unknownStockProductCount = 0;
  let noStockDataProductCount = 0;

  for (const item of products) {
    if (item.stockRequiresConference) {
      unknownStockProductCount += 1;
      continue;
    }

    if (!item.snapshot.hasStockData) {
      noStockDataProductCount += 1;
      continue;
    }

    if (item.snapshot.hasMissingPrice) {
      missingPriceProductCount += 1;
      continue;
    }

    if (item.snapshot.value !== null) {
      totalKnown += item.snapshot.value;
      valuedProductCount += 1;
    }
  }

  const hasMissingPrice = missingPriceProductCount > 0;
  const hasUnknownStock = unknownStockProductCount > 0;
  const hasMissingStockData = noStockDataProductCount > 0;
  const hasExactPhysicalClose =
    products.length > 0 &&
    products.every(
      (item) =>
        !item.stockRequiresConference &&
        item.snapshot.hasStockData &&
        item.snapshot.hasExactPhysicalClose
    );

  return {
    referenceDate,
    totalKnown: roundMoney(totalKnown),
    productCount: products.length,
    valuedProductCount,
    missingPriceProductCount,
    unknownStockProductCount,
    noStockDataProductCount,
    hasMissingPrice,
    hasUnknownStock,
    hasMissingStockData,
    hasExactPhysicalClose,
    isEstimatedFromAvailableRecords: !hasExactPhysicalClose,
    products
  };
}

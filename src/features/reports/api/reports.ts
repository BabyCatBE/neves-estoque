import { supabase } from "../../../shared/lib/supabase";
import {
  calculateMonthlyStockValueReport,
  type HistoricalProductConferenceFact,
  type HistoricalProductEntryFact,
  type HistoricalProductLifecycleFact,
  type HistoricalProductMergeFact,
  type HistoricalReportProduct,
  type MonthlyStockReportFacts,
  type MonthlyStockValueReport
} from "../lib/monthlyStockReport";
import {
  calculateMonthlyStockValueSeries,
  reportCurrentDateKey,
  type MonthlyStockValueSeries
} from "../lib/monthlyStockSeries";

const PAGE_SIZE = 500;

type PageResult<T> = {
  data: T[] | null;
  error: unknown;
};

type ProductRow = {
  id: string;
  name: string;
  unit: string;
  created_at: string;
  deleted_at: string | null;
  initial_stock_quantity: number | null;
  initial_stock_at: string | null;
  initial_price: number | null;
  initial_price_at: string | null;
};

type EntryRow = {
  id: string;
  effective_at: string;
  created_at: string;
};

type EntryItemRow = {
  id: string;
  entry_id: string;
  product_id: string;
  quantity: number;
  unit_price: number | null;
  position: number;
};

type ConferenceRow = {
  id: string;
  effective_at: string;
  created_at: string;
};

type ConferenceItemRow = {
  conference_id: string;
  product_id: string;
  quantity: number;
};

type ProductAuditRow = {
  id: number;
  entity_id: string | null;
  action: string;
  created_at: string;
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

async function collectPages<T>(
  fetchPage: (from: number, to: number) => Promise<PageResult<T>>
) {
  const rows: T[] = [];

  for (let from = 0; ; from += PAGE_SIZE) {
    const result = await fetchPage(from, from + PAGE_SIZE - 1);
    if (result.error) throw result.error;

    const page = result.data ?? [];
    rows.push(...page);

    if (page.length < PAGE_SIZE) break;
  }

  return rows;
}

export async function loadMonthlyStockReportFacts(): Promise<MonthlyStockReportFacts> {
  const client = requireClient();

  const [
    products,
    entries,
    entryItems,
    conferences,
    conferenceItems,
    productAudits
  ] = await Promise.all([
    collectPages<ProductRow>(async (from, to) =>
      await client
        .from("products")
        .select(
          "id,name,unit,created_at,deleted_at,initial_stock_quantity,initial_stock_at,initial_price,initial_price_at"
        )
        .order("created_at", { ascending: true })
        .order("id", { ascending: true })
        .range(from, to)
    ),
    collectPages<EntryRow>(async (from, to) =>
      await client
        .from("entries")
        .select("id,effective_at,created_at")
        .is("deleted_at", null)
        .order("id", { ascending: true })
        .range(from, to)
    ),
    collectPages<EntryItemRow>(async (from, to) =>
      await client
        .from("entry_items")
        .select("id,entry_id,product_id,quantity,unit_price,position")
        .order("id", { ascending: true })
        .range(from, to)
    ),
    collectPages<ConferenceRow>(async (from, to) =>
      await client
        .from("conferences")
        .select("id,effective_at,created_at")
        .is("deleted_at", null)
        .order("id", { ascending: true })
        .range(from, to)
    ),
    collectPages<ConferenceItemRow>(async (from, to) =>
      await client
        .from("conference_items")
        .select("conference_id,product_id,quantity")
        .order("conference_id", { ascending: true })
        .order("product_id", { ascending: true })
        .range(from, to)
    ),
    collectPages<ProductAuditRow>(async (from, to) =>
      await client
        .from("audit_log")
        .select("id,entity_id,action,created_at")
        .eq("entity_type", "products")
        .in("action", ["PRODUCT_MERGE", "SOFT_DELETE", "RESTORE"])
        .order("id", { ascending: true })
        .range(from, to)
    )
  ]);

  const entryById = new Map(entries.map((entry) => [entry.id, entry] as const));
  const conferenceById = new Map(
    conferences.map((conference) => [conference.id, conference] as const)
  );

  const mappedProducts: HistoricalReportProduct[] = products.map((product) => ({
    id: product.id,
    name: product.name,
    unit: product.unit,
    createdAt: product.created_at,
    deletedAt: product.deleted_at,
    initialStockQuantity:
      product.initial_stock_quantity === null
        ? null
        : Number(product.initial_stock_quantity),
    initialStockAt: product.initial_stock_at,
    initialPrice:
      product.initial_price === null ? null : Number(product.initial_price),
    initialPriceAt: product.initial_price_at
  }));

  const mappedEntries: HistoricalProductEntryFact[] = entryItems.flatMap(
    (item) => {
      const entry = entryById.get(item.entry_id);
      if (!entry) return [];

      return [
        {
          id: item.id,
          productId: item.product_id,
          effectiveAt: entry.effective_at,
          createdAt: entry.created_at,
          quantity: Number(item.quantity),
          unitPrice: item.unit_price === null ? null : Number(item.unit_price),
          position: item.position
        }
      ];
    }
  );

  const mappedConferences: HistoricalProductConferenceFact[] =
    conferenceItems.flatMap((item) => {
      const conference = conferenceById.get(item.conference_id);
      if (!conference) return [];

      return [
        {
          id: conference.id,
          productId: item.product_id,
          effectiveAt: conference.effective_at,
          createdAt: conference.created_at,
          quantity: Number(item.quantity)
        }
      ];
    });

  const mappedMerges: HistoricalProductMergeFact[] = productAudits.flatMap(
    (audit) =>
      audit.entity_id && audit.action === "PRODUCT_MERGE"
        ? [
            {
              id: audit.id,
              productId: audit.entity_id,
              createdAt: audit.created_at
            }
          ]
        : []
  );

  const mappedLifecycle: HistoricalProductLifecycleFact[] =
    productAudits.flatMap((audit) => {
      if (
        !audit.entity_id ||
        (audit.action !== "SOFT_DELETE" && audit.action !== "RESTORE")
      ) {
        return [];
      }

      return [
        {
          id: audit.id,
          productId: audit.entity_id,
          action: audit.action,
          createdAt: audit.created_at
        }
      ];
    });

  return {
    products: mappedProducts,
    entries: mappedEntries,
    conferences: mappedConferences,
    merges: mappedMerges,
    lifecycle: mappedLifecycle
  };
}

export async function getMonthlyStockValueReport(
  referenceDate: string
): Promise<MonthlyStockValueReport> {
  const facts = await loadMonthlyStockReportFacts();
  return calculateMonthlyStockValueReport(referenceDate, facts);
}


export async function getMonthlyStockValueHistory(
  now = new Date()
): Promise<MonthlyStockValueSeries> {
  const facts = await loadMonthlyStockReportFacts();
  const nowTimestamp = now.getTime();

  const factsAvailableNow: MonthlyStockReportFacts = {
    ...facts,
    products: facts.products.map((product) => ({
      ...product,
      initialStockAt:
        product.initialStockAt &&
        new Date(product.initialStockAt).getTime() <= nowTimestamp
          ? product.initialStockAt
          : null,
      initialStockQuantity:
        product.initialStockAt &&
        new Date(product.initialStockAt).getTime() <= nowTimestamp
          ? product.initialStockQuantity
          : null,
      initialPriceAt:
        product.initialPriceAt &&
        new Date(product.initialPriceAt).getTime() <= nowTimestamp
          ? product.initialPriceAt
          : null,
      initialPrice:
        product.initialPriceAt &&
        new Date(product.initialPriceAt).getTime() <= nowTimestamp
          ? product.initialPrice
          : null
    })),
    entries: facts.entries.filter(
      (entry) => new Date(entry.effectiveAt).getTime() <= nowTimestamp
    ),
    conferences: facts.conferences.filter(
      (conference) => new Date(conference.effectiveAt).getTime() <= nowTimestamp
    ),
    merges: facts.merges.filter(
      (merge) => new Date(merge.createdAt).getTime() <= nowTimestamp
    ),
    lifecycle: facts.lifecycle.filter(
      (event) => new Date(event.createdAt).getTime() <= nowTimestamp
    )
  };

  return calculateMonthlyStockValueSeries(
    reportCurrentDateKey(now),
    factsAvailableNow
  );
}

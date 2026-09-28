import { supabase } from "../../../shared/lib/supabase";
import {
  fetchAllByIdKeyset,
  fetchAllByNumericIdKeyset
} from "../../../shared/lib/keysetPagination";
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
  filterMonthlyStockReportFactsAvailableAt,
  reportCurrentDateKey,
  type MonthlyStockValueSeries
} from "../lib/monthlyStockSeries";

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
  id: string;
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

function compareTimestamps(left: string, right: string) {
  const diff = Date.parse(left) - Date.parse(right);
  if (Number.isFinite(diff) && diff !== 0) return diff;
  // Mesmo milissegundo: o texto ISO do PostgREST preserva os microssegundos.
  if (left === right) return 0;
  return left < right ? -1 : 1;
}

/**
 * Ordem de exibição dos produtos no relatório (antes: `order(created_at, id)` no
 * servidor). A leitura agora é por cursor de `id`; a ordem original é
 * reconstruída aqui para não mudar a saída.
 */
function sortProductsByCreation(rows: ProductRow[]) {
  return [...rows].sort(
    (a, b) =>
      compareTimestamps(a.created_at, b.created_at) ||
      (a.id === b.id ? 0 : a.id < b.id ? -1 : 1)
  );
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
    fetchAllByIdKeyset<ProductRow>(({ afterId, limit }) => {
      let query = client
        .from("products")
        .select(
          "id,name,unit,created_at,deleted_at,initial_stock_quantity,initial_stock_at,initial_price,initial_price_at"
        );
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }),
    fetchAllByIdKeyset<EntryRow>(({ afterId, limit }) => {
      let query = client
        .from("entries")
        .select("id,effective_at,created_at")
        .is("deleted_at", null);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }),
    fetchAllByIdKeyset<EntryItemRow>(({ afterId, limit }) => {
      let query = client
        .from("entry_items")
        .select("id,entry_id,product_id,quantity,unit_price,position");
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }),
    fetchAllByIdKeyset<ConferenceRow>(({ afterId, limit }) => {
      let query = client
        .from("conferences")
        .select("id,effective_at,created_at")
        .is("deleted_at", null);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }),
    fetchAllByIdKeyset<ConferenceItemRow>(({ afterId, limit }) => {
      let query = client
        .from("conference_items")
        .select("id,conference_id,product_id,quantity");
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }),
    fetchAllByNumericIdKeyset<ProductAuditRow>(({ afterId, limit }) => {
      let query = client
        .from("audit_log")
        .select("id,entity_id,action,created_at")
        .eq("entity_type", "products")
        .in("action", ["PRODUCT_MERGE", "SOFT_DELETE", "RESTORE"]);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    })
  ]);

  const entryById = new Map(entries.map((entry) => [entry.id, entry] as const));
  const conferenceById = new Map(
    conferences.map((conference) => [conference.id, conference] as const)
  );

  const mappedProducts: HistoricalReportProduct[] = sortProductsByCreation(products).map((product) => ({
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
  const factsAvailableNow = filterMonthlyStockReportFactsAvailableAt(facts, now);

  return calculateMonthlyStockValueSeries(
    reportCurrentDateKey(now),
    factsAvailableNow
  );
}

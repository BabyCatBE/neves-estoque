import {
  fetchAllByIdKeyset,
  fetchByIdChunks
} from "../../../shared/lib/keysetPagination";
import { supabase } from "../../../shared/lib/supabase";
import {
  OFFLINE_CACHE_KEYS,
  readThroughOfflineCache
} from "../../../shared/offline/offlineCache";
import { listActiveCategories, type CategoryListItem } from "../../categories/api/categories";
import { listActiveProducts } from "../../products/api/products";
import { calculateProductUsageInsights } from "../../products/lib/productUsageInsights";
import {
  evaluateConferenceConsumption,
  type ConferenceConsumptionWarning as ConsumptionWarning
} from "../lib/conferenceConsumptionReview";
import {
  localDateInputValue,
  localDateKey,
  localDayRange
} from "../lib/conferenceValidation";

export type ConferenceProduct = {
  id: string;
  name: string;
  unit: string;
  sortOrder: number | null;
};

export type ConferenceCategorySummary = {
  id: string;
  name: string;
  sortOrder: number | null;
  productCount: number;
  lastConferenceAt: string | null;
  conferredToday: boolean;
};

export type CategoryConferenceSetup = {
  category: Pick<CategoryListItem, "id" | "name">;
  products: ConferenceProduct[];
};

export type ConferenceHistoryItem = {
  id: string;
  categoryId: string;
  effectiveAt: string;
  createdAt: string;
  physicalResponsible: string;
  observation: string | null;
};

export type ConferenceDetailItem = {
  id: string;
  productId: string;
  productName: string;
  unit: string;
  quantity: number;
  position: number;
};

export type ConferenceDetails = {
  id: string;
  categoryId: string;
  categoryName: string;
  effectiveAt: string;
  createdAt: string;
  physicalResponsible: string;
  observation: string | null;
  items: ConferenceDetailItem[];
};

export type CategoryConferenceWriteInput = {
  categoryId: string;
  effectiveAt: string;
  physicalResponsible: string;
  deviceId: string;
  idempotencyKey: string;
  observation: string | null;
  items: Array<{ productId: string; quantity: number }>;
};

export type CategoryConferenceUpdateInput = Omit<
  CategoryConferenceWriteInput,
  "categoryId" | "idempotencyKey"
> & { conferenceId: string };

export type ProductConferenceWriteInput = {
  productId: string;
  effectiveAt: string;
  physicalResponsible: string;
  deviceId: string;
  idempotencyKey: string;
  quantity: number;
  observation: string | null;
};

export type ConferencePrintCategory = CategoryListItem & {
  products: ConferenceProduct[];
};

export type ConferencePrintData = {
  categories: ConferencePrintCategory[];
  pendingProductCount: number;
};

export type ConferenceConsumptionWarning = ConsumptionWarning;

export type ConferenceConsumptionReviewInput = {
  effectiveAt: string;
  items: Array<{ productId: string; quantity: number }>;
  excludeConferenceId?: string;
};

export type ConferenceTrashActionInput = {
  conferenceId: string;
  deviceId: string;
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

export async function listConferenceCategories(): Promise<ConferenceCategorySummary[]> {
  return readThroughOfflineCache(
    OFFLINE_CACHE_KEYS.conferenceCategories,
    listConferenceCategoriesFromServer
  );
}

async function listConferenceCategoriesFromServer(): Promise<ConferenceCategorySummary[]> {
  const client = requireClient();
  const [categories, conferences] = await Promise.all([
    listActiveCategories(),
    fetchAllByIdKeyset(({ afterId, limit }) => {
      let query = client
        .from("conferences")
        .select("id,category_id,effective_at,created_at")
        .eq("scope_type", "category")
        .is("deleted_at", null);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    })
  ]);

  const latestByCategory = new Map<
    string,
    { effectiveAt: string; createdAt: string }
  >();
  for (const conference of conferences) {
    if (!conference.category_id) continue;
    const current = latestByCategory.get(conference.category_id);
    const isNewer =
      !current ||
      Date.parse(conference.effective_at) > Date.parse(current.effectiveAt) ||
      (
        Date.parse(conference.effective_at) === Date.parse(current.effectiveAt) &&
        Date.parse(conference.created_at) > Date.parse(current.createdAt)
      );
    if (isNewer) {
      latestByCategory.set(conference.category_id, {
        effectiveAt: conference.effective_at,
        createdAt: conference.created_at
      });
    }
  }

  const today = localDateInputValue();
  return categories.map((category) => {
    const lastConferenceAt = latestByCategory.get(category.id)?.effectiveAt ?? null;
    return {
      id: category.id,
      name: category.name,
      sortOrder: category.sort_order,
      productCount: category.productCount,
      lastConferenceAt,
      conferredToday: lastConferenceAt ? localDateKey(lastConferenceAt) === today : false
    };
  });
}

export async function getCategoryConferenceSetup(
  categoryId: string
): Promise<CategoryConferenceSetup> {
  const [categories, products] = await Promise.all([
    listActiveCategories(),
    listActiveProducts()
  ]);

  const category = categories.find((item) => item.id === categoryId);
  if (!category) throw new Error("Categoria não encontrada.");

  return {
    category: { id: category.id, name: category.name },
    products: products
      .filter((product) => product.categoryId === categoryId)
      .sort((a, b) => {
        const orderA = a.sortOrder ?? Number.MAX_SAFE_INTEGER;
        const orderB = b.sortOrder ?? Number.MAX_SAFE_INTEGER;
        return orderA - orderB || a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" });
      })
      .map((product) => ({
        id: product.id,
        name: product.name,
        unit: product.unit,
        sortOrder: product.sortOrder
      }))
  };
}

export async function createCategoryConference(input: CategoryConferenceWriteInput) {
  const client = requireClient();
  const { data, error } = await client.rpc("create_category_conference", {
    p_category_id: input.categoryId,
    p_effective_at: input.effectiveAt,
    p_physical_responsible: input.physicalResponsible,
    p_device_id: input.deviceId,
    p_idempotency_key: input.idempotencyKey,
    p_items: input.items.map((item) => ({
      product_id: item.productId,
      quantity: item.quantity
    })),
    ...(input.observation === null ? {} : { p_observation: input.observation })
  });

  if (error) throw error;
  return data;
}

export async function updateCategoryConference(input: CategoryConferenceUpdateInput) {
  const client = requireClient();
  const { error } = await client.rpc("update_category_conference", {
    p_conference_id: input.conferenceId,
    p_effective_at: input.effectiveAt,
    p_physical_responsible: input.physicalResponsible,
    p_device_id: input.deviceId,
    p_items: input.items.map((item) => ({
      product_id: item.productId,
      quantity: item.quantity
    })),
    ...(input.observation === null ? {} : { p_observation: input.observation })
  });

  if (error) throw error;
}

export async function createProductConference(input: ProductConferenceWriteInput) {
  const client = requireClient();
  const { data, error } = await client.rpc("create_product_conference", {
    p_product_id: input.productId,
    p_effective_at: input.effectiveAt,
    p_physical_responsible: input.physicalResponsible,
    p_device_id: input.deviceId,
    p_idempotency_key: input.idempotencyKey,
    p_quantity: input.quantity,
    ...(input.observation === null ? {} : { p_observation: input.observation })
  });

  if (error) throw error;
  return data;
}

export async function reviewConferenceConsumption(
  input: ConferenceConsumptionReviewInput
): Promise<ConferenceConsumptionWarning[]> {
  const client = requireClient();
  const productIds = [...new Set(input.items.map((item) => item.productId))];
  if (!productIds.length) return [];

  // Históricos: leitura completa por cursor (ver shared/lib/keysetPagination).
  const [conferenceItems, conferences, entryItems, entries] = await Promise.all([
    fetchAllByIdKeyset(({ afterId, limit }) => {
      let query = client
        .from("conference_items")
        .select("id,conference_id,product_id,quantity")
        .in("product_id", productIds);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }),
    fetchAllByIdKeyset(({ afterId, limit }) => {
      let query = client
        .from("conferences")
        .select("id,effective_at,created_at")
        .is("deleted_at", null)
        .lt("effective_at", input.effectiveAt);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }),
    fetchAllByIdKeyset(({ afterId, limit }) => {
      let query = client
        .from("entry_items")
        .select("id,entry_id,product_id,quantity")
        .in("product_id", productIds);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    }),
    fetchAllByIdKeyset(({ afterId, limit }) => {
      let query = client
        .from("entries")
        .select("id,effective_at")
        .is("deleted_at", null)
        .lte("effective_at", input.effectiveAt);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    })
  ]);

  const conferenceById = new Map(
    conferences
      .filter((conference) => conference.id !== input.excludeConferenceId)
      .map((conference) => [conference.id, conference] as const)
  );
  const entryById = new Map(
    entries.map((entry) => [entry.id, entry] as const)
  );

  const conferencePointsByProduct = new Map<
    string,
    Array<{ effectiveAt: string; createdAt: string; quantity: number }>
  >();
  for (const item of conferenceItems) {
    const conference = conferenceById.get(item.conference_id);
    if (!conference) continue;
    const points = conferencePointsByProduct.get(item.product_id) ?? [];
    points.push({
      effectiveAt: conference.effective_at,
      createdAt: conference.created_at,
      quantity: Number(item.quantity)
    });
    conferencePointsByProduct.set(item.product_id, points);
  }

  const entriesByProduct = new Map<
    string,
    Array<{ effectiveAt: string; quantity: number }>
  >();
  for (const item of entryItems) {
    const entry = entryById.get(item.entry_id);
    if (!entry) continue;
    const points = entriesByProduct.get(item.product_id) ?? [];
    points.push({
      effectiveAt: entry.effective_at,
      quantity: Number(item.quantity)
    });
    entriesByProduct.set(item.product_id, points);
  }

  const candidateTime = new Date(input.effectiveAt).getTime();
  const warnings: ConferenceConsumptionWarning[] = [];

  for (const candidate of input.items) {
    const conferencesForProduct = [...(conferencePointsByProduct.get(candidate.productId) ?? [])]
      .sort((a, b) => {
        const effectiveDiff =
          new Date(a.effectiveAt).getTime() - new Date(b.effectiveAt).getTime();
        if (effectiveDiff !== 0) return effectiveDiff;
        return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      });

    const usage = calculateProductUsageInsights({
      conferences: conferencesForProduct,
      entries: entriesByProduct.get(candidate.productId) ?? [],
      currentQuantity: null,
      referenceAt: input.effectiveAt
    });

    if (usage.status !== "ready" || usage.dailyAverage === null) continue;

    const previous = conferencesForProduct.at(-1);
    if (!previous) continue;
    const previousTime = new Date(previous.effectiveAt).getTime();
    if (
      !Number.isFinite(candidateTime) ||
      !Number.isFinite(previousTime) ||
      candidateTime <= previousTime
    ) {
      continue;
    }

    const intervalDays = (candidateTime - previousTime) / (24 * 60 * 60 * 1000);
    const entriesQuantity = (entriesByProduct.get(candidate.productId) ?? [])
      .filter((entry) => {
        const time = new Date(entry.effectiveAt).getTime();
        return time > previousTime && time <= candidateTime;
      })
      .reduce((total, entry) => total + entry.quantity, 0);

    const warning = evaluateConferenceConsumption({
      productId: candidate.productId,
      expectedDailyAverage: usage.dailyAverage,
      intervalDays,
      previousQuantity: previous.quantity,
      entriesQuantity,
      candidateQuantity: candidate.quantity
    });

    if (warning) warnings.push(warning);
  }

  return warnings;
}

export async function listSameDayCategoryConferences(
  categoryId: string,
  dateValue: string
): Promise<ConferenceHistoryItem[]> {
  const client = requireClient();
  const { start, end } = localDayRange(dateValue);
  const rows = await fetchAllByIdKeyset(({ afterId, limit }) => {
    let query = client
      .from("conferences")
      .select("id,category_id,effective_at,created_at,physical_responsible,observation")
      .eq("scope_type", "category")
      .eq("category_id", categoryId)
      .is("deleted_at", null)
      .gte("effective_at", start)
      .lt("effective_at", end);
    if (afterId !== null) query = query.gt("id", afterId);
    return query.order("id", { ascending: true }).limit(limit);
  });

  return rows
    .filter((row) => Boolean(row.category_id))
    .sort(
      (left, right) =>
        Date.parse(right.effective_at) - Date.parse(left.effective_at) ||
        Date.parse(right.created_at) - Date.parse(left.created_at)
    )
    .map((row) => ({
      id: row.id,
      categoryId: row.category_id!,
      effectiveAt: row.effective_at,
      createdAt: row.created_at,
      physicalResponsible: row.physical_responsible,
      observation: row.observation
    }));
}

export async function listCategoryConferenceHistory(
  categoryId: string
): Promise<ConferenceHistoryItem[]> {
  const client = requireClient();
  const rows = await fetchAllByIdKeyset(({ afterId, limit }) => {
    let query = client
      .from("conferences")
      .select("id,category_id,effective_at,created_at,physical_responsible,observation")
      .eq("scope_type", "category")
      .eq("category_id", categoryId)
      .is("deleted_at", null);
    if (afterId !== null) query = query.gt("id", afterId);
    return query.order("id", { ascending: true }).limit(limit);
  });

  return rows
    .filter((row) => Boolean(row.category_id))
    .sort(
      (left, right) =>
        Date.parse(right.effective_at) - Date.parse(left.effective_at) ||
        Date.parse(right.created_at) - Date.parse(left.created_at)
    )
    .map((row) => ({
      id: row.id,
      categoryId: row.category_id!,
      effectiveAt: row.effective_at,
      createdAt: row.created_at,
      physicalResponsible: row.physical_responsible,
      observation: row.observation
    }));
}

export async function getConferenceDetails(conferenceId: string): Promise<ConferenceDetails> {
  const client = requireClient();
  const { data: conference, error: conferenceError } = await client
    .from("conferences")
    .select("id,category_id,effective_at,created_at,physical_responsible,observation,scope_type")
    .eq("id", conferenceId)
    .is("deleted_at", null)
    .maybeSingle();

  if (conferenceError) throw conferenceError;
  if (!conference || conference.scope_type !== "category" || !conference.category_id) {
    throw new Error("Conferência de categoria não encontrada.");
  }

  const [categoryResult, items] = await Promise.all([
    client
      .from("categories")
      .select("id,name")
      .eq("id", conference.category_id)
      .maybeSingle(),
    fetchAllByIdKeyset(({ afterId, limit }) => {
      let query = client
        .from("conference_items")
        .select("id,product_id,quantity,position")
        .eq("conference_id", conference.id);
      if (afterId !== null) query = query.gt("id", afterId);
      return query.order("id", { ascending: true }).limit(limit);
    })
  ]);

  if (categoryResult.error) throw categoryResult.error;

  const productIds = [...new Set(items.map((item) => item.product_id))];
  const products = await fetchByIdChunks(productIds, (ids) =>
    client.from("products").select("id,name,unit").in("id", ids)
  );
  const productById = new Map(
    products.map((product) => [product.id, product] as const)
  );

  return {
    id: conference.id,
    categoryId: conference.category_id,
    categoryName: categoryResult.data?.name ?? "Categoria não disponível",
    effectiveAt: conference.effective_at,
    createdAt: conference.created_at,
    physicalResponsible: conference.physical_responsible,
    observation: conference.observation,
    items: [...items].sort((a, b) => a.position - b.position).map((item) => ({
      id: item.id,
      productId: item.product_id,
      productName: productById.get(item.product_id)?.name ?? "Produto não disponível",
      unit: productById.get(item.product_id)?.unit ?? "—",
      quantity: Number(item.quantity),
      position: item.position
    }))
  };
}

export async function listConferencePrintData(): Promise<ConferencePrintData> {
  const [categories, products] = await Promise.all([
    listActiveCategories(),
    listActiveProducts()
  ]);

  return {
    categories: categories.map((category) => ({
      ...category,
      products: products
        .filter((product) => product.categoryId === category.id)
        .sort((a, b) => {
          const orderA = a.sortOrder ?? Number.MAX_SAFE_INTEGER;
          const orderB = b.sortOrder ?? Number.MAX_SAFE_INTEGER;
          return orderA - orderB || a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" });
        })
        .map((product) => ({
          id: product.id,
          name: product.name,
          unit: product.unit,
          sortOrder: product.sortOrder
        }))
    })),
    pendingProductCount: products.filter((product) => product.categoryId === null).length
  };
}


export async function softDeleteConference(input: ConferenceTrashActionInput) {
  const client = requireClient();
  const { error } = await client.rpc("soft_delete_conference", {
    p_conference_id: input.conferenceId,
    p_device_id: input.deviceId
  });

  if (error) throw error;
}

export async function restoreConference(input: ConferenceTrashActionInput) {
  const client = requireClient();
  const { error } = await client.rpc("restore_conference", {
    p_conference_id: input.conferenceId,
    p_device_id: input.deviceId
  });

  if (error) throw error;
}

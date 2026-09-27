import { createBrowserUuid } from "../../../shared/lib/browserUuid";
import { supabase } from "../../../shared/lib/supabase";
import type { Tables } from "../../../shared/types/database.types";

const CATEGORY_ILLUSTRATIONS_BUCKET = "category-illustrations";
const MAX_CATEGORY_ILLUSTRATION_SIZE = 5 * 1024 * 1024;

export type CategoryIllustrationSource = "library" | "upload" | null;

type CategoryDbRow = Pick<
  Tables<"categories">,
  | "id"
  | "name"
  | "sort_order"
  | "illustration_source"
  | "illustration_key"
  | "illustration_position_x"
  | "illustration_position_y"
>;

export type CategoryListItem = Omit<CategoryDbRow, "illustration_source"> & {
  illustration_source: CategoryIllustrationSource;
  illustrationUrl: string | null;
  productCount: number;
};

export type CategoryTrashItem = Pick<
  Tables<"categories">,
  "id" | "name" | "sort_order" | "deleted_at" | "restore_until"
>;

export type CategoryDetailsInput = {
  id: string;
  name: string;
  sortOrder: number;
  illustrationSource: CategoryIllustrationSource;
  illustrationKey: string | null;
  illustrationPositionX: number;
  illustrationPositionY: number;
};

function requireClient() {
  if (!supabase) throw new Error("Supabase não está configurado neste ambiente.");
  return supabase;
}

function normalizeIllustrationSource(value: string | null): CategoryIllustrationSource {
  return value === "library" || value === "upload" ? value : null;
}

export async function listActiveCategories(): Promise<CategoryListItem[]> {
  const client = requireClient();

  const [categoriesResult, productsResult] = await Promise.all([
    client
      .from("categories")
      .select(
        "id,name,sort_order,illustration_source,illustration_key,illustration_position_x,illustration_position_y"
      )
      .is("deleted_at", null)
      .order("sort_order", { ascending: true, nullsFirst: false })
      .order("name", { ascending: true }),
    client.from("products").select("category_id").is("deleted_at", null)
  ]);

  if (categoriesResult.error) throw categoriesResult.error;
  if (productsResult.error) throw productsResult.error;

  const counts = new Map<string, number>();
  for (const product of productsResult.data ?? []) {
    if (!product.category_id) continue;
    counts.set(product.category_id, (counts.get(product.category_id) ?? 0) + 1);
  }

  const categories = categoriesResult.data ?? [];
  const uploadPaths = [
    ...new Set(
      categories
        .filter(
          (category) =>
            category.illustration_source === "upload" && Boolean(category.illustration_key)
        )
        .map((category) => category.illustration_key as string)
    )
  ];

  const signedUrls = new Map<string, string>();

  if (uploadPaths.length > 0) {
    const signedResult = await client.storage
      .from(CATEGORY_ILLUSTRATIONS_BUCKET)
      .createSignedUrls(uploadPaths, 60 * 60);

    if (!signedResult.error) {
      for (const item of signedResult.data ?? []) {
        if (item.path && item.signedUrl) signedUrls.set(item.path, item.signedUrl);
      }
    }
  }

  return categories.map((category) => ({
    ...category,
    illustration_source: normalizeIllustrationSource(category.illustration_source),
    illustrationUrl:
      category.illustration_source === "upload" && category.illustration_key
        ? (signedUrls.get(category.illustration_key) ?? null)
        : null,
    productCount: counts.get(category.id) ?? 0
  }));
}

export async function listRestorableCategories(): Promise<CategoryTrashItem[]> {
  const client = requireClient();
  const now = new Date().toISOString();

  const { data, error } = await client
    .from("categories")
    .select("id,name,sort_order,deleted_at,restore_until")
    .not("deleted_at", "is", null)
    .gt("restore_until", now)
    .order("deleted_at", { ascending: false });

  if (error) throw error;
  return data ?? [];
}

export async function createCategory(input: CategoryDetailsInput) {
  const client = requireClient();
  const { error } = await client.from("categories").insert({
    id: input.id,
    name: input.name,
    sort_order: input.sortOrder,
    illustration_source: input.illustrationSource,
    illustration_key: input.illustrationKey,
    illustration_position_x: input.illustrationPositionX,
    illustration_position_y: input.illustrationPositionY
  });

  if (error) throw error;
}

export async function updateCategoryDetails(input: Omit<CategoryDetailsInput, "sortOrder">) {
  const client = requireClient();
  const { error } = await client
    .from("categories")
    .update({
      name: input.name,
      illustration_source: input.illustrationSource,
      illustration_key: input.illustrationKey,
      illustration_position_x: input.illustrationPositionX,
      illustration_position_y: input.illustrationPositionY
    })
    .eq("id", input.id)
    .is("deleted_at", null);

  if (error) throw error;
}

export async function uploadCategoryIllustration(categoryId: string, file: File) {
  const client = requireClient();

  const extensionByMime: Record<string, "jpg" | "png" | "webp"> = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp"
  };

  const extension = extensionByMime[file.type];
  if (!extension) throw new Error("Formato de imagem não permitido.");
  if (file.size > MAX_CATEGORY_ILLUSTRATION_SIZE) {
    throw new Error("A imagem pode ter no máximo 5 MB.");
  }

  const path = `${categoryId}/${createBrowserUuid()}.${extension}`;
  const { error } = await client.storage.from(CATEGORY_ILLUSTRATIONS_BUCKET).upload(path, file, {
    cacheControl: "3600",
    contentType: file.type,
    upsert: false
  });

  if (error) throw error;
  return path;
}

export async function removeCategoryIllustration(path: string) {
  const client = requireClient();
  const { error } = await client.storage.from(CATEGORY_ILLUSTRATIONS_BUCKET).remove([path]);
  if (error) throw error;
}

export async function softDeleteCategory(id: string) {
  const client = requireClient();
  const { error } = await client
    .from("categories")
    .update({ deleted_at: new Date().toISOString() })
    .eq("id", id);

  if (error) throw error;
}

export async function restoreCategory(id: string) {
  const client = requireClient();
  const { error } = await client
    .from("categories")
    .update({ deleted_at: null })
    .eq("id", id)
    .not("deleted_at", "is", null);

  if (error) throw error;
}

export async function reorderCategories(categoryIds: string[]) {
  const client = requireClient();
  const { error } = await client.rpc("reorder_categories", {
    p_category_ids: categoryIds
  });

  if (error) throw error;
}

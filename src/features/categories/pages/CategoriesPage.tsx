import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type PointerEvent as ReactPointerEvent } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import {
  createCategory,
  listActiveCategories,
  removeCategoryIllustration,
  reorderCategories,
  softDeleteCategory,
  updateCategoryDetails,
  uploadCategoryIllustration,
  type CategoryDetailsInput,
  type CategoryListItem
} from "../api/categories";
import {
  CategoryIllustrationPicker,
  emptyCategoryIllustrationDraft,
  type CategoryIllustrationDraft
} from "../components/CategoryIllustrationPicker";
import { CategoryIllustrationVisual } from "../components/CategoryIllustrationVisual";
import { categoryNameSchema, getCategoryErrorMessage } from "../lib/categoryValidation";
import { moveItemById } from "../lib/reorderCategories";

type CategoryForm = {
  name: string;
};

type PreparedIllustration = {
  source: "library" | "upload" | null;
  key: string | null;
  positionX: number;
  positionY: number;
  uploadedPath: string | null;
};

const categoriesKey = ["categories", "active"] as const;

export function CategoriesPage() {
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [createIllustration, setCreateIllustration] = useState<CategoryIllustrationDraft>(
    emptyCategoryIllustrationDraft()
  );
  const [editing, setEditing] = useState<CategoryListItem | null>(null);
  const [editingName, setEditingName] = useState("");
  const [editingIllustration, setEditingIllustration] = useState<CategoryIllustrationDraft>(
    emptyCategoryIllustrationDraft()
  );
  const [reordering, setReordering] = useState(false);
  const [draftOrder, setDraftOrder] = useState<CategoryListItem[]>([]);
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const categoriesQuery = useQuery({
    queryKey: categoriesKey,
    queryFn: listActiveCategories
  });

  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors }
  } = useForm<CategoryForm>({
    defaultValues: { name: "" }
  });

  const createName = watch("name");

  const refreshCategories = async () => {
    await queryClient.invalidateQueries({ queryKey: categoriesKey });
  };

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: refreshCategories
  });

  const updateMutation = useMutation({
    mutationFn: updateCategoryDetails,
    onSuccess: refreshCategories
  });

  const deleteMutation = useMutation({
    mutationFn: softDeleteCategory,
    onSuccess: refreshCategories
  });

  const reorderMutation = useMutation({
    mutationFn: reorderCategories,
    onSuccess: refreshCategories
  });

  const prepareIllustration = async (
    categoryId: string,
    draft: CategoryIllustrationDraft
  ): Promise<PreparedIllustration> => {
    if (!draft.source) {
      return {
        source: null,
        key: null,
        positionX: 50,
        positionY: 50,
        uploadedPath: null
      };
    }

    if (draft.source === "library") {
      if (!draft.key) throw new Error("Escolha uma ilustração da biblioteca.");
      return {
        source: "library",
        key: draft.key,
        positionX: 50,
        positionY: 50,
        uploadedPath: null
      };
    }

    if (draft.file) {
      const uploadedPath = await uploadCategoryIllustration(categoryId, draft.file);
      return {
        source: "upload",
        key: uploadedPath,
        positionX: draft.positionX,
        positionY: draft.positionY,
        uploadedPath
      };
    }

    if (draft.key) {
      return {
        source: "upload",
        key: draft.key,
        positionX: draft.positionX,
        positionY: draft.positionY,
        uploadedPath: null
      };
    }

    throw new Error("Selecione novamente a imagem da categoria.");
  };

  const cleanupUploadedPath = async (path: string | null) => {
    if (!path) return;
    try {
      await removeCategoryIllustration(path);
    } catch {
      // O cadastro/edição principal não deve ser revertido por uma limpeza tardia.
    }
  };

  const onCreate = handleSubmit(async (values) => {
    setNotice(null);
    setActionError(null);

    const parsed = categoryNameSchema.safeParse(values.name);
    if (!parsed.success) {
      setError("name", { message: parsed.error.issues[0]?.message ?? "Nome inválido." });
      return;
    }

    const maxSortOrder = Math.max(
      0,
      ...(categoriesQuery.data ?? []).map((category) => category.sort_order ?? 0)
    );
    const categoryId = crypto.randomUUID();
    let prepared: PreparedIllustration | null = null;

    try {
      prepared = await prepareIllustration(categoryId, createIllustration);

      const payload: CategoryDetailsInput = {
        id: categoryId,
        name: parsed.data,
        sortOrder: maxSortOrder + 1,
        illustrationSource: prepared.source,
        illustrationKey: prepared.key,
        illustrationPositionX: prepared.positionX,
        illustrationPositionY: prepared.positionY
      };

      await createMutation.mutateAsync(payload);
      reset();
      setCreateIllustration(emptyCategoryIllustrationDraft());
      setCreating(false);
      setNotice("Categoria criada com sucesso.");
    } catch (error) {
      await cleanupUploadedPath(prepared?.uploadedPath ?? null);
      setError("name", { message: getCategoryErrorMessage(error) });
    }
  });

  const startEditing = (category: CategoryListItem) => {
    setNotice(null);
    setActionError(null);
    setCreating(false);
    reset();
    setEditing(category);
    setEditingName(category.name);
    setEditingIllustration({
      source: category.illustration_source,
      key: category.illustration_key,
      file: null,
      previewUrl: category.illustrationUrl,
      positionX: category.illustration_position_x ?? 50,
      positionY: category.illustration_position_y ?? 50
    });
  };

  const cancelEditing = () => {
    setEditing(null);
    setEditingName("");
    setEditingIllustration(emptyCategoryIllustrationDraft());
    setActionError(null);
  };

  const saveEditing = async () => {
    if (!editing) return;

    setNotice(null);
    setActionError(null);
    const parsed = categoryNameSchema.safeParse(editingName);

    if (!parsed.success) {
      setActionError(parsed.error.issues[0]?.message ?? "Nome inválido.");
      return;
    }

    const oldUploadPath =
      editing.illustration_source === "upload" ? editing.illustration_key : null;
    let prepared: PreparedIllustration | null = null;

    try {
      prepared = await prepareIllustration(editing.id, editingIllustration);
      await updateMutation.mutateAsync({
        id: editing.id,
        name: parsed.data,
        illustrationSource: prepared.source,
        illustrationKey: prepared.key,
        illustrationPositionX: prepared.positionX,
        illustrationPositionY: prepared.positionY
      });

      if (oldUploadPath && oldUploadPath !== prepared.key) {
        await cleanupUploadedPath(oldUploadPath);
      }

      setEditing(null);
      setEditingName("");
      setEditingIllustration(emptyCategoryIllustrationDraft());
      setNotice("Categoria atualizada com sucesso.");
    } catch (error) {
      if (prepared?.uploadedPath) {
        await cleanupUploadedPath(prepared.uploadedPath);
      }
      setActionError(getCategoryErrorMessage(error));
    }
  };

  const deleteCategory = async (category: CategoryListItem) => {
    setNotice(null);
    setActionError(null);

    if (category.productCount > 0) {
      setActionError("A categoria só pode ser excluída quando estiver vazia.");
      return;
    }

    const confirmed = window.confirm(
      `Excluir a categoria “${category.name}”? Ela irá para a lixeira e poderá ser restaurada por 7 dias.`
    );

    if (!confirmed) return;

    try {
      await deleteMutation.mutateAsync(category.id);
      setNotice("Categoria enviada para a lixeira.");
    } catch (error) {
      setActionError(getCategoryErrorMessage(error));
    }
  };

  const beginReordering = () => {
    const categories = categoriesQuery.data ?? [];
    if (categories.length < 2) return;

    setNotice(null);
    setActionError(null);
    setCreating(false);
    reset();
    cancelEditing();
    setDraftOrder(categories);
    setReordering(true);
  };

  const cancelReordering = () => {
    setReordering(false);
    setDraftOrder([]);
    setDraggingId(null);
    setActionError(null);
  };

  const saveReordering = async () => {
    setNotice(null);
    setActionError(null);

    try {
      await reorderMutation.mutateAsync(draftOrder.map((category) => category.id));
      setReordering(false);
      setDraftOrder([]);
      setDraggingId(null);
      setNotice("Ordem das categorias atualizada com sucesso.");
    } catch {
      setActionError(
        "Não foi possível salvar a nova ordem. A ordem oficial no banco foi mantida; tente novamente."
      );
      await refreshCategories();
    }
  };

  const moveCategory = (categoryId: string, direction: -1 | 1) => {
    setDraftOrder((current) => {
      const currentIndex = current.findIndex((category) => category.id === categoryId);
      const nextIndex = currentIndex + direction;
      if (currentIndex < 0 || nextIndex < 0 || nextIndex >= current.length) return current;

      const targetCategory = current[nextIndex];
      if (!targetCategory) return current;

      return moveItemById(current, categoryId, targetCategory.id);
    });
  };

  const moveDraggedCategory = (categoryId: string, clientX: number, clientY: number) => {
    const target = document
      .elementFromPoint(clientX, clientY)
      ?.closest<HTMLElement>("[data-category-sort-id]");
    const overId = target?.dataset.categorySortId;

    if (!overId || overId === categoryId) return;
    setDraftOrder((current) => moveItemById(current, categoryId, overId));
  };

  const beginDrag = (event: ReactPointerEvent<HTMLButtonElement>, categoryId: string) => {
    if (!event.isPrimary || (event.pointerType === "mouse" && event.button !== 0)) return;

    event.currentTarget.setPointerCapture(event.pointerId);
    setDraggingId(categoryId);
  };

  const drag = (event: ReactPointerEvent<HTMLButtonElement>, categoryId: string) => {
    if (draggingId !== categoryId) return;
    event.preventDefault();
    moveDraggedCategory(categoryId, event.clientX, event.clientY);
  };

  const endDrag = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    setDraggingId(null);
  };

  const isSaving =
    createMutation.isPending ||
    updateMutation.isPending ||
    deleteMutation.isPending ||
    reorderMutation.isPending;

  const visibleCategories = reordering ? draftOrder : (categoriesQuery.data ?? []);

  return (
    <AppShell title="Categorias" showBack backTo="/produtos">
      <section>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Categorias do estoque</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
              A ordem exibida aqui será a base para a organização dos produtos e das conferências.
            </p>
          </div>

          {reordering ? (
            <div className="flex flex-wrap gap-2">
              <Button variant="ghost" disabled={reorderMutation.isPending} onClick={cancelReordering}>
                Cancelar
              </Button>
              <Button disabled={reorderMutation.isPending} onClick={() => void saveReordering()}>
                {reorderMutation.isPending ? "Salvando…" : "Salvar ordem"}
              </Button>
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              <Link
                to="/produtos/categorias/lixeira"
                className="inline-flex min-h-11 items-center justify-center rounded-xl border border-red-200 bg-white px-4 py-2.5 text-sm font-semibold text-red-700 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2"
              >
                Lixeira
              </Link>
              <Button
                variant="secondary"
                disabled={(categoriesQuery.data?.length ?? 0) < 2 || isSaving}
                onClick={beginReordering}
              >
                Reordenar categorias
              </Button>
              <Button
                disabled={isSaving}
                onClick={() => {
                  cancelEditing();
                  setCreating((value) => !value);
                  setCreateIllustration(emptyCategoryIllustrationDraft());
                  reset();
                }}
              >
                {creating ? "Cancelar" : "+ Criar categoria"}
              </Button>
            </div>
          )}
        </div>

        {creating && !reordering ? (
          <Card className="mt-5 p-5">
            <form onSubmit={onCreate}>
              <TextField
                label="Nome"
                placeholder="Ex.: CONFEITARIA"
                autoFocus
                error={errors.name?.message}
                {...register("name")}
              />
              <CategoryIllustrationPicker
                value={createIllustration}
                onChange={setCreateIllustration}
                categoryName={createName}
              />
              <div className="mt-5 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  onClick={() => {
                    reset();
                    setCreateIllustration(emptyCategoryIllustrationDraft());
                    setCreating(false);
                  }}
                >
                  Cancelar
                </Button>
                <Button type="submit" disabled={createMutation.isPending}>
                  {createMutation.isPending ? "Salvando…" : "Salvar categoria"}
                </Button>
              </div>
            </form>
          </Card>
        ) : null}

        {notice ? (
          <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
            {notice}
          </div>
        ) : null}

        {actionError ? (
          <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            {actionError}
          </div>
        ) : null}

        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
          {reordering
            ? "Modo de reordenação ativo: arraste pelo marcador de cada cartão. No teclado, use os botões subir/descer. A nova ordem só vira oficial ao tocar em Salvar ordem."
            : "Categorias já possuem cadastro, edição, lixeira, restauração, ordem manual e ilustração opcional por biblioteca ou imagem própria. Imagens próprias aceitam JPG, PNG ou WEBP até 5 MB."}
        </div>

        {categoriesQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando categorias…</Card>
        ) : null}

        {categoriesQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5">
            <p className="text-sm font-medium text-red-800">Não foi possível carregar as categorias.</p>
            <Button
              className="mt-4"
              variant="secondary"
              onClick={() => void categoriesQuery.refetch()}
            >
              Tentar novamente
            </Button>
          </Card>
        ) : null}

        {!categoriesQuery.isPending &&
        !categoriesQuery.isError &&
        visibleCategories.length === 0 ? (
          <Card className="mt-5 p-6 text-center">
            <div className="mx-auto">
              <CategoryIllustrationVisual
                source={null}
                illustrationKey={null}
                className="h-12 w-12"
              />
            </div>
            <h3 className="mt-4 font-semibold">Nenhuma categoria cadastrada</h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Crie a primeira categoria para começar a organizar o catálogo.
            </p>
          </Card>
        ) : null}

        <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {visibleCategories.map((category, index) => (
            <Card
              key={category.id}
              data-category-sort-id={reordering ? category.id : undefined}
              className={`${
                editing?.id === category.id && !reordering
                  ? "p-5 sm:col-span-2 lg:col-span-3"
                  : "p-4"
              } ${draggingId === category.id ? "ring-2 ring-red-300" : ""}`}
            >
              {reordering ? (
                <>
                  <div className="flex items-start gap-3">
                    <button
                      type="button"
                      className="flex h-11 w-11 shrink-0 touch-none select-none items-center justify-center rounded-xl bg-red-50 text-red-700 transition hover:bg-red-100 active:cursor-grabbing"
                      aria-label={`Arrastar categoria ${category.name}`}
                      aria-pressed={draggingId === category.id}
                      onPointerDown={(event) => beginDrag(event, category.id)}
                      onPointerMove={(event) => drag(event, category.id)}
                      onPointerUp={endDrag}
                      onPointerCancel={endDrag}
                    >
                      <DragHandleIcon />
                    </button>
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-medium uppercase tracking-wide text-zinc-400">
                        Posição {index + 1}
                      </p>
                      <h3 className="mt-1 break-words font-semibold text-zinc-900">{category.name}</h3>
                      <p className="mt-1 text-xs text-zinc-500">
                        {category.productCount === 1
                          ? "1 produto"
                          : `${category.productCount} produtos`}
                      </p>
                    </div>
                  </div>

                  <div className="mt-4 flex justify-end gap-2 border-t border-zinc-100 pt-3">
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={index === 0 || reorderMutation.isPending}
                      aria-label={`Mover ${category.name} para cima`}
                      onClick={() => moveCategory(category.id, -1)}
                    >
                      ↑ Subir
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={index === visibleCategories.length - 1 || reorderMutation.isPending}
                      aria-label={`Mover ${category.name} para baixo`}
                      onClick={() => moveCategory(category.id, 1)}
                    >
                      ↓ Descer
                    </Button>
                  </div>
                </>
              ) : editing?.id === category.id ? (
                <>
                  <TextField
                    label="Nome da categoria"
                    value={editingName}
                    onChange={(event) => setEditingName(event.target.value)}
                    error={actionError}
                    autoFocus
                  />
                  <CategoryIllustrationPicker
                    value={editingIllustration}
                    onChange={setEditingIllustration}
                    categoryName={editingName}
                  />
                  <div className="mt-5 flex flex-wrap justify-end gap-2">
                    <Button variant="ghost" size="sm" onClick={cancelEditing}>
                      Cancelar
                    </Button>
                    <Button
                      size="sm"
                      disabled={updateMutation.isPending}
                      onClick={() => void saveEditing()}
                    >
                      {updateMutation.isPending ? "Salvando…" : "Salvar alterações"}
                    </Button>
                  </div>
                </>
              ) : (
                <>
                  <div className="flex items-start gap-3">
                    <CategoryIllustrationVisual
                      source={category.illustration_source}
                      illustrationKey={category.illustration_key}
                      illustrationUrl={category.illustrationUrl}
                      positionX={category.illustration_position_x}
                      positionY={category.illustration_position_y}
                      className="h-14 w-14 shrink-0"
                    />
                    <div className="min-w-0 flex-1">
                      <h3 className="break-words font-semibold text-zinc-900">{category.name}</h3>
                      <p className="mt-1 text-xs text-zinc-500">
                        {category.productCount === 1
                          ? "1 produto"
                          : `${category.productCount} produtos`}
                      </p>
                    </div>
                  </div>

                  <div className="mt-4 flex flex-wrap justify-end gap-2 border-t border-zinc-100 pt-3">
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={isSaving}
                      onClick={() => startEditing(category)}
                    >
                      Editar
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      disabled={isSaving || category.productCount > 0}
                      title={
                        category.productCount > 0
                          ? "Remova ou mova os produtos antes de excluir esta categoria."
                          : undefined
                      }
                      onClick={() => void deleteCategory(category)}
                    >
                      Excluir
                    </Button>
                  </div>
                </>
              )}
            </Card>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

function DragHandleIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor">
      <circle cx="8" cy="7" r="1.5" />
      <circle cx="16" cy="7" r="1.5" />
      <circle cx="8" cy="12" r="1.5" />
      <circle cx="16" cy="12" r="1.5" />
      <circle cx="8" cy="17" r="1.5" />
      <circle cx="16" cy="17" r="1.5" />
    </svg>
  );
}

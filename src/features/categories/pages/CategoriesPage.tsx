import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import {
  createCategory,
  listActiveCategories,
  renameCategory,
  softDeleteCategory,
  type CategoryListItem
} from "../api/categories";
import { categoryNameSchema, getCategoryErrorMessage } from "../lib/categoryValidation";

type CategoryForm = {
  name: string;
};

const categoriesKey = ["categories", "active"] as const;

export function CategoriesPage() {
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<CategoryListItem | null>(null);
  const [editingName, setEditingName] = useState("");
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
    formState: { errors }
  } = useForm<CategoryForm>({
    defaultValues: { name: "" }
  });

  const refreshCategories = async () => {
    await queryClient.invalidateQueries({ queryKey: categoriesKey });
  };

  const createMutation = useMutation({
    mutationFn: ({ name, sortOrder }: { name: string; sortOrder: number }) =>
      createCategory(name, sortOrder),
    onSuccess: refreshCategories
  });

  const renameMutation = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => renameCategory(id, name),
    onSuccess: refreshCategories
  });

  const deleteMutation = useMutation({
    mutationFn: softDeleteCategory,
    onSuccess: refreshCategories
  });

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

    try {
      await createMutation.mutateAsync({
        name: parsed.data,
        sortOrder: maxSortOrder + 1
      });
      reset();
      setCreating(false);
      setNotice("Categoria criada com sucesso.");
    } catch (error) {
      setError("name", { message: getCategoryErrorMessage(error) });
    }
  });

  const startEditing = (category: CategoryListItem) => {
    setNotice(null);
    setActionError(null);
    setEditing(category);
    setEditingName(category.name);
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

    try {
      await renameMutation.mutateAsync({ id: editing.id, name: parsed.data });
      setEditing(null);
      setEditingName("");
      setNotice("Categoria atualizada com sucesso.");
    } catch (error) {
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

  const isSaving =
    createMutation.isPending || renameMutation.isPending || deleteMutation.isPending;

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

          <div className="flex flex-wrap gap-2">
            <Link
              to="/produtos/categorias/lixeira"
              className="inline-flex min-h-11 items-center justify-center rounded-xl border border-red-200 bg-white px-4 py-2.5 text-sm font-semibold text-red-700 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2"
            >
              Lixeira
            </Link>
            <Button onClick={() => setCreating((value) => !value)}>
              {creating ? "Cancelar" : "+ Criar categoria"}
            </Button>
          </div>
        </div>

        {creating ? (
          <Card className="mt-5 p-5">
            <form onSubmit={onCreate}>
              <TextField
                label="Nome"
                placeholder="Ex.: CONFEITARIA"
                autoFocus
                error={errors.name?.message}
                {...register("name")}
              />
              <div className="mt-4 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  onClick={() => {
                    reset();
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
          Cadastro, renomeação, exclusão segura e restauração pela lixeira já usam o banco real.
          Ilustrações e reordenação por arrastar continuam nos próximos incrementos deste módulo.
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
        (categoriesQuery.data?.length ?? 0) === 0 ? (
          <Card className="mt-5 p-6 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-red-50 text-red-700">
              <GenericCategoryIcon />
            </div>
            <h3 className="mt-4 font-semibold">Nenhuma categoria cadastrada</h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Crie a primeira categoria para começar a organizar o catálogo.
            </p>
          </Card>
        ) : null}

        <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {categoriesQuery.data?.map((category) => (
            <Card key={category.id} className="p-4">
              {editing?.id === category.id ? (
                <>
                  <TextField
                    label="Nome da categoria"
                    value={editingName}
                    onChange={(event) => setEditingName(event.target.value)}
                    error={actionError}
                    autoFocus
                  />
                  <div className="mt-4 flex flex-wrap justify-end gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        setEditing(null);
                        setEditingName("");
                        setActionError(null);
                      }}
                    >
                      Cancelar
                    </Button>
                    <Button size="sm" disabled={renameMutation.isPending} onClick={() => void saveEditing()}>
                      {renameMutation.isPending ? "Salvando…" : "Salvar"}
                    </Button>
                  </div>
                </>
              ) : (
                <>
                  <div className="flex items-start gap-3">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-red-50 text-red-700">
                      <GenericCategoryIcon />
                    </div>
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

function GenericCategoryIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none">
      <path
        d="M4.5 6.5A2.5 2.5 0 0 1 7 4h3l1.4 2H17a2.5 2.5 0 0 1 2.5 2.5v8A2.5 2.5 0 0 1 17 19H7a2.5 2.5 0 0 1-2.5-2.5v-10Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
    </svg>
  );
}

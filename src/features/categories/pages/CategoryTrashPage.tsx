import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import {
  listRestorableCategories,
  restoreCategory,
  type CategoryTrashItem
} from "../api/categories";
import { getCategoryErrorMessage } from "../lib/categoryValidation";

const activeCategoriesKey = ["categories", "active"] as const;
const trashCategoriesKey = ["categories", "trash"] as const;

export function CategoryTrashPage() {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const trashQuery = useQuery({
    queryKey: trashCategoriesKey,
    queryFn: listRestorableCategories
  });

  const restoreMutation = useMutation({
    mutationFn: restoreCategory,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: trashCategoriesKey }),
        queryClient.invalidateQueries({ queryKey: activeCategoriesKey })
      ]);
    }
  });

  const restore = async (category: CategoryTrashItem) => {
    setNotice(null);
    setActionError(null);

    const confirmed = window.confirm(
      `Restaurar a categoria “${category.name}”? Ela voltará para a posição manual anterior.`
    );

    if (!confirmed) return;

    try {
      await restoreMutation.mutateAsync(category.id);
      setNotice(`Categoria “${category.name}” restaurada com sucesso.`);
    } catch (error) {
      setActionError(getCategoryErrorMessage(error));
    }
  };

  return (
    <AppShell title="Lixeira de categorias" showBack backTo="/produtos/categorias">
      <section>
        <div>
          <h2 className="text-xl font-semibold tracking-tight">Categorias excluídas</h2>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
            Categorias ficam disponíveis para restauração por 7 dias. Depois desse prazo, deixam de
            aparecer aqui.
          </p>
        </div>

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

        {trashQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando lixeira…</Card>
        ) : null}

        {trashQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5">
            <p className="text-sm font-medium text-red-800">
              Não foi possível carregar a lixeira de categorias.
            </p>
            <Button
              className="mt-4"
              variant="secondary"
              onClick={() => void trashQuery.refetch()}
            >
              Tentar novamente
            </Button>
          </Card>
        ) : null}

        {!trashQuery.isPending &&
        !trashQuery.isError &&
        (trashQuery.data?.length ?? 0) === 0 ? (
          <Card className="mt-5 p-6 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-zinc-100 text-zinc-500">
              <TrashIcon />
            </div>
            <h3 className="mt-4 font-semibold">A lixeira está vazia</h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Quando uma categoria vazia for excluída, ela poderá ser restaurada aqui durante o
              prazo de 7 dias.
            </p>
          </Card>
        ) : null}

        <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {trashQuery.data?.map((category) => (
            <Card key={category.id} className="p-4">
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-zinc-100 text-zinc-600">
                  <TrashIcon />
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="break-words font-semibold text-zinc-900">{category.name}</h3>
                  <p className="mt-1 text-xs leading-5 text-zinc-500">
                    Restaurável até {formatRestoreUntil(category.restore_until)}
                  </p>
                </div>
              </div>

              <div className="mt-4 flex justify-end border-t border-zinc-100 pt-3">
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={restoreMutation.isPending}
                  onClick={() => void restore(category)}
                >
                  {restoreMutation.isPending ? "Restaurando…" : "Restaurar"}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

function formatRestoreUntil(value: string | null) {
  if (!value) return "prazo indisponível";

  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

function TrashIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none">
      <path
        d="M4 7h16M9 3h6l1 4H8l1-4Zm-2 4 1 13h8l1-13M10 11v5M14 11v5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

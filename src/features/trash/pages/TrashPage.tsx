import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { useAuth } from "../../auth/context/AuthContext";
import { listRestorableTrashItems, restoreTrashItem, type TrashItem, type TrashItemType } from "../api/trash";

type Filter = "all" | TrashItemType;
const trashKey = ["trash", "restorable"] as const;

export function TrashPage() {
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();
  const [searchParams] = useSearchParams();
  const [filter, setFilter] = useState<Filter>(() => parseFilter(searchParams.get("filter")));
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingRestore, setPendingRestore] = useState<TrashItem | null>(null);

  const trashQuery = useQuery({ queryKey: trashKey, queryFn: listRestorableTrashItems });

  const restoreMutation = useMutation({
    mutationFn: (item: Pick<TrashItem, "id" | "type">) => restoreTrashItem(item, deviceId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: trashKey }),
        queryClient.invalidateQueries({ queryKey: ["products", "active"] }),
        queryClient.invalidateQueries({ queryKey: ["categories", "active"] }),
        queryClient.invalidateQueries({ queryKey: ["products", "categories"] }),
        queryClient.invalidateQueries({ queryKey: ["suppliers"] }),
        queryClient.invalidateQueries({ queryKey: ["entries"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] })
      ]);
    }
  });

  const items = useMemo(() => {
    const all = trashQuery.data ?? [];
    return filter === "all" ? all : all.filter((item) => item.type === filter);
  }, [filter, trashQuery.data]);

  const confirmRestore = async () => {
    if (!pendingRestore) return;
    setNotice(null);
    setActionError(null);
    try {
      await restoreMutation.mutateAsync({ id: pendingRestore.id, type: pendingRestore.type });
      setNotice(`${trashTypeLabel(pendingRestore.type)} “${pendingRestore.name}” restaurado com sucesso.`);
      setPendingRestore(null);
    } catch (error) {
      setActionError(getTrashErrorMessage(error));
    }
  };

  return (
    <AppShell title="Lixeira" showBack backTo="/">
      <section>
        <div>
          <h2 className="text-xl font-semibold tracking-tight">Itens excluídos</h2>
          <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">Produtos, categorias, fornecedores e Entradas permanecem restauráveis por 7 dias. Depois desse prazo, deixam de aparecer aqui.</p>
        </div>

        <div className="mt-5 flex flex-wrap gap-2">
          <FilterButton active={filter === "all"} onClick={() => setFilter("all")}>Todos</FilterButton>
          <FilterButton active={filter === "product"} onClick={() => setFilter("product")}>Produtos</FilterButton>
          <FilterButton active={filter === "category"} onClick={() => setFilter("category")}>Categorias</FilterButton>
          <FilterButton active={filter === "supplier"} onClick={() => setFilter("supplier")}>Fornecedores</FilterButton>
          <FilterButton active={filter === "entry"} onClick={() => setFilter("entry")}>Entradas</FilterButton>
        </div>

        {notice ? <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</div> : null}
        {actionError ? <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{actionError}</div> : null}

        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
          A Lixeira Universal já restaura Produtos, Categorias, Fornecedores e Entradas. Restaurar uma Entrada recoloca seus efeitos nos cálculos ativos. Exclusão definitiva e “Esvaziar lixeira” continuam reservados para uma etapa posterior com confirmação reforçada.
        </div>

        {trashQuery.isPending ? <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando lixeira…</Card> : null}

        {trashQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5">
            <p className="text-sm font-medium text-red-800">Não foi possível carregar a lixeira.</p>
            <Button className="mt-4" variant="secondary" onClick={() => void trashQuery.refetch()}>Tentar novamente</Button>
          </Card>
        ) : null}

        {!trashQuery.isPending && !trashQuery.isError && items.length === 0 ? (
          <Card className="mt-5 p-7 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-zinc-100 text-zinc-500"><TrashIcon /></div>
            <h3 className="mt-4 font-semibold">Nenhum item neste filtro</h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">Itens excluídos dentro da janela de 7 dias aparecerão aqui.</p>
          </Card>
        ) : null}

        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          {items.map((item) => (
            <Card key={`${item.type}-${item.id}`} className="p-5">
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-zinc-100 text-zinc-600"><TrashIcon /></div>
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">{trashTypeLabel(item.type)}</p>
                  <h3 className="mt-1 break-words font-semibold text-zinc-900">{item.name}</h3>
                  {item.detail ? <p className="mt-1 text-xs text-zinc-500">{item.detail}</p> : null}
                  <p className="mt-2 text-xs text-zinc-500">Restaurável até {formatDateTime(item.restoreUntil)}</p>
                </div>
              </div>

              <div className="mt-4 flex justify-end border-t border-zinc-100 pt-3">
                <Button size="sm" variant="secondary" disabled={restoreMutation.isPending} onClick={() => { setNotice(null); setActionError(null); setPendingRestore(item); }}>Restaurar</Button>
              </div>
            </Card>
          ))}
        </div>

        <ConfirmDialog
          open={Boolean(pendingRestore)}
          title={pendingRestore ? `Restaurar ${trashTypeLabel(pendingRestore.type).toLowerCase()}?` : ""}
          description={pendingRestore ? restoreDescription(pendingRestore) : ""}
          confirmLabel={pendingRestore ? `Restaurar ${trashTypeLabel(pendingRestore.type).toLowerCase()}` : "Restaurar"}
          pendingLabel="Restaurando…"
          isPending={restoreMutation.isPending}
          onCancel={() => setPendingRestore(null)}
          onConfirm={() => void confirmRestore()}
        />
      </section>
    </AppShell>
  );
}

function FilterButton({ active, children, onClick }: { active: boolean; children: string; onClick: () => void }) {
  return <Button size="sm" variant={active ? "primary" : "secondary"} onClick={onClick}>{children}</Button>;
}

function trashTypeLabel(type: TrashItemType) {
  if (type === "product") return "Produto";
  if (type === "supplier") return "Fornecedor";
  if (type === "entry") return "Entrada";
  return "Categoria";
}

function restoreDescription(item: TrashItem) {
  if (item.type === "product") return `Restaurar o produto “${item.name}”? Ele voltará para a posição manual anterior na categoria.`;
  if (item.type === "supplier") return `Restaurar o fornecedor “${item.name}”? Ele voltará ao cadastro ativo e poderá ser usado em novas Entradas.`;
  if (item.type === "entry") return `Restaurar a Entrada de “${item.name}”? Ela voltará ao Histórico ativo e seus efeitos serão recolocados automaticamente nos cálculos atuais de estoque e preço.`;
  return `Restaurar a categoria “${item.name}”? Ela voltará para a posição manual anterior.`;
}

function TrashIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none"><path d="M5 7h14M9 7V4h6v3M8 10v7M12 10v7M16 10v7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M7 7l1 13h8l1-13" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /></svg>;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function getTrashErrorMessage(error: unknown) {
  if (typeof error === "object" && error !== null) {
    const message = (error as { message?: string }).message;
    if (typeof message === "string") {
      if (message.includes("Prazo de restauração expirado")) return "O prazo de 7 dias para restaurar este item expirou.";
      if (message.includes("Produto não encontrado na lixeira") || message.includes("Fornecedor não encontrado na lixeira") || message.includes("Entrada não encontrada na lixeira")) return "Este item não está mais disponível para restauração.";
      if (message.includes("Já existe um fornecedor ativo com este contato")) return "Já existe um fornecedor ativo com este mesmo contato. Revise o cadastro antes de restaurar.";
    }
  }
  return "Não foi possível restaurar o item. Tente novamente.";
}


function parseFilter(value: string | null): Filter {
  if (value === "product" || value === "category" || value === "supplier" || value === "entry") return value;
  return "all";
}

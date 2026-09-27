import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { SearchClearButton } from "../../../shared/components/ui/SearchClearButton";
import { TextField } from "../../../shared/components/ui/TextField";
import { matchesAnySearchText } from "../../../shared/lib/searchText";
import { useAuth } from "../../auth/context/AuthContext";
import {
  emptyTrash,
  listRestorableTrashItems,
  permanentlyDeleteTrashItem,
  restoreTrashItem,
  type EmptyTrashResult,
  type TrashItem,
  type TrashItemType
} from "../api/trash";
import { TrashTypedConfirmDialog } from "../components/TrashTypedConfirmDialog";

type Filter = "all" | TrashItemType;
const trashKey = ["trash", "restorable"] as const;

export function TrashPage() {
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();
  const [searchParams] = useSearchParams();
  const [filter, setFilter] = useState<Filter>(() => parseFilter(searchParams.get("filter")));
  const [search, setSearch] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingRestore, setPendingRestore] = useState<TrashItem | null>(null);
  const [pendingPermanentDelete, setPendingPermanentDelete] = useState<TrashItem | null>(null);
  const [emptyTrashOpen, setEmptyTrashOpen] = useState(false);

  const trashQuery = useQuery({ queryKey: trashKey, queryFn: listRestorableTrashItems });

  const invalidateTrashDependencies = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: trashKey }),
      queryClient.invalidateQueries({ queryKey: ["products", "active"] }),
      queryClient.invalidateQueries({ queryKey: ["categories", "active"] }),
      queryClient.invalidateQueries({ queryKey: ["products", "categories"] }),
      queryClient.invalidateQueries({ queryKey: ["suppliers"] }),
      queryClient.invalidateQueries({ queryKey: ["entries"] }),
      queryClient.invalidateQueries({ queryKey: ["conferences"] }),
      queryClient.invalidateQueries({ queryKey: ["stock"] }),
      queryClient.invalidateQueries({ queryKey: ["purchases"] })
    ]);
  };

  const restoreMutation = useMutation({
    mutationFn: (item: Pick<TrashItem, "id" | "type">) => restoreTrashItem(item, deviceId),
    onSuccess: invalidateTrashDependencies
  });

  const permanentDeleteMutation = useMutation({
    mutationFn: (item: Pick<TrashItem, "id" | "type">) =>
      permanentlyDeleteTrashItem(item, deviceId),
    onSuccess: invalidateTrashDependencies
  });

  const emptyTrashMutation = useMutation({
    mutationFn: () => emptyTrash(deviceId),
    onSuccess: invalidateTrashDependencies
  });

  const items = useMemo(() => {
    const all = trashQuery.data ?? [];
    return all
      .filter((item) => filter === "all" || item.type === filter)
      .filter((item) =>
        matchesAnySearchText(
          [item.name, item.detail ?? "", trashTypeLabel(item.type)],
          search
        )
      );
  }, [filter, search, trashQuery.data]);

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

  const confirmPermanentDelete = async () => {
    if (!pendingPermanentDelete) return;
    setNotice(null);
    setActionError(null);

    try {
      await permanentDeleteMutation.mutateAsync({
        id: pendingPermanentDelete.id,
        type: pendingPermanentDelete.type
      });
      setNotice(
        `${trashTypeLabel(pendingPermanentDelete.type)} “${pendingPermanentDelete.name}” excluído definitivamente da área restaurável.`
      );
      setPendingPermanentDelete(null);
    } catch (error) {
      setActionError(getTrashErrorMessage(error));
    }
  };

  const confirmEmptyTrash = async () => {
    setNotice(null);
    setActionError(null);

    try {
      const result = await emptyTrashMutation.mutateAsync();
      setNotice(emptyTrashSuccessMessage(result));
      setEmptyTrashOpen(false);
    } catch (error) {
      setActionError(getTrashErrorMessage(error));
    }
  };

  return (
    <AppShell title="Lixeira" showBack backTo="/">
      <section>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Itens excluídos</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
              Produtos, categorias, fornecedores, Entradas e Conferências permanecem restauráveis
              por 7 dias. Depois desse prazo, deixam de aparecer aqui.
            </p>
          </div>

          {(trashQuery.data?.length ?? 0) > 0 ? (
            <Button
              variant="danger"
              disabled={emptyTrashMutation.isPending || permanentDeleteMutation.isPending}
              onClick={() => {
                setNotice(null);
                setActionError(null);
                setEmptyTrashOpen(true);
              }}
            >
              Esvaziar lixeira
            </Button>
          ) : null}
        </div>

        <div className="mt-5 max-w-md">
          <div className="relative">
            <TextField
              id="trash-search"
              label="Pesquisar"
              placeholder="Nome, detalhe ou tipo"
              value={search}
              className={search ? "pr-11" : ""}
              onChange={(event) => setSearch(event.target.value)}
            />
            {search ? (
              <SearchClearButton
                onClear={() => {
                  setSearch("");
                  document.getElementById("trash-search")?.focus();
                }}
              />
            ) : null}
          </div>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <FilterButton active={filter === "all"} onClick={() => setFilter("all")}>Todos</FilterButton>
          <FilterButton active={filter === "product"} onClick={() => setFilter("product")}>Produtos</FilterButton>
          <FilterButton active={filter === "category"} onClick={() => setFilter("category")}>Categorias</FilterButton>
          <FilterButton active={filter === "supplier"} onClick={() => setFilter("supplier")}>Fornecedores</FilterButton>
          <FilterButton active={filter === "entry"} onClick={() => setFilter("entry")}>Entradas</FilterButton>
          <FilterButton active={filter === "conference"} onClick={() => setFilter("conference")}>Conferências</FilterButton>
        </div>

        {notice ? <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</div> : null}
        {actionError ? <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{actionError}</div> : null}

        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
          Restaurar recoloca o item na área ativa quando aplicável. <strong>Excluir definitivamente</strong>
          ou <strong>Esvaziar lixeira</strong> torna o item irrecuperável no aplicativo. A identidade
          histórica necessária pode permanecer internamente para não quebrar Entradas, Conferências,
          preços e relatórios antigos.
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
            <h3 className="mt-4 font-semibold">
              {search ? "Nenhum item encontrado" : "Nenhum item neste filtro"}
            </h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              {search
                ? "Tente outro termo ou altere o filtro."
                : "Itens excluídos dentro da janela de 7 dias aparecerão aqui."}
            </p>
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

              <div className="mt-4 flex flex-wrap justify-end gap-2 border-t border-zinc-100 pt-3">
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={restoreMutation.isPending || permanentDeleteMutation.isPending}
                  onClick={() => {
                    setNotice(null);
                    setActionError(null);
                    setPendingRestore(item);
                  }}
                >
                  Restaurar
                </Button>
                <Button
                  size="sm"
                  variant="danger"
                  disabled={restoreMutation.isPending || permanentDeleteMutation.isPending}
                  onClick={() => {
                    setNotice(null);
                    setActionError(null);
                    setPendingPermanentDelete(item);
                  }}
                >
                  Excluir definitivamente
                </Button>
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

        {pendingPermanentDelete ? (
          <TrashTypedConfirmDialog
            title={`Excluir definitivamente ${trashTypeLabel(pendingPermanentDelete.type).toLowerCase()}?`}
            phrase="EXCLUIR"
            confirmLabel="Excluir definitivamente"
            pendingLabel="Excluindo…"
            isPending={permanentDeleteMutation.isPending}
            description={permanentDeleteDescription(pendingPermanentDelete)}
            onCancel={() => setPendingPermanentDelete(null)}
            onConfirm={() => void confirmPermanentDelete()}
          />
        ) : null}

        {emptyTrashOpen ? (
          <TrashTypedConfirmDialog
            title="Esvaziar lixeira?"
            phrase="ESVAZIAR"
            confirmLabel="Esvaziar lixeira"
            pendingLabel="Esvaziando…"
            isPending={emptyTrashMutation.isPending}
            description={`Todos os ${trashQuery.data?.length ?? 0} itens atualmente restauráveis ficarão irrecuperáveis no aplicativo. A identidade histórica necessária será preservada internamente.`}
            onCancel={() => setEmptyTrashOpen(false)}
            onConfirm={() => void confirmEmptyTrash()}
          />
        ) : null}
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
  if (type === "conference") return "Conferência";
  return "Categoria";
}

function restoreDescription(item: TrashItem) {
  if (item.type === "product") return `Restaurar o produto “${item.name}”? Ele voltará para a posição manual anterior na categoria.`;
  if (item.type === "supplier") return `Restaurar o fornecedor “${item.name}”? Ele voltará ao cadastro ativo e poderá ser usado em novas Entradas.`;
  if (item.type === "entry") return `Restaurar a Entrada de “${item.name}”? Ela voltará ao Histórico ativo e seus efeitos serão recolocados automaticamente nos cálculos atuais de estoque e preço.`;
  if (item.type === "conference") return `Restaurar a Conferência de “${item.name}”? Ela voltará ao Histórico ativo e seus efeitos serão recolocados automaticamente nos cálculos de estoque, consumo e relatórios.`;
  return `Restaurar a categoria “${item.name}”? Ela voltará para a posição manual anterior.`;
}

function permanentDeleteDescription(item: TrashItem) {
  return `${trashTypeLabel(item.type)} “${item.name}” não poderá mais ser restaurado. A identidade histórica necessária pode permanecer internamente para preservar registros antigos.`;
}

function emptyTrashSuccessMessage(result: EmptyTrashResult) {
  if (result.total === 0) return "A Lixeira já estava vazia.";

  return `Lixeira esvaziada: ${result.total} ${result.total === 1 ? "item ficou" : "itens ficaram"} irrecuperável${result.total === 1 ? "" : "is"} no aplicativo.`;
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
      if (message.includes("Item excluído definitivamente")) return "Este item foi excluído definitivamente e não pode mais ser restaurado.";
      if (message.includes("Item não está disponível para exclusão definitiva")) return "Este item não está mais disponível para exclusão definitiva.";
      if (message.includes("Dispositivo não autorizado")) return "Este dispositivo não está autorizado para esta ação.";
      if (message.includes("Produto não encontrado na lixeira") || message.includes("Fornecedor não encontrado na lixeira") || message.includes("Entrada não encontrada na lixeira") || message.includes("Conferência não encontrada na lixeira")) return "Este item não está mais disponível para restauração.";
      if (message.includes("Já existe um fornecedor ativo com este contato")) return "Já existe um fornecedor ativo com este mesmo contato. Revise o cadastro antes de restaurar.";
    }
  }
  return "Não foi possível concluir a ação na Lixeira. Tente novamente.";
}


function parseFilter(value: string | null): Filter {
  if (value === "product" || value === "category" || value === "supplier" || value === "entry" || value === "conference") return value;
  return "all";
}

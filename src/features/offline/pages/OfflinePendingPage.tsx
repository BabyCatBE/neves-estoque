import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Card } from "../../../shared/components/ui/Card";
import type { OfflinePendingOperationRecord } from "../../../shared/offline/offlineDb";
import { useOfflinePendingOperations } from "../hooks/useOfflinePendingOperations";
import { deleteOfflinePendingOperation } from "../lib/pendingOperations";

export function OfflinePendingPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { items, loading } = useOfflinePendingOperations();
  const [pendingDelete, setPendingDelete] = useState<OfflinePendingOperationRecord | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const savedKind = searchParams.get("saved");
  const saved = savedKind === "entry" || savedKind === "conference";

  const visibleItems = useMemo(() => items, [items]);

  const clearSavedFlag = () => {
    if (!saved) return;
    const next = new URLSearchParams(searchParams);
    next.delete("saved");
    setSearchParams(next, { replace: true });
  };

  const confirmDelete = async () => {
    if (!pendingDelete || deleting) return;
    setDeleting(true);
    setError(null);
    try {
      await deleteOfflinePendingOperation(pendingDelete.id);
      setPendingDelete(null);
    } catch {
      setError("Não foi possível excluir esta pendência local.");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <AppShell title="Pendências locais" showBack backTo="/alertas">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Pendências offline</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          Operações preparadas sem internet ficam somente neste aparelho. Elas não alteram o
          estoque oficial até uma confirmação consciente quando a conexão voltar.
        </p>

        {saved ? (
          <button
            type="button"
            onClick={clearSavedFlag}
            className="mt-5 w-full rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-left text-sm text-emerald-900"
          >
            {savedKind === "conference" ? "Conferência" : "Entrada"} salva como PENDENTE DE CONFIRMAÇÃO neste aparelho. Toque para dispensar esta mensagem.
          </button>
        ) : null}

        {error ? (
          <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            {error}
          </div>
        ) : null}

        {loading ? <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando pendências locais…</Card> : null}

        {!loading && items.length === 0 ? (
          <Card className="mt-5 p-6 text-center">
            <h3 className="font-semibold">Nenhuma pendência local</h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Quando uma Entrada ou Conferência for preparada offline, ela aparecerá aqui antes de qualquer envio.
            </p>
          </Card>
        ) : null}

        {visibleItems.length > 0 ? (
          <div className="mt-6 space-y-3">
            {visibleItems.map((item) => (
              <Card key={item.id} className="p-5">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-900">
                        PENDENTE DE CONFIRMAÇÃO
                      </span>
                      <span className="text-xs font-semibold uppercase tracking-wide text-zinc-400">
                        {item.kind === "conference" ? "Conferência" : "Entrada"}
                      </span>
                    </div>
                    <h3 className="mt-3 text-lg font-semibold text-zinc-950">{item.summaryTitle}</h3>
                    <p className="mt-1 text-sm text-zinc-600">
                      {item.itemCount} {item.itemCount === 1 ? "produto" : "produtos"} · fato em {formatDateTime(item.effectiveAt)}
                    </p>
                    <p className="mt-1 text-xs text-zinc-500">
                      Preparada por {item.actorLabel} · salva localmente em {formatDateTime(item.createdAt)}
                    </p>
                    {item.summarySubtitle ? (
                      <p className="mt-3 text-sm leading-6 text-zinc-600">{item.summarySubtitle}</p>
                    ) : null}
                  </div>

                  <button
                    type="button"
                    onClick={() => setPendingDelete(item)}
                    className="min-h-10 rounded-xl border border-red-200 bg-white px-4 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-50"
                  >
                    Excluir pendência
                  </button>
                </div>
              </Card>
            ))}
          </div>
        ) : null}

        <div className="mt-5 rounded-xl border border-zinc-200 bg-zinc-100 px-4 py-3 text-xs leading-5 text-zinc-600">
          Nesta etapa o aplicativo apenas guarda e mostra as pendências de Entrada e Conferência. Editar e confirmar o envio serão adicionados nos próximos incrementos. Nada é enviado automaticamente ao recuperar a internet.
        </div>

        <ConfirmDialog
          open={Boolean(pendingDelete)}
          variant="danger"
          title="Excluir pendência local?"
          description="Esta operação ainda não existe no Supabase. Excluir remove somente o rascunho deste aparelho e não altera o estoque oficial."
          confirmLabel="Excluir pendência"
          pendingLabel="Excluindo…"
          isPending={deleting}
          onCancel={() => setPendingDelete(null)}
          onConfirm={() => void confirmDelete()}
        />
      </section>
    </AppShell>
  );
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "data indisponível";
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(date);
}

import { useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { useNetworkStatus } from "../../../shared/offline/NetworkContext";
import type { OfflinePendingOperationRecord } from "../../../shared/offline/offlineDb";
import {
  createCategoryConference,
  createProductConference,
  listConferenceCategories,
  listSameDayCategoryConferences,
  reviewConferenceConsumption,
  type CategoryConferenceWriteInput,
  type ConferenceConsumptionWarning,
  type ConferenceHistoryItem,
  type ProductConferenceWriteInput
} from "../../conferences/api/conferences";
import {
  getConferenceErrorMessage,
  localDateKey
} from "../../conferences/lib/conferenceValidation";
import {
  createEntry,
  findEntryConferenceConflicts,
  type CreateEntryInput,
  type EntryConferenceConflict
} from "../../entries/api/entries";
import { getEntryErrorMessage } from "../../entries/lib/entryValidation";
import { listActiveProducts, type ProductListItem } from "../../products/api/products";
import { listCurrentStock } from "../../stock/api/stock";
import { listActiveSuppliers } from "../../suppliers/api/suppliers";
import { useOfflinePendingOperations } from "../hooks/useOfflinePendingOperations";
import {
  deleteOfflinePendingOperation
} from "../lib/pendingOperations";
import {
  buildEntryConflictPositions,
  type EntryConflictPosition
} from "../lib/pendingSync";

type PendingPayload =
  | CreateEntryInput
  | CategoryConferenceWriteInput
  | ProductConferenceWriteInput;

type PreparedConfirmation = {
  record: OfflinePendingOperationRecord;
  payload: PendingPayload;
};

type EntryConflictReview = {
  record: OfflinePendingOperationRecord;
  payload: CreateEntryInput;
  conflicts: EntryConferenceConflict[];
  positions: EntryConflictPosition[];
  selectedPositionId: string;
};

type ConferenceReview = {
  record: OfflinePendingOperationRecord;
  payload: CategoryConferenceWriteInput | ProductConferenceWriteInput;
  warnings: ConferenceConsumptionWarning[];
  sameDay: ConferenceHistoryItem[];
  products: ProductListItem[];
};

export function OfflinePendingPage() {
  const queryClient = useQueryClient();
  const { isOnline } = useNetworkStatus();
  const [searchParams, setSearchParams] = useSearchParams();
  const { items, loading } = useOfflinePendingOperations();
  const [pendingDelete, setPendingDelete] = useState<OfflinePendingOperationRecord | null>(null);
  const [prepared, setPrepared] = useState<PreparedConfirmation | null>(null);
  const [entryConflictReview, setEntryConflictReview] = useState<EntryConflictReview | null>(null);
  const [conferenceReview, setConferenceReview] = useState<ConferenceReview | null>(null);
  const [checkingId, setCheckingId] = useState<string | null>(null);
  const [sendingId, setSendingId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
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
    setSuccess(null);
    try {
      await deleteOfflinePendingOperation(pendingDelete.id);
      setPendingDelete(null);
      setSuccess("Pendência local excluída. Nenhum registro oficial foi alterado.");
    } catch {
      setError("Não foi possível excluir esta pendência local.");
    } finally {
      setDeleting(false);
    }
  };

  const refreshOnlineState = async () => {
    await Promise.all([
      listActiveProducts(),
      listActiveSuppliers(),
      listCurrentStock(),
      listConferenceCategories()
    ]);
  };

  const startConfirmation = async (record: OfflinePendingOperationRecord) => {
    if (!isOnline || checkingId || sendingId) return;

    setCheckingId(record.id);
    setError(null);
    setSuccess(null);

    try {
      await refreshOnlineState();

      if (record.kind === "entry") {
        const payload = record.payload as CreateEntryInput;
        const conflicts = await findEntryConferenceConflicts(payload);

        if (conflicts.length) {
          const positions = buildEntryConflictPositions(conflicts, payload.effectiveAt);
          if (!positions.length) {
            throw new Error(
              "Há Conferências relevantes nesta data, mas não foi possível definir uma posição cronológica segura para a Entrada."
            );
          }

          const firstPosition = positions[0];
          if (!firstPosition) {
            throw new Error(
              "Há Conferências relevantes nesta data, mas não foi possível definir uma posição cronológica segura para a Entrada."
            );
          }

          setEntryConflictReview({
            record,
            payload,
            conflicts,
            positions,
            selectedPositionId: firstPosition.id
          });
          return;
        }

        setPrepared({ record, payload });
        return;
      }

      const payload = record.payload as
        | CategoryConferenceWriteInput
        | ProductConferenceWriteInput;
      const items = isCategoryConference(payload)
        ? payload.items
        : [{ productId: payload.productId, quantity: payload.quantity }];

      const [warnings, sameDay, products] = await Promise.all([
        reviewConferenceConsumption({
          effectiveAt: payload.effectiveAt,
          items
        }),
        isCategoryConference(payload)
          ? listSameDayCategoryConferences(
              payload.categoryId,
              localDateKey(payload.effectiveAt)
            )
          : Promise.resolve([]),
        listActiveProducts()
      ]);

      if (warnings.length || sameDay.length) {
        setConferenceReview({
          record,
          payload,
          warnings,
          sameDay,
          products
        });
        return;
      }

      setPrepared({ record, payload });
    } catch (cause) {
      setError(getPendingSyncErrorMessage(record, cause));
    } finally {
      setCheckingId(null);
    }
  };

  const continueEntryConflict = () => {
    if (!entryConflictReview) return;
    const position = entryConflictReview.positions.find(
      (item) => item.id === entryConflictReview.selectedPositionId
    );
    if (!position) return;

    setPrepared({
      record: entryConflictReview.record,
      payload: {
        ...entryConflictReview.payload,
        effectiveAt: position.effectiveAt
      }
    });
    setEntryConflictReview(null);
  };

  const continueConferenceReview = () => {
    if (!conferenceReview) return;
    setPrepared({
      record: conferenceReview.record,
      payload: conferenceReview.payload
    });
    setConferenceReview(null);
  };

  const sendPrepared = async () => {
    if (!prepared || !isOnline || sendingId) return;

    setSendingId(prepared.record.id);
    setError(null);
    setSuccess(null);

    try {
      if (prepared.record.kind === "entry") {
        await createEntry(prepared.payload as CreateEntryInput);
      } else if (isCategoryConference(prepared.payload)) {
        await createCategoryConference(prepared.payload);
      } else {
        await createProductConference(prepared.payload as ProductConferenceWriteInput);
      }

      await deleteOfflinePendingOperation(prepared.record.id);

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["entries"] }),
        queryClient.invalidateQueries({ queryKey: ["conferences"] }),
        queryClient.invalidateQueries({ queryKey: ["products"] }),
        queryClient.invalidateQueries({ queryKey: ["suppliers"] }),
        queryClient.invalidateQueries({ queryKey: ["stock"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] })
      ]);

      await Promise.allSettled([
        listActiveProducts(),
        listActiveSuppliers(),
        listCurrentStock(),
        listConferenceCategories()
      ]);

      setPrepared(null);
      setSuccess(
        prepared.record.kind === "entry"
          ? "Entrada confirmada e enviada ao estoque oficial."
          : "Conferência confirmada e enviada ao estoque oficial."
      );
    } catch (cause) {
      setError(getPendingSyncErrorMessage(prepared.record, cause));
    } finally {
      setSendingId(null);
    }
  };

  return (
    <AppShell title="Pendências locais" showBack backTo="/alertas">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Pendências offline</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          Operações preparadas sem internet ficam somente neste aparelho. Recuperar a conexão
          <strong> não envia nada automaticamente</strong>. Cada pendência precisa ser confirmada.
        </p>

        {!isOnline && items.length > 0 ? (
          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-950">
            Você continua offline. As pendências estão protegidas neste aparelho. Conecte-se para verificar o estado atual do servidor e confirmar um envio.
          </div>
        ) : null}

        {isOnline && items.length > 0 ? (
          <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm leading-6 text-emerald-950">
            Conexão disponível. As pendências continuam locais até você escolher <strong>Confirmar envio</strong>.
          </div>
        ) : null}

        {saved ? (
          <button
            type="button"
            onClick={clearSavedFlag}
            className="mt-5 w-full rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-left text-sm text-emerald-900"
          >
            {savedKind === "conference" ? "Conferência" : "Entrada"} salva como PENDENTE DE CONFIRMAÇÃO neste aparelho. Toque para dispensar esta mensagem.
          </button>
        ) : null}

        {success ? (
          <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            {success}
          </div>
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
            {visibleItems.map((item) => {
              const busy = checkingId === item.id || sendingId === item.id;
              return (
                <Card key={item.id} className="p-5">
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
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

                    <div className="flex flex-col gap-2 sm:flex-row lg:flex-col">
                      <Button
                        isLoading={checkingId === item.id}
                        loadingLabel="Verificando…"
                        disabled={!isOnline || busy}
                        onClick={() => void startConfirmation(item)}
                      >
                        Confirmar envio
                      </Button>
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => setPendingDelete(item)}
                      >
                        Excluir pendência
                      </Button>
                      <span className="px-1 text-center text-xs text-zinc-500">
                        Manter pendente: não faça nenhuma ação.
                      </span>
                    </div>
                  </div>
                </Card>
              );
            })}
          </div>
        ) : null}

        <div className="mt-5 rounded-xl border border-zinc-200 bg-zinc-100 px-4 py-3 text-xs leading-5 text-zinc-600">
          O envio usa a mesma chave de idempotência criada offline. Se o servidor tiver gravado a operação e a resposta se perder, uma nova tentativa não deve criar duplicidade. A pendência local só é removida depois da confirmação oficial do servidor.
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

        <ConfirmDialog
          open={Boolean(prepared)}
          title={
            prepared?.record.kind === "entry"
              ? "Enviar esta Entrada agora?"
              : "Enviar esta Conferência agora?"
          }
          description={
            <>
              O servidor já foi consultado. Ao confirmar, esta operação deixa de ser apenas local e passa a integrar o estoque oficial.
            </>
          }
          confirmLabel="Enviar agora"
          pendingLabel="Enviando…"
          cancelLabel="Manter pendente"
          isPending={Boolean(prepared && sendingId === prepared.record.id)}
          onCancel={() => setPrepared(null)}
          onConfirm={() => void sendPrepared()}
        />

        {entryConflictReview ? (
          <EntryConflictDialog
            review={entryConflictReview}
            onSelect={(selectedPositionId) =>
              setEntryConflictReview((current) =>
                current ? { ...current, selectedPositionId } : current
              )
            }
            onKeep={() => setEntryConflictReview(null)}
            onContinue={continueEntryConflict}
          />
        ) : null}

        {conferenceReview ? (
          <ConferenceReviewDialog
            review={conferenceReview}
            onKeep={() => setConferenceReview(null)}
            onContinue={continueConferenceReview}
          />
        ) : null}
      </section>
    </AppShell>
  );
}

function EntryConflictDialog({
  review,
  onSelect,
  onKeep,
  onContinue
}: {
  review: EntryConflictReview;
  onSelect: (id: string) => void;
  onKeep: () => void;
  onContinue: () => void;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="entry-conflict-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
    >
      <Card className="max-h-[90vh] w-full max-w-2xl overflow-y-auto p-5 shadow-xl">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-amber-700">
          Ordem do estoque
        </p>
        <h3 id="entry-conflict-title" className="mt-1 text-xl font-semibold text-zinc-950">
          Há Conferência relevante na mesma data
        </h3>
        <p className="mt-2 text-sm leading-6 text-zinc-600">
          A ordem entre esta Entrada e a Conferência pode alterar o estoque. Informe quando a Entrada aconteceu em relação às contagens abaixo.
        </p>

        <div className="mt-4 space-y-3">
          {review.conflicts.map((conflict) => (
            <div key={conflict.conferenceId} className="rounded-xl border border-amber-200 bg-amber-50 p-4">
              <p className="font-semibold text-zinc-950">
                Conferência em {formatDateTime(conflict.effectiveAt)}
              </p>
              <p className="mt-1 text-sm text-zinc-700">
                Responsável físico: <strong>{conflict.physicalResponsible}</strong>
              </p>
              <p className="mt-1 text-xs leading-5 text-zinc-600">
                Registrada por {conflict.registeredByLabel} · {conflict.deviceLabel}
              </p>
              <div className="mt-3 space-y-1 text-xs text-zinc-700">
                {conflict.items.map((item) => (
                  <p key={item.productId}>
                    <strong>{item.productName}</strong> · Entrada: {formatQuantity(item.entryQuantity)} · Conferência: {formatQuantity(item.conferenceQuantity)}
                  </p>
                ))}
              </div>
            </div>
          ))}
        </div>

        <fieldset className="mt-5">
          <legend className="text-sm font-semibold text-zinc-900">Posição da Entrada</legend>
          <div className="mt-2 space-y-2">
            {review.positions.map((position) => (
              <label
                key={position.id}
                className="flex cursor-pointer items-start gap-3 rounded-xl border border-zinc-200 px-4 py-3 hover:bg-zinc-50"
              >
                <input
                  type="radio"
                  name="entry-conflict-position"
                  checked={review.selectedPositionId === position.id}
                  onChange={() => onSelect(position.id)}
                  className="mt-1"
                />
                <span>
                  <span className="block text-sm font-semibold text-zinc-900">{position.label}</span>
                  <span className="mt-0.5 block text-xs leading-5 text-zinc-500">{position.description}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="ghost" onClick={onKeep}>Manter pendente</Button>
          <Button onClick={onContinue}>Continuar</Button>
        </div>
      </Card>
    </div>
  );
}

function ConferenceReviewDialog({
  review,
  onKeep,
  onContinue
}: {
  review: ConferenceReview;
  onKeep: () => void;
  onContinue: () => void;
}) {
  const productById = new Map(review.products.map((product) => [product.id, product] as const));

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="conference-review-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
    >
      <Card className="max-h-[90vh] w-full max-w-2xl overflow-y-auto p-5 shadow-xl">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-amber-700">
          Revisão online
        </p>
        <h3 id="conference-review-title" className="mt-1 text-xl font-semibold text-zinc-950">
          Esta Conferência precisa de atenção antes do envio
        </h3>

        {review.warnings.length > 0 ? (
          <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4">
            <p className="font-semibold text-zinc-950">Consumo fora do padrão</p>
            <p className="mt-1 text-sm leading-6 text-zinc-700">
              A revisão online encontrou {review.warnings.length} produto(s) que merecem conferência:
            </p>
            <div className="mt-2 space-y-1 text-sm text-zinc-700">
              {review.warnings.map((warning) => (
                <p key={warning.productId}>
                  • {productById.get(warning.productId)?.name ?? "Produto"}
                </p>
              ))}
            </div>
          </div>
        ) : null}

        {review.sameDay.length > 0 ? (
          <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4">
            <p className="font-semibold text-zinc-950">Já existe Conferência nesta data</p>
            <p className="mt-1 text-sm leading-6 text-zinc-700">
              Foram encontradas {review.sameDay.length} Conferência(s) desta categoria no mesmo dia.
            </p>
            <div className="mt-2 space-y-1 text-xs text-zinc-600">
              {review.sameDay.map((conference) => (
                <p key={conference.id}>
                  {formatDateTime(conference.effectiveAt)} · responsável físico: {conference.physicalResponsible}
                </p>
              ))}
            </div>
            <p className="mt-3 text-xs leading-5 text-zinc-600">
              Se esta contagem substitui uma anterior, mantenha a pendência e corrija a Conferência existente no módulo Conferência. Continue somente se esta foi realmente outra contagem física.
            </p>
          </div>
        ) : null}

        <p className="mt-4 text-sm leading-6 text-zinc-600">
          Continuar ainda não envia. Você verá uma confirmação final antes de a operação virar oficial.
        </p>

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="ghost" onClick={onKeep}>Manter pendente</Button>
          <Button onClick={onContinue}>Continuar para confirmação</Button>
        </div>
      </Card>
    </div>
  );
}

function isCategoryConference(
  payload: PendingPayload
): payload is CategoryConferenceWriteInput {
  return (
    typeof payload === "object" &&
    payload !== null &&
    "categoryId" in payload &&
    "items" in payload
  );
}

function getPendingSyncErrorMessage(
  record: OfflinePendingOperationRecord,
  cause: unknown
) {
  return record.kind === "entry"
    ? getEntryErrorMessage(cause)
    : getConferenceErrorMessage(cause);
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "data indisponível";
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(date);
}

function formatQuantity(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 3
  }).format(value);
}

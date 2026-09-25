import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useBlocker, useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { useCtrlEnter } from "../../../shared/hooks/useCtrlEnter";
import { handleDialogButtonArrowNavigation } from "../../../shared/lib/dialogKeyboard";
import { useAuth } from "../../auth/context/AuthContext";
import {
  createCategoryConference,
  getCategoryConferenceSetup,
  listSameDayCategoryConferences,
  type CategoryConferenceSetup,
  type CategoryConferenceWriteInput,
  type ConferenceHistoryItem
} from "../api/conferences";
import {
  buildConferenceEffectiveAt,
  formatConferenceTime,
  getConferenceErrorMessage,
  getFutureOperationalDateError,
  localDateInputValue,
  parseConferenceQuantity
} from "../lib/conferenceValidation";

export function NewCategoryConferencePage() {
  const { categoryId } = useParams();
  const setupQuery = useQuery({
    queryKey: ["conferences", "setup", categoryId],
    queryFn: () => {
      if (!categoryId) throw new Error("Categoria não encontrada.");
      return getCategoryConferenceSetup(categoryId);
    },
    enabled: Boolean(categoryId)
  });

  return (
    <AppShell title="Nova Conferência" showBack backTo="/conferencias/fazer">
      {setupQuery.isPending ? (
        <Card className="p-5 text-sm text-zinc-600">Carregando categoria…</Card>
      ) : null}
      {setupQuery.isError ? (
        <Card className="border-red-200 p-5 text-sm text-red-800">
          Não foi possível carregar esta categoria.
        </Card>
      ) : null}
      {setupQuery.data ? (
        <CategoryConferenceForm key={setupQuery.data.category.id} setup={setupQuery.data} />
      ) : null}
    </AppShell>
  );
}

function CategoryConferenceForm({ setup }: { setup: CategoryConferenceSetup }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();
  const [initialDate] = useState(() => localDateInputValue());
  const [date, setDate] = useState(initialDate);
  const [dateError, setDateError] = useState<string | null>(null);
  const [responsible, setResponsible] = useState("");
  const [observation, setObservation] = useState("");
  const [quantities, setQuantities] = useState<Record<string, string>>(() =>
    Object.fromEntries(setup.products.map((product) => [product.id, ""]))
  );
  const [quantityErrors, setQuantityErrors] = useState<Record<string, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [sameDayConferences, setSameDayConferences] = useState<ConferenceHistoryItem[]>([]);
  const [pendingPayload, setPendingPayload] = useState<CategoryConferenceWriteInput | null>(null);
  const [checkingSameDay, setCheckingSameDay] = useState(false);
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const allowNavigationRef = useRef(false);

  const dirty = useMemo(
    () =>
      Boolean(
        responsible.trim() ||
        observation.trim() ||
        date !== initialDate ||
        Object.values(quantities).some((value) => value.trim())
      ),
    [date, initialDate, observation, quantities, responsible]
  );

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty &&
      !allowNavigationRef.current &&
      currentLocation.pathname !== nextLocation.pathname
  );

  const saveMutation = useMutation({ mutationFn: createCategoryConference });

  const focusQuantity = (productId: string) => {
    window.setTimeout(() => document.getElementById(`conference-qty-${productId}`)?.focus(), 0);
  };

  const validateAndBuild = (): CategoryConferenceWriteInput => {
    setQuantityErrors({});
    const cleanResponsible = responsible.trim().replace(/\s+/g, " ");
    if (!cleanResponsible || cleanResponsible.length > 160) {
      document.getElementById("conference-responsible")?.focus();
      throw new Error("Responsável físico inválido.");
    }
    if (!deviceId) throw new Error("Este dispositivo ainda não está pronto para registrar Conferências.");
    if (!setup.products.length) throw new Error("A categoria não possui produtos ativos para conferir.");

    const errors: Record<string, string> = {};
    const items = setup.products.map((product) => {
      try {
        return {
          productId: product.id,
          quantity: parseConferenceQuantity(quantities[product.id] ?? "", `Quantidade de ${product.name}`)
        };
      } catch (error) {
        errors[product.id] =
          error instanceof Error ? error.message : "Quantidade inválida.";
        return { productId: product.id, quantity: 0 };
      }
    });

    if (Object.keys(errors).length) {
      setQuantityErrors(errors);
      const first = setup.products.find((product) => errors[product.id]);
      if (first) focusQuantity(first.id);
      throw new Error("Preencha todas as quantidades da categoria. Zero é um valor válido; campo em branco não é.");
    }

    return {
      categoryId: setup.category.id,
      effectiveAt: buildConferenceEffectiveAt(date),
      physicalResponsible: cleanResponsible,
      deviceId,
      idempotencyKey,
      observation: observation.trim() || null,
      items
    };
  };

  const persist = async (payload: CategoryConferenceWriteInput) => {
    setActionError(null);
    try {
      await saveMutation.mutateAsync(payload);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["conferences"] }),
        queryClient.invalidateQueries({ queryKey: ["products"] })
      ]);
      allowNavigationRef.current = true;
      navigate("/conferencias/fazer?saved=1", { replace: true });
    } catch (error) {
      setActionError(getConferenceErrorMessage(error));
    }
  };

  const requestSave = async () => {
    setActionError(null);
    let payload: CategoryConferenceWriteInput;
    try {
      payload = validateAndBuild();
    } catch (error) {
      setActionError(getConferenceErrorMessage(error));
      return;
    }

    setCheckingSameDay(true);
    try {
      const existing = await listSameDayCategoryConferences(setup.category.id, date);
      if (existing.length) {
        setPendingPayload(payload);
        setSameDayConferences(existing);
        return;
      }
      await persist(payload);
    } catch (error) {
      setActionError(getConferenceErrorMessage(error));
    } finally {
      setCheckingSameDay(false);
    }
  };

  useCtrlEnter(
    () => void requestSave(),
    blocker.state !== "blocked" &&
      !pendingPayload &&
      !saveMutation.isPending &&
      !checkingSameDay
  );

  const handleQuantityEnter = (
    event: KeyboardEvent<HTMLInputElement>,
    productIndex: number
  ) => {
    if (event.key !== "Enter" || event.ctrlKey || event.metaKey) return;
    event.preventDefault();
    const next = setup.products[productIndex + 1];
    if (next) focusQuantity(next.id);
    else document.getElementById("conference-observation")?.focus();
  };

  const latestSameDay = sameDayConferences[0] ?? null;

  return (
    <section className="pb-28">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
          Conferência por categoria
        </p>
        <h2 className="mt-1 text-2xl font-semibold tracking-tight">{setup.category.name}</h2>
        <p className="mt-1 text-sm text-zinc-600">
          Preencha todos os {setup.products.length} produtos. Campo vazio não significa zero.
        </p>
      </div>

      {actionError ? (
        <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {actionError}
        </div>
      ) : null}

      <Card className="mt-5 p-5">
        <div className="grid gap-4 md:grid-cols-2">
          <TextField
            id="conference-date"
            label="Data *"
            type="date"
            max={localDateInputValue()}
            value={date}
            autoFocus
            error={dateError}
            onChange={(event) => {
              const nextDate = event.target.value;
              setDate(nextDate);
              setDateError(getFutureOperationalDateError(nextDate));
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.ctrlKey && !event.metaKey) {
                event.preventDefault();
                const error = getFutureOperationalDateError(date);
                setDateError(error);
                if (error) {
                  document.getElementById("conference-date")?.focus();
                  return;
                }
                document.getElementById("conference-responsible")?.focus();
              }
            }}
          />
          <TextField
            id="conference-responsible"
            label="Responsável pela contagem física *"
            value={responsible}
            maxLength={160}
            onChange={(event) => setResponsible(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.ctrlKey && !event.metaKey) {
                event.preventDefault();
                const first = setup.products[0];
                if (first) focusQuantity(first.id);
              }
            }}
          />
        </div>
      </Card>

      <Card className="mt-4 overflow-hidden">
        <div className="border-b border-zinc-100 px-5 py-4">
          <h3 className="font-semibold">Contagem física</h3>
          <p className="mt-1 text-xs text-zinc-500">Enter avança para o próximo produto.</p>
        </div>
        <div className="divide-y divide-zinc-100">
          {setup.products.map((product, index) => (
            <div
              key={product.id}
              className="grid gap-3 px-5 py-4 md:grid-cols-[1fr_100px_180px] md:items-center"
            >
              <div>
                <p className="font-semibold text-zinc-900">{product.name}</p>
              </div>
              <div className="text-sm font-medium text-zinc-500">{product.unit}</div>
              <div>
                <label className="sr-only" htmlFor={`conference-qty-${product.id}`}>
                  Quantidade de {product.name}
                </label>
                <input
                  id={`conference-qty-${product.id}`}
                  inputMode="decimal"
                  placeholder="Quantidade"
                  value={quantities[product.id] ?? ""}
                  onChange={(event) => {
                    setQuantities((current) => ({ ...current, [product.id]: event.target.value }));
                    setQuantityErrors((current) => {
                      if (!current[product.id]) return current;
                      const next = { ...current };
                      delete next[product.id];
                      return next;
                    });
                  }}
                  onKeyDown={(event) => handleQuantityEnter(event, index)}
                  className={`min-h-11 w-full rounded-xl border bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100 ${
                    quantityErrors[product.id] ? "border-red-400" : "border-zinc-300"
                  }`}
                />
                {quantityErrors[product.id] ? (
                  <p className="mt-1 text-xs font-medium text-red-700">
                    {quantityErrors[product.id]}
                  </p>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card className="mt-4 p-5">
        <label className="block text-sm font-medium text-zinc-800" htmlFor="conference-observation">
          Observação
        </label>
        <textarea
          id="conference-observation"
          value={observation}
          maxLength={2000}
          onChange={(event) => setObservation(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== "Enter" || event.shiftKey || event.ctrlKey || event.metaKey) return;
            event.preventDefault();
            document.getElementById("conference-save")?.focus();
          }}
          rows={3}
          className="mt-2 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
          placeholder="Opcional para a Conferência inteira"
        />
      </Card>

      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-zinc-200 bg-white/95 px-4 py-3 backdrop-blur">
        <div className="mx-auto flex max-w-6xl justify-end">
          <Button
            id="conference-save"
            disabled={saveMutation.isPending || checkingSameDay}
            onClick={() => void requestSave()}
          >
            {saveMutation.isPending
              ? "Salvando…"
              : checkingSameDay
                ? "Verificando…"
                : "Salvar Conferência"}
          </Button>
        </div>
      </div>

      {pendingPayload && latestSameDay ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="same-day-conference-title"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
          onKeyDown={handleDialogButtonArrowNavigation}
        >
          <Card className="w-full max-w-lg p-5 shadow-xl">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-amber-700">
              Já existe Conferência nesta data
            </p>
            <h3 id="same-day-conference-title" className="mt-1 text-xl font-semibold text-zinc-950">
              {setup.category.name} já foi conferida
            </h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Existem {sameDayConferences.length} Conferência(s) nesta data. A mais recente foi às{" "}
              <strong>{formatConferenceTime(latestSameDay.effectiveAt)}</strong>, por{" "}
              <strong>{latestSameDay.physicalResponsible}</strong>.
            </p>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Corrija a existente se esta contagem substitui a anterior, ou registre uma nova se houve outra contagem física.
            </p>
            <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button
                autoFocus
                variant="ghost"
                onClick={() => {
                  setPendingPayload(null);
                  setSameDayConferences([]);
                }}
              >
                Cancelar
              </Button>
              <Button
                variant="secondary"
                onClick={() => {
                  allowNavigationRef.current = true;
                  navigate(`/conferencias/${latestSameDay.id}/editar`);
                }}
              >
                Corrigir existente
              </Button>
              <Button
                onClick={() => {
                  const payload = pendingPayload;
                  setPendingPayload(null);
                  setSameDayConferences([]);
                  void persist(payload);
                }}
              >
                Registrar nova
              </Button>
            </div>
          </Card>
        </div>
      ) : null}

      <ConfirmDialog
        open={blocker.state === "blocked"}
        title="Sair da Conferência?"
        description="As quantidades preenchidas ainda não foram salvas."
        confirmLabel="Descartar e sair"
        variant="warning"
        onCancel={() => blocker.reset?.()}
        onConfirm={() => {
          allowNavigationRef.current = true;
          blocker.proceed?.();
        }}
      />
    </section>
  );
}

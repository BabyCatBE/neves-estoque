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
  getConferenceDetails,
  updateCategoryConference,
  type ConferenceDetails
} from "../api/conferences";
import {
  buildEditedConferenceEffectiveAt,
  dateInputFromIso,
  getConferenceErrorMessage,
  parseConferenceQuantity
} from "../lib/conferenceValidation";

export function EditConferencePage() {
  const { conferenceId } = useParams();
  const conferenceQuery = useQuery({
    queryKey: ["conferences", "detail", conferenceId],
    queryFn: () => {
      if (!conferenceId) throw new Error("Conferência não encontrada.");
      return getConferenceDetails(conferenceId);
    },
    enabled: Boolean(conferenceId)
  });

  return (
    <AppShell title="Corrigir Conferência" showBack backTo={conferenceId ? `/conferencias/${conferenceId}` : "/conferencias/historico"}>
      {conferenceQuery.isPending ? (
        <Card className="p-5 text-sm text-zinc-600">Carregando Conferência…</Card>
      ) : null}
      {conferenceQuery.isError ? (
        <Card className="border-red-200 p-5 text-sm text-red-800">
          Não foi possível carregar esta Conferência.
        </Card>
      ) : null}
      {conferenceQuery.data ? (
        <ConferenceEditForm key={conferenceQuery.data.id} details={conferenceQuery.data} />
      ) : null}
    </AppShell>
  );
}

function ConferenceEditForm({ details }: { details: ConferenceDetails }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();
  const initialDate = dateInputFromIso(details.effectiveAt);
  const [date, setDate] = useState(initialDate);
  const [responsible, setResponsible] = useState(details.physicalResponsible);
  const [observation, setObservation] = useState(details.observation ?? "");
  const [quantities, setQuantities] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      details.items.map((item) => [
        item.productId,
        String(item.quantity).replace(".", ",")
      ])
    )
  );
  const [quantityErrors, setQuantityErrors] = useState<Record<string, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const allowNavigationRef = useRef(false);

  const dirty = useMemo(() => {
    if (date !== initialDate) return true;
    if (responsible !== details.physicalResponsible) return true;
    if (observation !== (details.observation ?? "")) return true;
    return details.items.some(
      (item) => (quantities[item.productId] ?? "") !== String(item.quantity).replace(".", ",")
    );
  }, [date, details, initialDate, observation, quantities, responsible]);

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty &&
      !allowNavigationRef.current &&
      currentLocation.pathname !== nextLocation.pathname
  );

  const updateMutation = useMutation({ mutationFn: updateCategoryConference });

  const buildPayload = () => {
    setQuantityErrors({});
    const cleanResponsible = responsible.trim().replace(/\s+/g, " ");
    if (!cleanResponsible || cleanResponsible.length > 160) {
      document.getElementById("conference-edit-responsible")?.focus();
      throw new Error("Responsável físico inválido.");
    }
    if (!deviceId) throw new Error("Este dispositivo ainda não está pronto para corrigir Conferências.");

    const errors: Record<string, string> = {};
    const items = details.items.map((item) => {
      try {
        return {
          productId: item.productId,
          quantity: parseConferenceQuantity(
            quantities[item.productId] ?? "",
            `Quantidade de ${item.productName}`
          )
        };
      } catch (error) {
        errors[item.productId] = error instanceof Error ? error.message : "Quantidade inválida.";
        return { productId: item.productId, quantity: 0 };
      }
    });

    if (Object.keys(errors).length) {
      setQuantityErrors(errors);
      const first = details.items.find((item) => errors[item.productId]);
      if (first) {
        window.setTimeout(
          () => document.getElementById(`conference-edit-qty-${first.productId}`)?.focus(),
          0
        );
      }
      throw new Error("Preencha todas as quantidades antes de salvar.");
    }

    return {
      conferenceId: details.id,
      effectiveAt: buildEditedConferenceEffectiveAt(date, details.effectiveAt),
      physicalResponsible: cleanResponsible,
      deviceId,
      observation: observation.trim() || null,
      items
    };
  };

  const requestReview = () => {
    setActionError(null);
    try {
      buildPayload();
      setReviewOpen(true);
    } catch (error) {
      setActionError(getConferenceErrorMessage(error));
    }
  };

  const confirmSave = async () => {
    setActionError(null);
    try {
      const payload = buildPayload();
      await updateMutation.mutateAsync(payload);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["conferences"] }),
        queryClient.invalidateQueries({ queryKey: ["products"] })
      ]);
      allowNavigationRef.current = true;
      navigate(`/conferencias/${details.id}`, { replace: true });
    } catch (error) {
      setReviewOpen(false);
      setActionError(getConferenceErrorMessage(error));
    }
  };

  useCtrlEnter(
    requestReview,
    dirty && !reviewOpen && blocker.state !== "blocked" && !updateMutation.isPending
  );

  const focusQuantity = (productId: string) => {
    window.setTimeout(
      () => document.getElementById(`conference-edit-qty-${productId}`)?.focus(),
      0
    );
  };

  const handleQuantityEnter = (
    event: KeyboardEvent<HTMLInputElement>,
    productIndex: number
  ) => {
    if (event.key !== "Enter" || event.ctrlKey || event.metaKey) return;
    event.preventDefault();
    const next = details.items[productIndex + 1];
    if (next) focusQuantity(next.productId);
    else document.getElementById("conference-edit-observation")?.focus();
  };

  const changes = [
    ...(date !== initialDate ? ["Data da Conferência"] : []),
    ...(responsible !== details.physicalResponsible ? ["Responsável"] : []),
    ...(observation !== (details.observation ?? "") ? ["Observação"] : []),
    ...details.items
      .filter(
        (item) =>
          (quantities[item.productId] ?? "") !== String(item.quantity).replace(".", ",")
      )
      .map((item) => item.productName)
  ];

  return (
    <section className="pb-24">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
          {details.categoryName}
        </p>
        <h2 className="mt-1 text-2xl font-semibold">Correção histórica</h2>
        <p className="mt-1 text-sm text-zinc-600">
          O mesmo registro será corrigido e a auditoria preservará antes/depois.
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
            id="conference-edit-date"
            label="Data *"
            type="date"
            value={date}
            autoFocus
            onChange={(event) => setDate(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.ctrlKey && !event.metaKey) {
                event.preventDefault();
                document.getElementById("conference-edit-responsible")?.focus();
              }
            }}
          />
          <TextField
            id="conference-edit-responsible"
            label="Responsável pela contagem física *"
            value={responsible}
            maxLength={160}
            onChange={(event) => setResponsible(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.ctrlKey && !event.metaKey) {
                event.preventDefault();
                const first = details.items[0];
                if (first) focusQuantity(first.productId);
              }
            }}
          />
        </div>
      </Card>

      <Card className="mt-4 overflow-hidden">
        <div className="border-b border-zinc-100 px-5 py-4">
          <h3 className="font-semibold">Quantidades</h3>
        </div>
        <div className="divide-y divide-zinc-100">
          {details.items.map((item, index) => (
            <div
              key={item.productId}
              className="grid gap-3 px-5 py-4 md:grid-cols-[1fr_100px_180px] md:items-center"
            >
              <p className="font-semibold">{item.productName}</p>
              <p className="text-sm text-zinc-500">{item.unit}</p>
              <div>
                <input
                  id={`conference-edit-qty-${item.productId}`}
                  inputMode="decimal"
                  value={quantities[item.productId] ?? ""}
                  onChange={(event) => {
                    setQuantities((current) => ({ ...current, [item.productId]: event.target.value }));
                    setQuantityErrors((current) => {
                      if (!current[item.productId]) return current;
                      const next = { ...current };
                      delete next[item.productId];
                      return next;
                    });
                  }}
                  onKeyDown={(event) => handleQuantityEnter(event, index)}
                  className={`min-h-11 w-full rounded-xl border bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100 ${
                    quantityErrors[item.productId] ? "border-red-400" : "border-zinc-300"
                  }`}
                />
                {quantityErrors[item.productId] ? (
                  <p className="mt-1 text-xs font-medium text-red-700">
                    {quantityErrors[item.productId]}
                  </p>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card className="mt-4 p-5">
        <label className="block text-sm font-medium text-zinc-800" htmlFor="conference-edit-observation">
          Observação
        </label>
        <textarea
          id="conference-edit-observation"
          value={observation}
          maxLength={2000}
          rows={3}
          onChange={(event) => setObservation(event.target.value)}
          className="mt-2 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
        />
      </Card>

      <div className="mt-5 flex justify-end">
        <Button disabled={!dirty || updateMutation.isPending} onClick={requestReview}>
          Salvar correção
        </Button>
      </div>

      {reviewOpen ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="conference-review-title"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
          onKeyDown={handleDialogButtonArrowNavigation}
        >
          <Card className="w-full max-w-lg p-5 shadow-xl">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
              Revisão da correção
            </p>
            <h3 id="conference-review-title" className="mt-1 text-xl font-semibold">
              Salvar alterações nesta Conferência?
            </h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              O registro manterá o mesmo ID e a auditoria guardará o antes/depois.
            </p>
            <div className="mt-4 rounded-xl bg-zinc-50 p-4 text-sm text-zinc-700">
              {changes.map((change) => (
                <p key={change}>• {change}</p>
              ))}
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <Button autoFocus variant="ghost" onClick={() => setReviewOpen(false)}>
                Continuar editando
              </Button>
              <Button disabled={updateMutation.isPending} onClick={() => void confirmSave()}>
                {updateMutation.isPending ? "Salvando…" : "Confirmar correção"}
              </Button>
            </div>
          </Card>
        </div>
      ) : null}

      <ConfirmDialog
        open={blocker.state === "blocked"}
        title="Sair da correção?"
        description="Existem alterações não salvas nesta Conferência."
        confirmLabel="Descartar alterações"
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

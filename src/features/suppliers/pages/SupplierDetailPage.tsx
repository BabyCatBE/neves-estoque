import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type SelectHTMLAttributes } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { useCtrlEnter } from "../../../shared/hooks/useCtrlEnter";
import { handleDialogButtonArrowNavigation } from "../../../shared/lib/dialogKeyboard";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { getSupplierDetails, softDeleteSupplier, updateSupplier, type SupplierDetails } from "../api/suppliers";
import {
  formatSupplierPhoneDisplay,
  formatSupplierPhoneForEdit,
  formatSupplierPhoneInput,
  getSupplierErrorMessage,
  parseOptionalInteger,
  SUPPLIER_WEEKDAYS,
  supplierCompanySchema,
  supplierNameSchema,
  supplierObservationSchema,
  supplierPhoneSchema,
  weekdayLabel
} from "../lib/supplierValidation";

type ExitReview = "back" | "cancel" | null;
type EditDraft = {
  name: string;
  company: string;
  phone: string;
  observation: string;
  purchaseFrequencyDays: string;
  preferredOrderWeekday: string;
  averageDeliveryDays: string;
  safetyMarginDays: string;
};
type DraftErrors = Partial<Record<keyof EditDraft, string>>;

export function SupplierDetailPage() {
  const { supplierId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<EditDraft | null>(null);
  const [exitReview, setExitReview] = useState<ExitReview>(null);
  const [deleteReviewOpen, setDeleteReviewOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<DraftErrors>({});

  const supplierQuery = useQuery({
    queryKey: ["suppliers", "detail", supplierId],
    queryFn: () => {
      if (!supplierId) throw new Error("Fornecedor inválido.");
      return getSupplierDetails(supplierId);
    },
    enabled: Boolean(supplierId)
  });

  const updateMutation = useMutation({
    mutationFn: updateSupplier,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["suppliers"] }),
        queryClient.invalidateQueries({ queryKey: ["trash", "restorable"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] })
      ]);
    }
  });

  const deleteMutation = useMutation({
    mutationFn: softDeleteSupplier,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["suppliers"] }),
        queryClient.invalidateQueries({ queryKey: ["trash", "restorable"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] })
      ]);
    }
  });

  const dirty = useMemo(() => {
    if (!draft || !supplierQuery.data) return false;
    return JSON.stringify(draft) !== JSON.stringify(toDraft(supplierQuery.data));
  }, [draft, supplierQuery.data]);

  const changeSummary = useMemo(() => {
    if (!draft || !supplierQuery.data) return [];
    const before = toDraft(supplierQuery.data);
    const items: Array<{ label: string; before: string; after: string }> = [];
    addChange(items, "Contato / vendedor", before.name, draft.name);
    addChange(items, "Empresa", before.company, draft.company);
    addChange(items, "Telefone", before.phone, draft.phone);
    addChange(items, "Frequência", displayInput(before.purchaseFrequencyDays, "Não informada"), displayInput(draft.purchaseFrequencyDays, "Não informada"));
    addChange(items, "Dia preferencial", weekdayInputLabel(before.preferredOrderWeekday), weekdayInputLabel(draft.preferredOrderWeekday));
    addChange(items, "Prazo de entrega", displayDays(before.averageDeliveryDays), displayDays(draft.averageDeliveryDays));
    addChange(items, "Margem de segurança", displayDays(before.safetyMarginDays), displayDays(draft.safetyMarginDays));
    addChange(items, "Observação", before.observation || "Sem observação", draft.observation || "Sem observação");
    return items;
  }, [draft, supplierQuery.data]);

  const startEditing = () => {
    if (!supplierQuery.data) return;
    setNotice(null);
    setActionError(null);
    setFieldErrors({});
    setDraft(toDraft(supplierQuery.data));
    setEditing(true);
  };

  const requestBack = () => {
    if (editing && dirty) {
      setExitReview("back");
      return;
    }
    navigate("/fornecedores");
  };

  const cancelEditing = () => {
    if (dirty) {
      setExitReview("cancel");
      return;
    }
    setDraft(null);
    setEditing(false);
    setActionError(null);
    setFieldErrors({});
  };

  const updateDraftField = (field: keyof EditDraft, value: string) => {
    setDraft((current) => (current ? { ...current, [field]: value } : current));
    setFieldErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
    setActionError(null);
  };

  const focusFirstFieldError = (errors: DraftErrors) => {
    const order: Array<keyof EditDraft> = [
      "name",
      "company",
      "phone",
      "purchaseFrequencyDays",
      "preferredOrderWeekday",
      "averageDeliveryDays",
      "safetyMarginDays",
      "observation"
    ];
    const first = order.find((field) => errors[field]);
    if (!first) return;
    window.setTimeout(() => {
      document.getElementById(`supplier-${first}`)?.focus();
    }, 0);
  };

  const validateDraft = () => {
    if (!draft) return null;

    const errors: DraftErrors = {};
    const name = supplierNameSchema.safeParse(draft.name);
    const company = supplierCompanySchema.safeParse(draft.company);
    const phone = supplierPhoneSchema.safeParse(draft.phone);
    const observation = supplierObservationSchema.safeParse(draft.observation);

    if (!name.success) errors.name = name.error.issues[0]?.message ?? "Informe o contato.";
    if (!company.success) errors.company = company.error.issues[0]?.message ?? "Informe a empresa.";
    if (!phone.success) errors.phone = phone.error.issues[0]?.message ?? "Informe um telefone válido.";
    if (!observation.success) errors.observation = observation.error.issues[0]?.message ?? "Observação inválida.";

    let purchaseFrequencyDays: number | null = null;
    let preferredOrderWeekday: number | null = null;
    let averageDeliveryDays: number | null = null;
    let safetyMarginDays: number | null = null;

    try {
      purchaseFrequencyDays = parseOptionalInteger(
        draft.purchaseFrequencyDays,
        "Frequência de compra",
        1,
        3650
      );
    } catch (error) {
      errors.purchaseFrequencyDays = getSupplierErrorMessage(error);
    }

    try {
      preferredOrderWeekday = parseOptionalInteger(
        draft.preferredOrderWeekday,
        "Dia preferencial",
        1,
        7
      );
    } catch (error) {
      errors.preferredOrderWeekday = getSupplierErrorMessage(error);
    }

    try {
      averageDeliveryDays = parseOptionalInteger(
        draft.averageDeliveryDays,
        "Prazo de entrega",
        0,
        365
      );
    } catch (error) {
      errors.averageDeliveryDays = getSupplierErrorMessage(error);
    }

    try {
      safetyMarginDays = parseOptionalInteger(
        draft.safetyMarginDays,
        "Margem de segurança",
        0,
        365
      );
    } catch (error) {
      errors.safetyMarginDays = getSupplierErrorMessage(error);
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      focusFirstFieldError(errors);
      return null;
    }

    setFieldErrors({});
    return {
      name: name.success ? name.data : draft.name,
      company: company.success ? company.data : draft.company,
      phone: phone.success ? phone.data : draft.phone,
      observation: observation.success ? observation.data.trim() || null : null,
      purchaseFrequencyDays,
      preferredOrderWeekday,
      averageDeliveryDays,
      safetyMarginDays
    };
  };

  const save = async (goBackAfterSave = false) => {
    if (!supplierId || !draft) return;
    setNotice(null);
    setActionError(null);
    try {
      const values = validateDraft();
      if (!values) return;
      await updateMutation.mutateAsync({ id: supplierId, ...values });
      setEditing(false);
      setDraft(null);
      setExitReview(null);
      setFieldErrors({});
      setNotice("Fornecedor atualizado com sucesso.");
      if (goBackAfterSave) navigate("/fornecedores?deleted=1");
    } catch (error) {
      setExitReview(null);
      setActionError(getSupplierErrorMessage(error));
    }
  };

  useCtrlEnter(
    () => {
      if (editing && dirty && !updateMutation.isPending) void save();
    },
    editing &&
      dirty &&
      !updateMutation.isPending &&
      !exitReview &&
      !deleteReviewOpen
  );

  const discardChanges = () => {
    const action = exitReview;
    setExitReview(null);
    setDraft(null);
    setEditing(false);
    setActionError(null);
    setFieldErrors({});
    if (action === "back") navigate("/fornecedores");
  };

  const confirmDeleteSupplier = async () => {
    if (!supplierId) return;
    setActionError(null);
    try {
      await deleteMutation.mutateAsync(supplierId);
      setDeleteReviewOpen(false);
      navigate("/fornecedores");
    } catch (error) {
      setActionError(getSupplierErrorMessage(error));
    }
  };

  return (
    <AppShell title="Fornecedor" showBack onBack={requestBack}>
      <section>
        {supplierQuery.isPending ? <Card className="p-5 text-sm text-zinc-600">Carregando fornecedor…</Card> : null}

        {supplierQuery.isError ? (
          <Card className="border-red-200 p-5">
            <p className="text-sm font-medium text-red-800">Não foi possível carregar este fornecedor.</p>
            <Button className="mt-4" variant="secondary" onClick={() => void supplierQuery.refetch()}>Tentar novamente</Button>
          </Card>
        ) : null}

        {supplierQuery.data ? (
          <>
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Cadastro de fornecedor</p>
                  {supplierQuery.data.isPending ? <span className="rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-semibold text-amber-800">Cadastro pendente</span> : null}
                </div>
                <h2 className="mt-1 text-2xl font-semibold tracking-tight text-zinc-950">{supplierQuery.data.name}</h2>
                <p className="mt-1 text-sm text-zinc-500">{supplierQuery.data.company ?? "Empresa não informada"}</p>
              </div>

              {!editing ? (
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" className="text-red-700" disabled={deleteMutation.isPending} onClick={() => setDeleteReviewOpen(true)}>Excluir fornecedor</Button>
                  <Button onClick={startEditing}>Editar fornecedor</Button>
                </div>
              ) : null}
            </div>

            {supplierQuery.data.isPending && !editing ? <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900">Este fornecedor veio de um cadastro rápido ou está incompleto. Preencha Empresa e Telefone para concluir o cadastro.</div> : null}
            {notice ? <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</div> : null}
            {actionError ? <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{actionError}</div> : null}

            {editing && draft ? (
              <Card className="mt-5 p-5">
                <div className="grid gap-4 lg:grid-cols-3">
                  <TextField id="supplier-name" label="Contato / vendedor" value={draft.name} autoFocus error={fieldErrors.name} onChange={(event) => updateDraftField("name", event.target.value)} />
                  <TextField id="supplier-company" label="Empresa" value={draft.company} error={fieldErrors.company} onChange={(event) => updateDraftField("company", event.target.value)} />
                  <TextField
                    id="supplier-phone"
                    label="Telefone"
                    value={draft.phone}
                    error={fieldErrors.phone}
                    inputMode="tel"
                    maxLength={16}
                    placeholder="(75) 9 9999-9999"
                    onChange={(event) => updateDraftField("phone", formatSupplierPhoneInput(event.target.value))}
                  />
                </div>

                <details open className="mt-4 rounded-xl border border-zinc-200 bg-zinc-50 p-4">
                  <summary className="cursor-pointer text-sm font-semibold text-zinc-800">Mais detalhes</summary>
                  <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                    <TextField id="supplier-purchaseFrequencyDays" label="Frequência de compra (dias)" inputMode="numeric" value={draft.purchaseFrequencyDays} error={fieldErrors.purchaseFrequencyDays} onChange={(event) => updateDraftField("purchaseFrequencyDays", event.target.value)} />
                    <SelectField id="supplier-preferredOrderWeekday" label="Dia preferencial" value={draft.preferredOrderWeekday} error={fieldErrors.preferredOrderWeekday} onChange={(event) => updateDraftField("preferredOrderWeekday", event.target.value)}>
                      <option value="">Não informado</option>
                      {SUPPLIER_WEEKDAYS.map((day) => <option key={day.value} value={day.value}>{day.label}</option>)}
                    </SelectField>
                    <TextField id="supplier-averageDeliveryDays" label="Prazo de entrega (dias)" inputMode="numeric" value={draft.averageDeliveryDays} error={fieldErrors.averageDeliveryDays} onChange={(event) => updateDraftField("averageDeliveryDays", event.target.value)} />
                    <TextField id="supplier-safetyMarginDays" label="Margem de segurança (dias)" inputMode="numeric" value={draft.safetyMarginDays} error={fieldErrors.safetyMarginDays} onChange={(event) => updateDraftField("safetyMarginDays", event.target.value)} />
                  </div>

                  <label className="mt-4 block">
                    <span className="text-sm font-medium text-zinc-800">Observação</span>
                    <textarea
                      id="supplier-observation"
                      rows={3}
                      value={draft.observation}
                      aria-invalid={fieldErrors.observation ? true : undefined}
                      onChange={(event) => updateDraftField("observation", event.target.value)}
                      className={`mt-2 w-full rounded-xl border bg-white px-3 py-2.5 text-sm text-zinc-900 outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100 ${fieldErrors.observation ? "border-red-400" : "border-zinc-300"}`}
                    />
                    {fieldErrors.observation ? (
                      <span className="mt-1.5 block text-xs font-medium text-red-700">{fieldErrors.observation}</span>
                    ) : null}
                  </label>
                </details>

                <div className="mt-5 flex flex-wrap justify-end gap-2">
                  <Button variant="ghost" disabled={updateMutation.isPending} onClick={cancelEditing}>Cancelar</Button>
                  <Button disabled={!dirty} isLoading={updateMutation.isPending} loadingLabel="Salvando…" onClick={() => void save()}>Salvar alterações</Button>
                </div>
              </Card>
            ) : (
              <>
                <div className="mt-5 grid gap-4 md:grid-cols-3">
                  <MetricCard label="Empresa" value={supplierQuery.data.company ?? "Não informada"} helper={supplierQuery.data.company ? "Empresa vinculada a este contato." : "Campo obrigatório pendente."} />
                  <MetricCard
                    label="Telefone"
                    value={formatSupplierPhoneDisplay(supplierQuery.data.phone)}
                    helper={supplierQuery.data.isPending ? "Telefone ausente ou fora do padrão de 11 dígitos." : "Contato principal do fornecedor."}
                  />
                  <MetricCard label="Próxima compra" value="Aguardando Entradas" helper="A recomendação será calculada quando existir histórico real de recebimentos." />
                </div>

                <Card className="mt-5 p-5">
                  <details>
                    <summary className="cursor-pointer font-semibold text-zinc-900">Mais detalhes</summary>
                    <dl className="mt-4 space-y-3 text-sm">
                      <DetailRow label="Frequência de compra" value={supplierQuery.data.purchaseFrequencyDays === null ? "Não informada" : `A cada ${supplierQuery.data.purchaseFrequencyDays} dias`} />
                      <DetailRow label="Dia preferencial" value={weekdayLabel(supplierQuery.data.preferredOrderWeekday)} />
                      <DetailRow label="Prazo de entrega" value={formatDays(supplierQuery.data.averageDeliveryDays)} />
                      <DetailRow label="Margem de segurança" value={formatDays(supplierQuery.data.safetyMarginDays)} />
                      <DetailRow label="Observação" value={supplierQuery.data.observation ?? "Sem observação"} />
                    </dl>
                  </details>
                </Card>
              </>
            )}

            <ConfirmDialog
              open={deleteReviewOpen}
              variant="danger"
              title="Excluir fornecedor?"
              description={`Excluir o fornecedor “${supplierQuery.data.name}”? Ele sairá do cadastro ativo, mas o histórico de Entradas continuará preservado. Poderá ser restaurado pela lixeira por 7 dias.`}
              confirmLabel="Excluir fornecedor"
              pendingLabel="Excluindo…"
              isPending={deleteMutation.isPending}
              onCancel={() => setDeleteReviewOpen(false)}
              onConfirm={() => void confirmDeleteSupplier()}
            />

            {exitReview ? (
              <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="supplier-change-summary-title"
                className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
                onKeyDown={handleDialogButtonArrowNavigation}
              >
                <Card className="w-full max-w-lg p-5 shadow-xl">
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Alterações não salvas</p>
                  <h3 id="supplier-change-summary-title" className="mt-1 text-xl font-semibold text-zinc-950">Revise antes de sair</h3>
                  <p className="mt-2 text-sm leading-6 text-zinc-600">Escolha se deseja salvar, descartar ou continuar editando este fornecedor.</p>

                  <div className="mt-4 max-h-72 space-y-3 overflow-auto rounded-xl bg-zinc-50 p-4">
                    {changeSummary.map((change) => (
                      <div key={change.label} className="text-sm">
                        <p className="font-semibold text-zinc-800">{change.label}</p>
                        <p className="mt-1 break-words text-zinc-500">{change.before} <span aria-hidden="true">→</span>{" "}<span className="font-medium text-zinc-900">{change.after}</span></p>
                      </div>
                    ))}
                  </div>

                  <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                    <Button autoFocus variant="ghost" disabled={updateMutation.isPending} onClick={() => setExitReview(null)}>Continuar editando</Button>
                    <Button variant="secondary" disabled={updateMutation.isPending} onClick={discardChanges}>Descartar alterações</Button>
                    <Button isLoading={updateMutation.isPending} loadingLabel="Salvando…" onClick={() => void save(exitReview === "back")}>Salvar alterações</Button>
                  </div>
                </Card>
              </div>
            ) : null}
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function toDraft(supplier: SupplierDetails): EditDraft {
  return {
    name: supplier.name,
    company: supplier.company ?? "",
    phone: formatSupplierPhoneForEdit(supplier.phone),
    observation: supplier.observation ?? "",
    purchaseFrequencyDays: numberToInput(supplier.purchaseFrequencyDays),
    preferredOrderWeekday: numberToInput(supplier.preferredOrderWeekday),
    averageDeliveryDays: numberToInput(supplier.averageDeliveryDays),
    safetyMarginDays: numberToInput(supplier.safetyMarginDays)
  };
}

function addChange(items: Array<{ label: string; before: string; after: string }>, label: string, before: string, after: string) {
  if (before.trim() !== after.trim()) items.push({ label, before: before || "Não informado", after: after || "Não informado" });
}

function numberToInput(value: number | null) {
  return value === null ? "" : String(value);
}

function displayInput(value: string, empty: string) {
  return value.trim() || empty;
}

function displayDays(value: string) {
  return value.trim() ? `${value.trim()} dias` : "Não informado";
}

function weekdayInputLabel(value: string) {
  return value ? weekdayLabel(Number(value)) : "Não informado";
}

function formatDays(value: number | null) {
  if (value === null) return "Não informado";
  return `${value} ${value === 1 ? "dia" : "dias"}`;
}

function MetricCard({ label, value, helper }: { label: string; value: string; helper: string }) {
  return <Card className="p-5"><p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">{label}</p><p className="mt-2 break-words text-lg font-semibold text-zinc-950">{value}</p><p className="mt-2 text-xs leading-5 text-zinc-500">{helper}</p></Card>;
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return <div className="flex items-start justify-between gap-4 border-b border-zinc-100 pb-3 last:border-0 last:pb-0"><dt className="text-zinc-500">{label}</dt><dd className="max-w-[65%] whitespace-pre-wrap text-right font-medium text-zinc-900">{value}</dd></div>;
}

function SelectField({
  label,
  error,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string | null }) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-zinc-800">{label}</span>
      <select
        aria-invalid={error ? true : props["aria-invalid"]}
        className={`mt-2 min-h-11 w-full rounded-xl border bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100 ${error ? "border-red-400" : "border-zinc-300"}`}
        {...props}
      >
        {children}
      </select>
      {error ? <span className="mt-1.5 block text-xs font-medium text-red-700">{error}</span> : null}
    </label>
  );
}

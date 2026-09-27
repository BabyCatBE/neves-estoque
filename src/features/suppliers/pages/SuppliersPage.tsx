import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type SelectHTMLAttributes } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { useCtrlEnter } from "../../../shared/hooks/useCtrlEnter";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { TextField } from "../../../shared/components/ui/TextField";
import { SearchClearButton } from "../../../shared/components/ui/SearchClearButton";
import { normalizeSearchText } from "../../../shared/lib/searchText";
import { createSupplier, listActiveSuppliers } from "../api/suppliers";
import {
  formatSupplierPhoneDisplay,
  formatSupplierPhoneInput,
  getSupplierErrorMessage,
  parseOptionalInteger,
  SUPPLIER_WEEKDAYS,
  supplierCompanySchema,
  supplierNameSchema,
  supplierObservationSchema,
  supplierPhoneSchema
} from "../lib/supplierValidation";

type SupplierForm = {
  name: string;
  company: string;
  phone: string;
  observation: string;
  purchaseFrequencyDays: string;
  preferredOrderWeekday: string;
  averageDeliveryDays: string;
  safetyMarginDays: string;
};

const suppliersKey = ["suppliers", "active"] as const;
const emptyForm: SupplierForm = {
  name: "",
  company: "",
  phone: "",
  observation: "",
  purchaseFrequencyDays: "",
  preferredOrderWeekday: "",
  averageDeliveryDays: "",
  safetyMarginDays: ""
};

export function SuppliersPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [creating, setCreating] = useState(false);
  const [search, setSearch] = useState("");
  const pendingOnly = searchParams.get("filter") === "pending";
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (searchParams.get("deleted") !== "1") return;
    setNotice("Fornecedor enviado para a lixeira.");
    const next = new URLSearchParams(searchParams);
    next.delete("deleted");
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const suppliersQuery = useQuery({ queryKey: suppliersKey, queryFn: listActiveSuppliers });

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors }
  } = useForm<SupplierForm>({ defaultValues: emptyForm });

  const phoneField = register("phone");

  const createMutation = useMutation({
    mutationFn: createSupplier,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: suppliersKey }),
        queryClient.invalidateQueries({ queryKey: ["trash", "restorable"] })
      ]);
    }
  });

  const filteredSuppliers = useMemo(() => {
    const term = normalizeSearchText(search);
    return (suppliersQuery.data ?? []).filter((supplier) => {
      if (pendingOnly && !supplier.isPending) return false;
      if (!term) return true;
      return [supplier.name, supplier.company ?? "", supplier.phone ?? ""].some((value) =>
        normalizeSearchText(value).includes(term)
      );
    });
  }, [pendingOnly, search, suppliersQuery.data]);

  const onCreate = handleSubmit(async (values) => {
    setNotice(null);
    setActionError(null);

    const name = supplierNameSchema.safeParse(values.name);
    const company = supplierCompanySchema.safeParse(values.company);
    const phone = supplierPhoneSchema.safeParse(values.phone);
    const observation = supplierObservationSchema.safeParse(values.observation);

    if (!name.success) {
      setError("name", { message: name.error.issues[0]?.message }, { shouldFocus: true });
      return;
    }
    if (!company.success) {
      setError("company", { message: company.error.issues[0]?.message }, { shouldFocus: true });
      return;
    }
    if (!phone.success) {
      setError("phone", { message: phone.error.issues[0]?.message }, { shouldFocus: true });
      return;
    }
    if (!observation.success) {
      setError("observation", { message: observation.error.issues[0]?.message }, { shouldFocus: true });
      return;
    }

    let purchaseFrequencyDays: number | null;
    let preferredOrderWeekday: number | null;
    let averageDeliveryDays: number | null;
    let safetyMarginDays: number | null;

    try {
      purchaseFrequencyDays = parseOptionalInteger(
        values.purchaseFrequencyDays,
        "Frequência de compra",
        1,
        3650
      );
    } catch (error) {
      setError(
        "purchaseFrequencyDays",
        { message: getSupplierErrorMessage(error) },
        { shouldFocus: true }
      );
      return;
    }

    try {
      preferredOrderWeekday = parseOptionalInteger(
        values.preferredOrderWeekday,
        "Dia preferencial",
        1,
        7
      );
    } catch (error) {
      setError(
        "preferredOrderWeekday",
        { message: getSupplierErrorMessage(error) },
        { shouldFocus: true }
      );
      return;
    }

    try {
      averageDeliveryDays = parseOptionalInteger(
        values.averageDeliveryDays,
        "Prazo de entrega",
        0,
        365
      );
    } catch (error) {
      setError(
        "averageDeliveryDays",
        { message: getSupplierErrorMessage(error) },
        { shouldFocus: true }
      );
      return;
    }

    try {
      safetyMarginDays = parseOptionalInteger(
        values.safetyMarginDays,
        "Margem de segurança",
        0,
        365
      );
    } catch (error) {
      setError(
        "safetyMarginDays",
        { message: getSupplierErrorMessage(error) },
        { shouldFocus: true }
      );
      return;
    }

    try {
      await createMutation.mutateAsync({
        name: name.data,
        company: company.data,
        phone: phone.data,
        observation: observation.data.trim() || null,
        purchaseFrequencyDays,
        preferredOrderWeekday,
        averageDeliveryDays,
        safetyMarginDays
      });

      reset(emptyForm);
      setCreating(false);
      setNotice("Fornecedor criado com sucesso.");
    } catch (error) {
      setActionError(getSupplierErrorMessage(error));
    }
  });

  useCtrlEnter(
    () => {
      if (creating && !createMutation.isPending) void onCreate();
    },
    creating && !createMutation.isPending
  );

  return (
    <AppShell title="Fornecedores" showBack backTo="/">
      <section>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Fornecedores</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
              Cadastre contatos, empresas e parâmetros que serão usados nas Entradas e nas futuras recomendações de compra.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Link
              to="/alertas/lixeira"
              className="inline-flex min-h-11 items-center justify-center rounded-xl border border-red-200 bg-white px-4 py-2.5 text-sm font-semibold text-red-700 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2"
            >
              Lixeira
            </Link>
            <Button
              disabled={createMutation.isPending}
              onClick={() => {
                setNotice(null);
                setActionError(null);
                setCreating((value) => !value);
                reset(emptyForm);
              }}
            >
              {creating ? "Cancelar" : "+ Novo fornecedor"}
            </Button>
          </div>
        </div>

        {creating ? (
          <Card className="mt-5 p-5">
            <form onSubmit={onCreate}>
              <div className="grid gap-4 lg:grid-cols-3">
                <TextField label="Contato / vendedor" placeholder="Ex.: João" autoFocus error={errors.name?.message} {...register("name")} />
                <TextField label="Empresa" placeholder="Ex.: Distribuidora Silva" error={errors.company?.message} {...register("company")} />
                <TextField
                  label="Telefone"
                  placeholder="Ex.: (75) 9 9999-9999"
                  inputMode="tel"
                  maxLength={16}
                  error={errors.phone?.message}
                  {...phoneField}
                  onChange={(event) => {
                    event.target.value = formatSupplierPhoneInput(event.target.value);
                    void phoneField.onChange(event);
                  }}
                />
              </div>

              <details className="mt-4 rounded-xl border border-zinc-200 bg-zinc-50 p-4">
                <summary className="cursor-pointer text-sm font-semibold text-zinc-800">Configuração de compra</summary>
                <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                  <TextField label="Frequência de compra (dias)" placeholder="Ex.: 7" inputMode="numeric" error={errors.purchaseFrequencyDays?.message} {...register("purchaseFrequencyDays")} />
                  <SelectField label="Dia preferencial" error={errors.preferredOrderWeekday?.message} {...register("preferredOrderWeekday")}>
                    <option value="">Não informado</option>
                    {SUPPLIER_WEEKDAYS.map((day) => <option key={day.value} value={day.value}>{day.label}</option>)}
                  </SelectField>
                  <TextField label="Prazo de entrega (dias)" placeholder="Ex.: 2" inputMode="numeric" error={errors.averageDeliveryDays?.message} {...register("averageDeliveryDays")} />
                  <TextField label="Margem de segurança (dias)" placeholder="Ex.: 1" inputMode="numeric" error={errors.safetyMarginDays?.message} {...register("safetyMarginDays")} />
                </div>

                <label className="mt-4 block">
                  <span className="text-sm font-medium text-zinc-800">Observação (opcional)</span>
                  <textarea rows={3} className="mt-2 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2.5 text-sm text-zinc-900 outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100" placeholder="Informações úteis sobre atendimento, pedido ou entrega." {...register("observation")} />
                  {errors.observation?.message ? <span className="mt-1 block text-xs text-red-700">{errors.observation.message}</span> : null}
                </label>
              </details>

              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
                Empresa e telefone são obrigatórios no cadastro completo. O cadastro rápido com apenas o nome será usado futuramente dentro de Nova Entrada e ficará marcado como pendente.
              </div>

              {actionError ? <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{actionError}</div> : null}

              <div className="mt-5 flex justify-end gap-2">
                <Button variant="ghost" disabled={createMutation.isPending} onClick={() => { reset(emptyForm); setCreating(false); setActionError(null); }}>Cancelar</Button>
                <Button type="submit" isLoading={createMutation.isPending} loadingLabel="Salvando…">Salvar fornecedor</Button>
              </div>
            </form>
          </Card>
        ) : null}

        {notice ? <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">{notice}</div> : null}
        {!creating && actionError ? <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{actionError}</div> : null}

        <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="relative w-full max-w-md">
            <TextField
              id="suppliers-search"
              label="Pesquisar"
              placeholder="Contato, empresa ou telefone"
              value={search}
              className={search ? "pr-11" : ""}
              onChange={(event) => setSearch(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && search.trim() && filteredSuppliers[0]) {
                  event.preventDefault();
                  navigate(`/fornecedores/${filteredSuppliers[0].id}`);
                }
              }}
            />
            {search ? (
              <SearchClearButton
                onClear={() => {
                  setSearch("");
                  document.getElementById("suppliers-search")?.focus();
                }}
              />
            ) : null}
          </div>
          {pendingOnly ? (
            <button
              type="button"
              className="min-h-11 rounded-xl border border-amber-200 bg-amber-50 px-4 text-sm font-semibold text-amber-900"
              onClick={() => {
                const next = new URLSearchParams(searchParams);
                next.delete("filter");
                setSearchParams(next);
              }}
            >
              Mostrando pendentes · Ver todos
            </button>
          ) : null}
        </div>

        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
          “Próxima compra recomendada” será calculada a partir de Entradas reais e da configuração do fornecedor. Até existir esse histórico, o app não inventará uma data.
        </div>

        {suppliersQuery.isPending ? <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando fornecedores…</Card> : null}

        {suppliersQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5">
            <p className="text-sm font-medium text-red-800">Não foi possível carregar os fornecedores.</p>
            <Button className="mt-4" variant="secondary" onClick={() => void suppliersQuery.refetch()}>Tentar novamente</Button>
          </Card>
        ) : null}

        {!suppliersQuery.isPending && !suppliersQuery.isError && filteredSuppliers.length === 0 ? (
          <Card className="mt-5 p-7 text-center">
            <h3 className="font-semibold">{search ? "Nenhum fornecedor encontrado" : "Nenhum fornecedor cadastrado"}</h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">{search ? "Tente outro termo de pesquisa." : "Cadastre o primeiro fornecedor para preparar o módulo de Entradas."}</p>
          </Card>
        ) : null}

        <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {filteredSuppliers.map((supplier) => (
            <Link key={supplier.id} to={`/fornecedores/${supplier.id}`} className="block">
              <InteractiveCard className="h-full p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">Contato</p>
                    <h3 className="mt-1 break-words font-semibold text-zinc-950">{supplier.name}</h3>
                  </div>
                  {supplier.isPending ? <span className="shrink-0 rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-semibold text-amber-800">Pendente</span> : null}
                </div>

                <div className="mt-4 space-y-2 text-sm">
                  <InfoLine label="Empresa" value={supplier.company ?? "Não informada"} />
                  <InfoLine label="Telefone" value={formatSupplierPhoneDisplay(supplier.phone)} />
                  <InfoLine label="Próxima compra" value="Aguardando Entradas" />
                </div>
              </InteractiveCard>
            </Link>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

function InfoLine({ label, value }: { label: string; value: string }) {
  return <div className="flex items-start justify-between gap-3"><span className="text-zinc-500">{label}</span><span className="text-right font-medium text-zinc-800">{value}</span></div>;
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

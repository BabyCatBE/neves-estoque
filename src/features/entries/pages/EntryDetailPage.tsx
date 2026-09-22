import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { getEntryDetails } from "../api/entries";
import { formatMoney } from "../lib/entryValidation";

export function EntryDetailPage() {
  const { entryId } = useParams();
  const entryQuery = useQuery({
    queryKey: ["entries", "detail", entryId],
    queryFn: () => {
      if (!entryId) throw new Error("Entrada inválida.");
      return getEntryDetails(entryId);
    },
    enabled: Boolean(entryId)
  });

  return (
    <AppShell title="Entrada" showBack backTo="/entradas/historico">
      <section>
        {entryQuery.isPending ? <Card className="p-5 text-sm text-zinc-600">Carregando Entrada…</Card> : null}
        {entryQuery.isError ? (
          <Card className="border-red-200 p-5">
            <p className="text-sm font-medium text-red-800">Não foi possível carregar esta Entrada.</p>
            <Button className="mt-4" variant="secondary" onClick={() => void entryQuery.refetch()}>Tentar novamente</Button>
          </Card>
        ) : null}

        {entryQuery.data ? (
          <>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Entrada salva</p>
                <h2 className="mt-1 text-2xl font-semibold">{entryQuery.data.supplierName}</h2>
                <p className="mt-1 text-sm text-zinc-500">{entryQuery.data.supplierCompany ?? "Empresa não informada"} · {formatDate(entryQuery.data.effectiveAt)}</p>
              </div>
              <div className="flex w-fit items-center gap-2">
                <span className="inline-flex min-h-8 items-center rounded-full bg-zinc-100 px-3 py-1 text-xs font-semibold text-zinc-600">
                  Somente leitura
                </span>
                <Link
                  to={`/entradas/${entryQuery.data.id}/editar`}
                  className="inline-flex min-h-8 items-center rounded-full bg-red-700 px-3 py-1 text-xs font-semibold text-white transition hover:bg-red-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2"
                >
                  Editar Entrada
                </Link>
              </div>
            </div>

            <div className="mt-5 grid gap-4 sm:grid-cols-3">
              <Metric label="Fornecedor" value={entryQuery.data.supplierName} />
              <Metric label="Data" value={formatDate(entryQuery.data.effectiveAt)} />
              <Metric label="Total conhecido" value={`${formatMoney(entryQuery.data.totalKnown)}${entryQuery.data.hasMissingPrice ? " *" : ""}`} />
            </div>

            {entryQuery.data.hasMissingPrice ? (
              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                * O total soma apenas os itens com preço conhecido. Há pelo menos um item com preço não informado.
              </div>
            ) : null}

            {entryQuery.data.observation ? (
              <Card className="mt-4 p-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">Observação</p>
                <p className="mt-2 whitespace-pre-wrap text-sm text-zinc-800">{entryQuery.data.observation}</p>
              </Card>
            ) : null}

            <Card className="mt-5 overflow-hidden">
              <div className="border-b border-zinc-100 px-5 py-4">
                <h3 className="font-semibold">Itens recebidos</h3>
              </div>
              <div className="divide-y divide-zinc-100">
                {entryQuery.data.items.map((item) => (
                  <div key={item.id} className="grid gap-3 px-5 py-4 md:grid-cols-[1fr_120px_150px_150px] md:items-center">
                    <div>
                      <p className="font-semibold">{item.productName}</p>
                      <p className="mt-1 text-xs text-zinc-500">{item.unit}</p>
                    </div>
                    <Info label="Quantidade" value={String(item.quantity).replace(".", ",")} />
                    <Info
                      label="Unitário"
                      value={item.unitPrice === null ? "Preço não informado" : item.unitPrice === 0 ? "Bonificação" : formatMoney(item.unitPrice)}
                    />
                    <Info
                      label="Total"
                      value={item.unitPrice === null ? "—" : formatMoney(item.quantity * item.unitPrice)}
                    />
                  </div>
                ))}
              </div>
            </Card>
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <Card className="p-5"><p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">{label}</p><p className="mt-2 break-words text-lg font-semibold">{value}</p></Card>;
}

function Info({ label, value }: { label: string; value: string }) {
  return <div><p className="text-xs uppercase tracking-wide text-zinc-400">{label}</p><p className="mt-1 text-sm font-medium">{value}</p></div>;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" }).format(new Date(value));
}

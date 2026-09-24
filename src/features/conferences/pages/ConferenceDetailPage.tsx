import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { getConferenceDetails } from "../api/conferences";
import { formatConferenceDate, formatConferenceTime } from "../lib/conferenceValidation";

export function ConferenceDetailPage() {
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
    <AppShell title="Conferência" showBack backTo="/conferencias/historico">
      <section>
        {conferenceQuery.isPending ? (
          <Card className="p-5 text-sm text-zinc-600">Carregando Conferência…</Card>
        ) : null}

        {conferenceQuery.isError ? (
          <Card className="border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar esta Conferência.
          </Card>
        ) : null}

        {conferenceQuery.data ? (
          <>
            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
                  {conferenceQuery.data.categoryName}
                </p>
                <h2 className="mt-1 text-2xl font-semibold tracking-tight">
                  {formatConferenceDate(conferenceQuery.data.effectiveAt)}
                </h2>
                <p className="mt-1 text-sm text-zinc-500">
                  Registrada às {formatConferenceTime(conferenceQuery.data.effectiveAt)}
                </p>
              </div>
              <Link to={`/conferencias/${conferenceQuery.data.id}/editar`}>
                <Button>Corrigir Conferência</Button>
              </Link>
            </div>

            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <Info label="Responsável pela contagem" value={conferenceQuery.data.physicalResponsible} />
              <Info
                label="Produtos conferidos"
                value={String(conferenceQuery.data.items.length)}
              />
            </div>

            {conferenceQuery.data.observation ? (
              <Card className="mt-4 p-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">Observação</p>
                <p className="mt-2 whitespace-pre-wrap text-sm text-zinc-800">
                  {conferenceQuery.data.observation}
                </p>
              </Card>
            ) : null}

            <Card className="mt-5 overflow-hidden">
              <div className="border-b border-zinc-100 px-5 py-4">
                <h3 className="font-semibold">Quantidades registradas</h3>
              </div>
              <div className="divide-y divide-zinc-100">
                {conferenceQuery.data.items.map((item) => (
                  <div
                    key={item.id}
                    className="grid gap-2 px-5 py-4 sm:grid-cols-[1fr_100px_160px] sm:items-center"
                  >
                    <p className="font-semibold">{item.productName}</p>
                    <p className="text-sm text-zinc-500">{item.unit}</p>
                    <p className="text-sm font-semibold sm:text-right">
                      {new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 3 }).format(item.quantity)}
                    </p>
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

function Info({ label, value }: { label: string; value: string }) {
  return (
    <Card className="p-5">
      <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">{label}</p>
      <p className="mt-2 text-lg font-semibold text-zinc-950">{value}</p>
    </Card>
  );
}

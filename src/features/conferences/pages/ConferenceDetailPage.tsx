import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { useAuth } from "../../auth/context/AuthContext";
import { getConferenceDetails, softDeleteConference } from "../api/conferences";
import {
  formatConferenceDate,
  formatConferenceTime,
  getConferenceErrorMessage
} from "../lib/conferenceValidation";

export function ConferenceDetailPage() {
  const { conferenceId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const conferenceQuery = useQuery({
    queryKey: ["conferences", "detail", conferenceId],
    queryFn: () => {
      if (!conferenceId) throw new Error("Conferência não encontrada.");
      return getConferenceDetails(conferenceId);
    },
    enabled: Boolean(conferenceId)
  });

  const deleteMutation = useMutation({ mutationFn: softDeleteConference });

  const confirmDelete = async () => {
    const details = conferenceQuery.data;
    if (!details) return;

    if (!deviceId) {
      setActionError("Este dispositivo ainda não está pronto para excluir Conferências.");
      setDeleteOpen(false);
      return;
    }

    setActionError(null);

    try {
      await deleteMutation.mutateAsync({
        conferenceId: details.id,
        deviceId
      });

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["conferences"] }),
        queryClient.invalidateQueries({ queryKey: ["products"] }),
        queryClient.invalidateQueries({ queryKey: ["stock"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] }),
        queryClient.invalidateQueries({ queryKey: ["trash"] })
      ]);

      navigate(`/conferencias/historico/${details.categoryId}?deleted=1`, { replace: true });
    } catch (error) {
      setDeleteOpen(false);
      setActionError(getConferenceErrorMessage(error));
    }
  };

  return (
    <AppShell title="Conferência" showBack backTo="/conferencias/historico">
      <section>
        {searchParams.get("updated") === "1" ? (
          <div className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
            Correção da Conferência salva com sucesso.
          </div>
        ) : null}

        {actionError ? (
          <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            {actionError}
          </div>
        ) : null}

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
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  className="text-red-700"
                  disabled={deleteMutation.isPending}
                  onClick={() => {
                    setActionError(null);
                    setDeleteOpen(true);
                  }}
                >
                  Excluir Conferência
                </Button>
                <Link to={`/conferencias/${conferenceQuery.data.id}/editar`}>
                  <Button>Corrigir Conferência</Button>
                </Link>
              </div>
            </div>

            <div className="mt-5">
              <Info label="Responsável pela contagem" value={conferenceQuery.data.physicalResponsible} />
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

            <ConfirmDialog
              open={deleteOpen}
              title="Excluir Conferência?"
              description={
                <>
                  Excluir a Conferência de <strong>“{conferenceQuery.data.categoryName}”</strong> de{" "}
                  <strong>{formatConferenceDate(conferenceQuery.data.effectiveAt)}</strong>?
                  <br />
                  Ela sairá do Histórico ativo e poderá alterar estoque, consumo e relatórios.
                  Ficará restaurável na Lixeira por 7 dias.
                </>
              }
              confirmLabel="Enviar para Lixeira"
              pendingLabel="Excluindo…"
              variant="danger"
              isPending={deleteMutation.isPending}
              onCancel={() => setDeleteOpen(false)}
              onConfirm={() => void confirmDelete()}
            />
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

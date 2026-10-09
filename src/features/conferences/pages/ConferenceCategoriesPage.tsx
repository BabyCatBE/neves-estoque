import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { useAuth } from "../../auth/context/AuthContext";
import { useNetworkStatus } from "../../../shared/offline/NetworkContext";
import {
  listConferenceCategories,
  listSameDayCategoryConferences
} from "../api/conferences";
import {
  conferenceBackgroundSaves,
  useConferenceSaveStatuses
} from "../lib/conferenceBackgroundSaves";
import { formatConferenceDate, localDateInputValue } from "../lib/conferenceValidation";

export function ConferenceCategoriesPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { session } = useAuth();
  const { isOnline } = useNetworkStatus();
  const statuses = useConferenceSaveStatuses();
  const ownerId = session?.user.id ?? "";
  const [selectedCategory, setSelectedCategory] = useState<{ id: string; name: string } | null>(null);
  const [openingEdit, setOpeningEdit] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [discardCategory, setDiscardCategory] = useState<{ id: string; name: string } | null>(null);
  const [discardError, setDiscardError] = useState<string | null>(null);
  const [discarding, setDiscarding] = useState(false);
  const categoriesQuery = useQuery({
    queryKey: ["conferences", "categories"],
    queryFn: listConferenceCategories
  });

  const editSelected = async () => {
    if (!selectedCategory || openingEdit) return;
    setDialogError(null);
    setOpeningEdit(true);
    try {
      // Uma contagem: abre edição. Várias: escolher no histórico sem presumir qual corrigir.
      const today = await listSameDayCategoryConferences(selectedCategory.id, localDateInputValue());
      const destination = today.length === 1
        ? `/conferencias/${today[0]!.id}/editar`
        : `/conferencias/historico/${selectedCategory.id}`;
      setSelectedCategory(null);
      navigate(destination);
    } catch {
      setDialogError("Não foi possível consultar a Conferência. Tente novamente.");
    } finally {
      setOpeningEdit(false);
    }
  };

  const discardFailed = async () => {
    if (!discardCategory || discarding || !ownerId) return;
    setDiscardError(null);
    setDiscarding(true);
    try {
      await conferenceBackgroundSaves.discard(ownerId, discardCategory.id);
      setDiscardCategory(null);
    } catch {
      setDiscardError("Não foi possível descartar esta cópia local. Ela foi preservada.");
    } finally {
      setDiscarding(false);
    }
  };

  return (
    <AppShell title="Fazer conferência" showBack backTo="/conferencias">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Escolha uma categoria</h2>
        <p className="mt-1 text-sm leading-6 text-zinc-600">
          Cada categoria é uma Conferência independente. Todas as quantidades da categoria precisam ser preenchidas para salvar.
        </p>

        {searchParams.get("queued") === "1" ? (
          <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900">
            Envio iniciado. Você pode conferir outra categoria enquanto o aplicativo aguarda confirmação do servidor.
          </div>
        ) : null}

        {searchParams.get("saved") === "1" ? (
          <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800">
            Conferência salva com sucesso.
          </div>
        ) : null}

        {categoriesQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando categorias…</Card>
        ) : null}
        {categoriesQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar as categorias.
          </Card>
        ) : null}

        <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {(categoriesQuery.data ?? []).map((category) => {
            const status = statuses[ownerId + ":" + category.id];
            const blocked = status?.phase === "saving" || status?.phase === "failed";
            const conferredToday = category.conferredToday ||
              (status?.phase === "saved" && status.effectiveDate === localDateInputValue());
            const content = (
              <InteractiveCard className={`h-full p-5 ${category.productCount === 0 ? "opacity-60" : ""}`}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-semibold text-zinc-950">{category.name}</h3>
                    <p className="mt-1 text-xs text-zinc-500">
                      {category.productCount} {category.productCount === 1 ? "produto" : "produtos"}
                    </p>
                  </div>
                  {conferredToday ? (
                    <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                      ✓ Conferida hoje
                    </span>
                  ) : null}
                </div>
                <p className="mt-4 text-sm text-zinc-600">
                  {category.productCount === 0
                    ? "Categoria sem produtos ativos."
                    : status?.phase === "saving"
                      ? "Salvando em segundo plano. Aguarde a confirmação antes de registrar outra contagem nesta categoria."
                      : status?.phase === "failed"
                        ? "Falha no envio: " + (status.message ?? "Revise a tentativa.")
                        : conferredToday
                          ? "Outra Conferência hoje só deve ser registrada se houve nova contagem física."
                          : category.lastConferenceAt
                            ? `Última conferência: ${formatConferenceDate(category.lastConferenceAt)}`
                            : "Nunca conferida"}
                </p>
                {status?.phase === "saving" ? (
                  <p className="mt-3 text-sm font-semibold text-amber-700" role="status">Salvando…</p>
                ) : null}
                {status?.phase === "saved" && status.message ? (
                  <p className="mt-3 text-sm text-amber-800" role="status">{status.message}</p>
                ) : null}
              </InteractiveCard>
            );
            return (
              <div key={category.id} className="space-y-2">
                {category.productCount > 0 ? (
                  <button
                    type="button"
                    disabled={blocked}
                    onClick={() => {
                      if (conferredToday) {
                        setDialogError(null);
                        setSelectedCategory({ id: category.id, name: category.name });
                      } else {
                        navigate(`/conferencias/fazer/${category.id}`);
                      }
                    }}
                    className="block w-full rounded-xl text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 disabled:cursor-wait"
                  >
                    {content}
                  </button>
                ) : content}
                {status?.phase === "failed" ? (
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="secondary"
                      disabled={!isOnline}
                      onClick={() => conferenceBackgroundSaves.retry(ownerId, category.id)}
                    >
                      Tentar novamente
                    </Button>
                    <Button variant="ghost" onClick={() => {
                      setDiscardError(null);
                      setDiscardCategory({ id: category.id, name: category.name });
                    }}>
                      Descartar tentativa
                    </Button>
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>

        {!categoriesQuery.isPending && (categoriesQuery.data?.length ?? 0) === 0 ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">
            Ainda não existem categorias ativas.
          </Card>
        ) : null}
      </section>

      {selectedCategory ? (
        <div role="dialog" aria-modal="true" aria-labelledby="conference-choice-title" className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6">
          <Card className="w-full max-w-md p-5 shadow-xl">
            <h3 id="conference-choice-title" className="text-xl font-semibold">{selectedCategory.name}</h3>
            <p className="mt-2 text-sm text-zinc-600">
              Esta categoria já foi conferida hoje. Escolha Editar para consultar ou corrigir a contagem, ou Nova conferência para registrar outra contagem física.
            </p>
            {dialogError ? <p role="alert" className="mt-3 text-sm text-red-700">{dialogError}</p> : null}
            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <Button variant="ghost" disabled={openingEdit} onClick={() => setSelectedCategory(null)}>
                Cancelar
              </Button>
              <Button variant="secondary" isLoading={openingEdit} onClick={() => void editSelected()}>
                Editar
              </Button>
              <Button disabled={openingEdit} onClick={() => {
                const id = selectedCategory.id;
                setSelectedCategory(null);
                navigate(`/conferencias/fazer/${id}`);
              }}>
                Nova conferência
              </Button>
            </div>
          </Card>
        </div>
      ) : null}

      {discardCategory ? (
        <div role="dialog" aria-modal="true" aria-labelledby="conference-discard-title" className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6">
          <Card className="w-full max-w-md p-5 shadow-xl">
            <h3 id="conference-discard-title" className="text-xl font-semibold">Descartar tentativa?</h3>
            <p className="mt-2 text-sm text-zinc-600">
              A cópia local de {discardCategory.name} será apagada. Se a rede caiu durante o envio, confira antes o Histórico: a gravação pode ter sido concluída no servidor.
            </p>
            {discardError ? <p role="alert" className="mt-3 text-sm text-red-700">{discardError}</p> : null}
            <div className="mt-5 flex justify-end gap-2">
              <Button variant="ghost" disabled={discarding} onClick={() => setDiscardCategory(null)}>Cancelar</Button>
              <Button isLoading={discarding} onClick={() => void discardFailed()}>Descartar cópia local</Button>
            </div>
          </Card>
        </div>
      ) : null}
    </AppShell>
  );
}

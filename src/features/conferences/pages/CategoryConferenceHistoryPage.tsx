import { useQuery } from "@tanstack/react-query";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import {
  getCategoryConferenceSetup,
  listCategoryConferenceHistory
} from "../api/conferences";
import {
  formatConferenceDate,
  formatConferenceTime,
  localDateKey
} from "../lib/conferenceValidation";

export function CategoryConferenceHistoryPage() {
  const { categoryId } = useParams();
  const [searchParams] = useSearchParams();
  const categoryQuery = useQuery({
    queryKey: ["conferences", "setup", categoryId],
    queryFn: () => {
      if (!categoryId) throw new Error("Categoria não encontrada.");
      return getCategoryConferenceSetup(categoryId);
    },
    enabled: Boolean(categoryId)
  });
  const historyQuery = useQuery({
    queryKey: ["conferences", "history", categoryId],
    queryFn: () => {
      if (!categoryId) return [];
      return listCategoryConferenceHistory(categoryId);
    },
    enabled: Boolean(categoryId)
  });

  const countsByDay = new Map<string, number>();
  for (const item of historyQuery.data ?? []) {
    const key = localDateKey(item.effectiveAt);
    countsByDay.set(key, (countsByDay.get(key) ?? 0) + 1);
  }

  return (
    <AppShell title="Histórico da categoria" showBack backTo="/conferencias/historico">
      <section>
        {searchParams.get("deleted") === "1" ? (
          <div className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
            Conferência enviada para a Lixeira. Ela pode ser restaurada por 7 dias.
          </div>
        ) : null}

        <h2 className="text-xl font-semibold tracking-tight">
          {categoryQuery.data?.category.name ?? "Conferências"}
        </h2>

        {historyQuery.isPending || categoryQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando Conferências…</Card>
        ) : null}

        <div className="mt-5 space-y-3">
          {(historyQuery.data ?? []).map((conference) => {
            const sameDayCount = countsByDay.get(localDateKey(conference.effectiveAt)) ?? 0;
            return (
              <Link key={conference.id} to={`/conferencias/${conference.id}`} className="block">
                <Card className="p-5 transition hover:border-red-200 hover:shadow-sm">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-zinc-950">
                        {formatConferenceDate(conference.effectiveAt)}
                        {sameDayCount > 1 ? ` · ${formatConferenceTime(conference.effectiveAt)}` : ""}
                      </p>
                      <p className="mt-1 text-sm text-zinc-600">
                        Responsável: {conference.physicalResponsible}
                      </p>
                    </div>
                    <span className="text-sm font-semibold text-red-700">Abrir</span>
                  </div>
                </Card>
              </Link>
            );
          })}
        </div>

        {!historyQuery.isPending && (historyQuery.data?.length ?? 0) === 0 ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">
            Nenhuma Conferência registrada para esta categoria.
          </Card>
        ) : null}
      </section>
    </AppShell>
  );
}

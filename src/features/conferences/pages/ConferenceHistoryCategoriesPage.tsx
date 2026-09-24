import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { listConferenceCategories } from "../api/conferences";
import { formatConferenceDate } from "../lib/conferenceValidation";

export function ConferenceHistoryCategoriesPage() {
  const categoriesQuery = useQuery({
    queryKey: ["conferences", "categories"],
    queryFn: listConferenceCategories
  });

  return (
    <AppShell title="Histórico de Conferências" showBack backTo="/conferencias">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Categorias</h2>
        <p className="mt-1 text-sm text-zinc-600">Escolha uma categoria para consultar as contagens salvas.</p>

        {categoriesQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando histórico…</Card>
        ) : null}

        <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {(categoriesQuery.data ?? []).map((category) => {
            const card = (
              <InteractiveCard className={`h-full p-5 ${category.lastConferenceAt ? "" : "opacity-60"}`}>
                <h3 className="font-semibold">{category.name}</h3>
                <p className="mt-2 text-sm text-zinc-600">
                  {category.lastConferenceAt
                    ? `Última conferência: ${formatConferenceDate(category.lastConferenceAt)}`
                    : "Sem Conferências registradas"}
                </p>
              </InteractiveCard>
            );
            return category.lastConferenceAt ? (
              <Link
                key={category.id}
                to={`/conferencias/historico/${category.id}`}
                className="block"
              >
                {card}
              </Link>
            ) : (
              <div key={category.id}>{card}</div>
            );
          })}
        </div>
      </section>
    </AppShell>
  );
}

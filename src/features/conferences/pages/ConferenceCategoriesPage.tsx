import { useQuery } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { listConferenceCategories } from "../api/conferences";
import { formatConferenceDate } from "../lib/conferenceValidation";

export function ConferenceCategoriesPage() {
  const [searchParams] = useSearchParams();
  const categoriesQuery = useQuery({
    queryKey: ["conferences", "categories"],
    queryFn: listConferenceCategories
  });

  return (
    <AppShell title="Fazer conferência" showBack backTo="/conferencias">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Escolha uma categoria</h2>
        <p className="mt-1 text-sm leading-6 text-zinc-600">
          Cada categoria é uma Conferência independente. Todas as quantidades da categoria precisam ser preenchidas para salvar.
        </p>

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
            const content = (
              <InteractiveCard className={`h-full p-5 ${category.productCount === 0 ? "opacity-60" : ""}`}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-semibold text-zinc-950">{category.name}</h3>
                    <p className="mt-1 text-xs text-zinc-500">
                      {category.productCount} {category.productCount === 1 ? "produto" : "produtos"}
                    </p>
                  </div>
                  {category.conferredToday ? (
                    <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                      ✓ Conferida hoje
                    </span>
                  ) : null}
                </div>
                <p className="mt-4 text-sm text-zinc-600">
                  {category.productCount === 0
                    ? "Categoria sem produtos ativos."
                    : category.conferredToday
                      ? "Você pode registrar outra Conferência hoje se houver uma nova contagem física."
                      : category.lastConferenceAt
                        ? `Última conferência: ${formatConferenceDate(category.lastConferenceAt)}`
                        : "Nunca conferida"}
                </p>
              </InteractiveCard>
            );

            return category.productCount > 0 ? (
              <Link key={category.id} to={`/conferencias/fazer/${category.id}`} className="block">
                {content}
              </Link>
            ) : (
              <div key={category.id}>{content}</div>
            );
          })}
        </div>

        {!categoriesQuery.isPending && (categoriesQuery.data?.length ?? 0) === 0 ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">
            Ainda não existem categorias ativas.
          </Card>
        ) : null}
      </section>
    </AppShell>
  );
}

import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";

export function ConferencesHubPage() {
  return (
    <AppShell title="Conferência" showBack backTo="/">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Conferência física</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          A contagem física é a referência do estoque. Cada categoria pode ser conferida de forma independente.
        </p>

        <div className="mt-6 grid gap-4 md:grid-cols-3">
          <Link to="/conferencias/imprimir" className="block">
            <InteractiveCard className="h-full p-6">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-zinc-500">Preparação</p>
              <h3 className="mt-2 text-xl font-semibold">Imprimir papéis</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Gere as folhas A4 a partir das categorias e produtos atuais.
              </p>
            </InteractiveCard>
          </Link>

          <Link to="/conferencias/fazer" className="block">
            <InteractiveCard className="h-full p-6">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Operação</p>
              <h3 className="mt-2 text-xl font-semibold">Fazer conferência</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Escolha uma categoria e registre todas as quantidades contadas.
              </p>
            </InteractiveCard>
          </Link>

          <Link to="/conferencias/historico" className="block">
            <InteractiveCard className="h-full p-6">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-zinc-500">Consulta</p>
              <h3 className="mt-2 text-xl font-semibold">Histórico</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Consulte as Conferências salvas por categoria e corrija um registro quando necessário.
              </p>
            </InteractiveCard>
          </Link>
        </div>
      </section>
    </AppShell>
  );
}

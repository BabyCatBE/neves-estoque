import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";

export function PurchasesHubPage() {
  return (
    <AppShell title="Compras" showBack backTo="/">
      <section>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
          Simulação temporária
        </p>
        <h2 className="mt-1 text-2xl font-semibold tracking-tight">Preparar compras</h2>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-zinc-600">
          A lista de Compras não altera o estoque e não cria pedido salvo no V1.
        </p>

        <div className="mt-6 grid gap-4 md:grid-cols-3">
          <Link to="/compras/fornecedor" className="block">
            <InteractiveCard className="h-full p-6">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
                Disponível nesta etapa
              </p>
              <h3 className="mt-2 text-xl font-semibold">Por fornecedor</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Escolha um fornecedor e prepare uma lista usando o histórico real de compras.
              </p>
            </InteractiveCard>
          </Link>

          <InteractiveCard className="h-full cursor-default p-6 opacity-70">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-zinc-500">
              Próxima etapa
            </p>
            <h3 className="mt-2 text-xl font-semibold">Por estoque</h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Reunirá os produtos que precisarem de reposição.
            </p>
          </InteractiveCard>

          <InteractiveCard className="h-full cursor-default p-6 opacity-70">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-zinc-500">
              Próxima etapa
            </p>
            <h3 className="mt-2 text-xl font-semibold">Por categoria</h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Permitirá preparar a lista seguindo a ordem manual da categoria.
            </p>
          </InteractiveCard>
        </div>

        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
          A recomendação automática ainda não foi ativada. O algoritmo exato continua a definir.
        </div>
      </section>
    </AppShell>
  );
}

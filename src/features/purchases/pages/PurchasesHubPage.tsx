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
                Inteligência + histórico
              </p>
              <h3 className="mt-2 text-xl font-semibold">Por fornecedor</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Escolha um fornecedor. Quando houver histórico e configuração suficientes,
                os Produtos recomendados aparecem primeiro com quantidade sugerida.
              </p>
            </InteractiveCard>
          </Link>

          <Link to="/compras/estoque" className="block">
            <InteractiveCard className="h-full p-6">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
                Necessidade de compra
              </p>
              <h3 className="mt-2 text-xl font-semibold">Por estoque</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Veja primeiro os Produtos com recomendação automática e, quando quiser,
                abra também o restante do estoque para inclusão manual.
              </p>
            </InteractiveCard>
          </Link>

          <Link to="/compras/categoria" className="block">
            <InteractiveCard className="h-full p-6">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
                Categoria + sugestão
              </p>
              <h3 className="mt-2 text-xl font-semibold">Por categoria</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Preserve a ordem manual da categoria e veja sugestão e risco quando houver
                histórico e configuração suficientes.
              </p>
            </InteractiveCard>
          </Link>
        </div>

        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
          A recomendação automática só é gerada com histórico confiável suficiente:
          pelo menos 28 dias e 3 intervalos válidos entre Conferências, além da
          configuração necessária do fornecedor. Quando isso não existir, a quantidade
          continua podendo ser informada manualmente.
        </div>
      </section>
    </AppShell>
  );
}

import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";

export function EntriesHubPage() {
  return (
    <AppShell title="Entradas" showBack backTo="/">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Entradas de mercadoria</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          Entrada representa mercadoria realmente recebida. Pedidos futuros não devem ser registrados aqui.
        </p>

        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <Link to="/entradas/nova" className="block">
            <Card className="h-full p-6 transition hover:-translate-y-0.5 hover:border-red-200 hover:shadow-md">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Operação</p>
              <h3 className="mt-2 text-xl font-semibold">Nova Entrada</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Registre fornecedor, data, produtos recebidos, quantidades e preços conhecidos.
              </p>
            </Card>
          </Link>

          <Link to="/entradas/historico" className="block">
            <Card className="h-full p-6 transition hover:-translate-y-0.5 hover:border-red-200 hover:shadow-md">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-zinc-500">Consulta</p>
              <h3 className="mt-2 text-xl font-semibold">Histórico de Entradas</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Consulte Entradas salvas e pesquise por fornecedor ou produto.
              </p>
            </Card>
          </Link>
        </div>
      </section>
    </AppShell>
  );
}

import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";

export function AlertsHubPage() {
  return (
    <AppShell title="Alertas" showBack backTo="/">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Alertas e recuperação</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          Acesse pendências operacionais e itens que ainda podem ser restaurados.
        </p>

        <div className="mt-6 grid gap-4 sm:grid-cols-2">
          <Link to="/alertas/lixeira" className="block">
            <Card className="h-full p-6 transition hover:-translate-y-0.5 hover:border-red-200 hover:shadow-md">
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-red-50 text-red-700">
                <TrashIcon />
              </div>
              <h3 className="mt-4 text-lg font-semibold">Lixeira Universal</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Restaurar Produtos, Categorias, Fornecedores e Entradas dentro da janela de 7 dias.
              </p>
            </Card>
          </Link>
        </div>
      </section>
    </AppShell>
  );
}

function TrashIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none">
      <path d="M5 7h14M9 7V4h6v3M8 10v7M12 10v7M16 10v7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M7 7l1 13h8l1-13" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </svg>
  );
}

import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";

const modules = [
  ["Estoque Atual", "/estoque", "Posição derivada da última conferência válida e entradas posteriores."],
  ["Conferência", "/conferencias", "Registrar contagens físicas por categoria ou produto."],
  ["Entrada", "/entradas", "Registrar mercadorias efetivamente recebidas."],
  ["Produtos", "/produtos", "Catálogo operacional de produtos."],
  ["Fornecedores", "/fornecedores", "Cadastro e histórico de fornecedores."],
  ["Compras", "/compras", "Simulação de necessidade de compra."]
] as const;

export function HomePage() {
  return (
    <AppShell>
      <section>
        <span className="inline-flex rounded-full bg-red-50 px-3 py-1 text-xs font-semibold text-red-700">DEV</span>
        <h2 className="mt-3 text-2xl font-semibold tracking-tight">Controle de estoque</h2>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-zinc-600">Estrutura navegável inicial. Os módulos ainda não estão implementados operacionalmente.</p>
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {modules.map(([title, href, description]) => (
            <Link key={href} to={href} className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-red-200 hover:shadow-md">
              <h3 className="font-semibold">{title}</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">{description}</p>
            </Link>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

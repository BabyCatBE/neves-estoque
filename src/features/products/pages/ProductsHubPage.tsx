import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";

const options = [
  {
    title: "Produtos",
    description: "Consultar, cadastrar e editar o catálogo de produtos.",
    href: "/produtos/lista"
  },
  {
    title: "Categorias",
    description: "Organizar as categorias e a ordem usada no estoque e nas conferências.",
    href: "/produtos/categorias"
  }
] as const;

export function ProductsHubPage() {
  return (
    <AppShell title="Produtos" showBack>
      <section>
        <p className="max-w-2xl text-sm leading-6 text-zinc-600">
          Escolha o cadastro que deseja administrar.
        </p>

        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          {options.map((option) => (
            <Link key={option.href} to={option.href} className="block">
              <Card className="h-full p-5 transition hover:-translate-y-0.5 hover:border-red-200 hover:shadow-md">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-red-50 text-red-700">
                  <CategoryGridIcon />
                </div>
                <h2 className="mt-4 text-lg font-semibold">{option.title}</h2>
                <p className="mt-2 text-sm leading-6 text-zinc-600">{option.description}</p>
              </Card>
            </Link>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

function CategoryGridIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none">
      <rect x="4" y="4" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
      <rect x="14" y="4" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
      <rect x="4" y="14" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
      <rect x="14" y="14" width="6" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

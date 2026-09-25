import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { listProductCategories } from "../../products/api/products";

export function PurchaseCategorySelectPage() {
  const categoriesQuery = useQuery({
    queryKey: ["products", "categories"],
    queryFn: listProductCategories,
    staleTime: 30_000
  });

  return (
    <AppShell title="Compras · Por categoria" showBack backTo="/compras">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Escolha a categoria</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          Os Produtos serão exibidos na ordem manual já definida no cadastro.
        </p>

        {categoriesQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando categorias…</Card>
        ) : null}

        {categoriesQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar as categorias.
          </Card>
        ) : null}

        {categoriesQuery.data ? (
          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {categoriesQuery.data.map((category) => (
              <Link key={category.id} to={`/compras/categoria/${category.id}`} className="block">
                <InteractiveCard className="h-full p-5">
                  <h3 className="font-semibold text-zinc-950">{category.name}</h3>
                </InteractiveCard>
              </Link>
            ))}
          </div>
        ) : null}
      </section>
    </AppShell>
  );
}

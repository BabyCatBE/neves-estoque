import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { listActiveProducts, listProductCategories } from "../../products/api/products";
import { PurchaseListEditor } from "../components/PurchaseListEditor";

export function PurchaseCategoryPage() {
  const { categoryId } = useParams();

  const categoriesQuery = useQuery({
    queryKey: ["products", "categories"],
    queryFn: listProductCategories,
    staleTime: 30_000
  });

  const productsQuery = useQuery({
    queryKey: ["products", "active"],
    queryFn: listActiveProducts,
    staleTime: 30_000
  });

  const category = categoriesQuery.data?.find((item) => item.id === categoryId);

  const items = [...(productsQuery.data ?? [])]
    .filter((product) => product.categoryId === categoryId)
    .sort((a, b) => {
      const aOrder = a.sortOrder ?? Number.MAX_SAFE_INTEGER;
      const bOrder = b.sortOrder ?? Number.MAX_SAFE_INTEGER;
      return aOrder - bOrder || a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" });
    })
    .map((product) => ({
      productId: product.id,
      productName: product.name,
      unit: product.unit,
      currentQuantity: product.currentQuantity
    }));

  return (
    <AppShell title="Compras · Por categoria" showBack backTo="/compras/categoria">
      <section>
        {categoriesQuery.isPending || productsQuery.isPending ? (
          <Card className="p-5 text-sm text-zinc-600">Carregando categoria…</Card>
        ) : null}

        {categoriesQuery.isError || productsQuery.isError ? (
          <Card className="border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar esta categoria.
          </Card>
        ) : null}

        {category && productsQuery.data ? (
          <>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
              Por categoria
            </p>
            <h2 className="mt-1 text-2xl font-semibold tracking-tight">{category.name}</h2>
            <PurchaseListEditor
              items={items}
              orderTitle={`Lista de compras — ${category.name}`}
              listTitle="Produtos da categoria"
              intro="Selecione os itens na ordem manual da categoria e informe quanto deseja comprar."
              emptyText="Esta categoria não possui Produtos ativos."
            />
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

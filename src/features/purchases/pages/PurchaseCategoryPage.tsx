import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { listProductCategories } from "../../products/api/products";
import { PurchaseListEditor } from "../components/PurchaseListEditor";
import { listPurchaseIntelligenceProducts } from "../api/purchases";

export function PurchaseCategoryPage() {
  const { categoryId } = useParams();

  const categoriesQuery = useQuery({
    queryKey: ["products", "categories"],
    queryFn: listProductCategories,
    staleTime: 30_000
  });

  const productsQuery = useQuery({
    queryKey: ["purchases", "intelligence"],
    queryFn: listPurchaseIntelligenceProducts,
    staleTime: 30_000
  });

  const category = categoriesQuery.data?.find((item) => item.id === categoryId);

  const items = [...(productsQuery.data ?? [])]
    .filter((product) => product.categoryId === categoryId)
    .sort((a, b) => {
      const aOrder = a.sortOrder ?? Number.MAX_SAFE_INTEGER;
      const bOrder = b.sortOrder ?? Number.MAX_SAFE_INTEGER;
      return aOrder - bOrder || a.productName.localeCompare(b.productName, "pt-BR", { sensitivity: "base" });
    })
    .map((product) => ({
      productId: product.productId,
      productName: product.productName,
      unit: product.unit,
      currentQuantity: product.currentQuantity,
      projection: product.projection
    }));

  return (
    <AppShell title="Compras · Por categoria" showBack backTo="/compras/categoria">
      <section>
        {categoriesQuery.isPending || productsQuery.isPending ? (
          <Card className="p-5 text-sm text-zinc-600">Calculando projeções…</Card>
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
              intro="A ordem manual da categoria é preservada. Quando houver histórico e configuração suficientes, a quantidade sugerida aparece automaticamente; os demais continuam disponíveis para inclusão manual."
              emptyText="Esta categoria não possui Produtos ativos."
            />
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

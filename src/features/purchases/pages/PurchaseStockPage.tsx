import { useQuery } from "@tanstack/react-query";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { listActiveProducts } from "../../products/api/products";
import { PurchaseListEditor } from "../components/PurchaseListEditor";

export function PurchaseStockPage() {
  const productsQuery = useQuery({
    queryKey: ["products", "active"],
    queryFn: listActiveProducts,
    staleTime: 30_000
  });

  const items = [...(productsQuery.data ?? [])]
    .sort((a, b) => {
      if (a.currentQuantity === null && b.currentQuantity !== null) return 1;
      if (a.currentQuantity !== null && b.currentQuantity === null) return -1;
      const quantityOrder = Number(a.currentQuantity ?? 0) - Number(b.currentQuantity ?? 0);
      return quantityOrder || a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" });
    })
    .map((product) => ({
      productId: product.id,
      productName: product.name,
      unit: product.unit,
      currentQuantity: product.currentQuantity
    }));

  return (
    <AppShell title="Compras · Por estoque" showBack backTo="/compras">
      <section>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Por estoque</p>
        <h2 className="mt-1 text-2xl font-semibold tracking-tight">Lista por nível de estoque</h2>

        {productsQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando produtos…</Card>
        ) : null}

        {productsQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar os produtos.
          </Card>
        ) : null}

        {productsQuery.data ? (
          <PurchaseListEditor
            items={items}
            orderTitle="Lista de compras"
            listTitle="Produtos por estoque atual"
            intro="Enquanto a recomendação automática não está definida, todos os Produtos aparecem para seleção manual, do menor estoque atual para o maior."
            emptyText="Nenhum Produto ativo encontrado."
            warning="Modo manual temporário: esta tela ainda não decide o que precisa ser comprado. A recomendação automática será adicionada somente após definição do algoritmo."
          />
        ) : null}
      </section>
    </AppShell>
  );
}

import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { PurchaseListEditor } from "../components/PurchaseListEditor";
import { listPurchaseIntelligenceProducts } from "../api/purchases";

export function PurchaseStockPage() {
  const [showRest, setShowRest] = useState(false);
  const productsQuery = useQuery({
    queryKey: ["purchases", "intelligence"],
    queryFn: listPurchaseIntelligenceProducts
  });

  const { recommended, remaining } = useMemo(() => {
    const products = productsQuery.data ?? [];
    const recommendedItems = products
      .filter((product) => product.projection.status === "recommended")
      .sort(compareRecommended)
      .map(toEditorItem);

    const remainingItems = products
      .filter((product) => product.projection.status !== "recommended")
      .sort((a, b) => {
        if (a.currentQuantity === null && b.currentQuantity !== null) return 1;
        if (a.currentQuantity !== null && b.currentQuantity === null) return -1;
        const quantityOrder =
          Number(a.currentQuantity ?? 0) - Number(b.currentQuantity ?? 0);
        return quantityOrder || a.productName.localeCompare(b.productName, "pt-BR", { sensitivity: "base" });
      })
      .map(toEditorItem);

    return { recommended: recommendedItems, remaining: remainingItems };
  }, [productsQuery.data]);

  const items = showRest ? [...recommended, ...remaining] : recommended;

  return (
    <AppShell title="Compras · Por estoque" showBack backTo="/compras">
      <section>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Por estoque</p>
        <h2 className="mt-1 text-2xl font-semibold tracking-tight">Necessidade de compra</h2>

        {productsQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Calculando projeções…</Card>
        ) : null}

        {productsQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5 text-sm text-red-800">
            Não foi possível calcular as projeções de compra.
          </Card>
        ) : null}

        {productsQuery.data ? (
          <>
            <div className="mt-5 flex flex-wrap items-center gap-3">
              <span className="text-sm font-semibold text-zinc-800">
                {recommended.length} {recommended.length === 1 ? "produto recomendado" : "produtos recomendados"}
              </span>
              {remaining.length > 0 ? (
                <Button variant="secondary" size="sm" onClick={() => setShowRest((current) => !current)}>
                  {showRest ? "Ocultar restante do estoque" : "Ver restante do estoque"}
                </Button>
              ) : null}
            </div>

            <PurchaseListEditor
              items={items}
              orderTitle="Lista de compras"
              listTitle={showRest ? "Recomendados e restante do estoque" : "Recomendados para compra"}
              intro="A sugestão usa consumo ponderado, estoque atual, ciclo de compra, prazo de entrega e margem de segurança. Você continua podendo alterar qualquer quantidade."
              emptyText={
                recommended.length === 0 && !showRest
                  ? "Nenhum Produto possui recomendação automática de compra agora."
                  : "Nenhum Produto ativo encontrado."
              }
              warning={
                recommended.length === 0
                  ? "Produtos sem histórico mínimo, sem fornecedor/configuração ou com estoque suficiente não são tratados como necessidade automática. Use “Ver restante do estoque” para incluí-los manualmente."
                  : null
              }
            />
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function toEditorItem(product: Awaited<ReturnType<typeof listPurchaseIntelligenceProducts>>[number]) {
  return {
    productId: product.productId,
    productName: product.productName,
    unit: product.unit,
    currentQuantity: product.currentQuantity,
    projection: product.projection
  };
}

function compareRecommended(
  a: Awaited<ReturnType<typeof listPurchaseIntelligenceProducts>>[number],
  b: Awaited<ReturnType<typeof listPurchaseIntelligenceProducts>>[number]
) {
  const riskOrder = { risk: 0, vulnerable: 1, protected: 2, unknown: 3 } as const;
  const riskDiff = riskOrder[a.projection.risk] - riskOrder[b.projection.risk];
  if (riskDiff) return riskDiff;

  const aCoverage = a.projection.coverageDays ?? Number.POSITIVE_INFINITY;
  const bCoverage = b.projection.coverageDays ?? Number.POSITIVE_INFINITY;
  return (
    aCoverage - bCoverage ||
    a.productName.localeCompare(b.productName, "pt-BR", { sensitivity: "base" })
  );
}

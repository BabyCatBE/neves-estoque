import { useQuery } from "@tanstack/react-query";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { getProductDetails } from "../api/products";

export function ProductStockUpdatePage() {
  const { productId } = useParams();
  const [searchParams] = useSearchParams();
  const productQuery = useQuery({
    queryKey: ["products", "detail", productId],
    queryFn: () => {
      if (!productId) throw new Error("Produto não encontrado.");
      return getProductDetails(productId);
    },
    enabled: Boolean(productId)
  });

  const backTo = productId ? `/produtos/${productId}` : "/produtos/lista";

  return (
    <AppShell title="Atualizar estoque" showBack backTo={backTo}>
      {searchParams.get("saved") === "1" ? (
        <div className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          Conferência registrada com sucesso. O estoque atual foi recalculado.
        </div>
      ) : null}

      {productQuery.isPending ? (
        <Card className="p-5 text-sm text-zinc-600">Carregando produto…</Card>
      ) : null}

      {productQuery.isError ? (
        <Card className="border-red-200 p-5 text-sm text-red-800">
          Não foi possível carregar este produto.
        </Card>
      ) : null}

      {productQuery.data ? (
        <section>
          <Card className="p-5">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
              Produto
            </p>
            <h2 className="mt-1 text-xl font-semibold text-zinc-950">
              {productQuery.data.name}
            </h2>
            <p className="mt-2 text-sm text-zinc-600">
              Estoque atual:{" "}
              <strong>{formatQuantity(productQuery.data.currentQuantity, productQuery.data.unit)}</strong>
            </p>
          </Card>

          <p className="mt-5 text-sm leading-6 text-zinc-600">
            Escolha como deseja atualizar o estoque.
          </p>

          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <Link to={`/produtos/${productQuery.data.id}/estoque/conferencia`} className="block">
              <InteractiveCard className="h-full p-5">
                <h3 className="text-lg font-semibold text-zinc-950">Conferência</h3>
                <p className="mt-2 text-sm leading-6 text-zinc-600">
                  Registrar uma nova contagem física e substituir o checkpoint atual deste produto.
                </p>
              </InteractiveCard>
            </Link>

            <Link
              to={`/entradas/nova?productId=${encodeURIComponent(productQuery.data.id)}`}
              className="block"
            >
              <InteractiveCard className="h-full p-5">
                <h3 className="text-lg font-semibold text-zinc-950">Entrada</h3>
                <p className="mt-2 text-sm leading-6 text-zinc-600">
                  Registrar recebimento de mercadoria com este produto já selecionado.
                </p>
              </InteractiveCard>
            </Link>
          </div>
        </section>
      ) : null}
    </AppShell>
  );
}

function formatQuantity(value: number | null, unit: string) {
  if (value === null) return "Sem dados";
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(value)} ${unit}`;
}

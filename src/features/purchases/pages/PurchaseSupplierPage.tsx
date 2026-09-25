import { useQuery } from "@tanstack/react-query";
import { useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { SearchClearButton } from "../../../shared/components/ui/SearchClearButton";
import { TextField } from "../../../shared/components/ui/TextField";
import { listActiveProducts } from "../../products/api/products";
import { getSupplierDetails } from "../../suppliers/api/suppliers";
import {
  PurchaseListEditor,
  type PurchaseListEditorHandle
} from "../components/PurchaseListEditor";
import { listSupplierPurchaseProducts } from "../api/purchases";

export function PurchaseSupplierPage() {
  const { supplierId } = useParams();
  const [addOpen, setAddOpen] = useState(false);
  const [productSearch, setProductSearch] = useState("");
  const [manualProductIds, setManualProductIds] = useState<string[]>([]);
  const editorRef = useRef<PurchaseListEditorHandle>(null);

  const supplierQuery = useQuery({
    queryKey: ["suppliers", "detail", supplierId],
    queryFn: () => getSupplierDetails(supplierId ?? ""),
    enabled: Boolean(supplierId)
  });

  const productsQuery = useQuery({
    queryKey: ["purchases", "supplier-products", supplierId],
    queryFn: () => listSupplierPurchaseProducts(supplierId ?? ""),
    enabled: Boolean(supplierId)
  });

  const allProductsQuery = useQuery({
    queryKey: ["products", "active"],
    queryFn: listActiveProducts,
    staleTime: 30_000
  });

  const historicalProductIds = useMemo(
    () => new Set((productsQuery.data ?? []).map((product) => product.productId)),
    [productsQuery.data]
  );

  const manualProducts = useMemo(
    () =>
      (allProductsQuery.data ?? [])
        .filter((product) => manualProductIds.includes(product.id))
        .map((product) => ({
          productId: product.id,
          productName: product.name,
          unit: product.unit,
          currentQuantity: product.currentQuantity,
          isManualAddition: true
        })),
    [allProductsQuery.data, manualProductIds]
  );

  const purchaseItems = useMemo(
    () =>
      [...(productsQuery.data ?? []), ...manualProducts].sort((a, b) => {
        const aRecommended = "projection" in a && a.projection?.status === "recommended";
        const bRecommended = "projection" in b && b.projection?.status === "recommended";
        if (aRecommended !== bRecommended) return aRecommended ? -1 : 1;
        return a.productName.localeCompare(b.productName, "pt-BR", { sensitivity: "base" });
      }),
    [manualProducts, productsQuery.data]
  );

  const availableProducts = useMemo(() => {
    const term = normalize(productSearch);
    const manualIds = new Set(manualProductIds);

    return (allProductsQuery.data ?? [])
      .filter((product) => !historicalProductIds.has(product.id) && !manualIds.has(product.id))
      .filter((product) => !term || normalize(product.name).includes(term))
      .sort((a, b) =>
        a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" })
      )
      .slice(0, 20);
  }, [allProductsQuery.data, historicalProductIds, manualProductIds, productSearch]);

  const addManualProduct = (productId: string) => {
    setManualProductIds((current) =>
      current.includes(productId) ? current : [...current, productId]
    );
    editorRef.current?.selectProduct(productId);
    setProductSearch("");
  };

  const configurationReady =
    supplierQuery.data?.purchaseFrequencyDays !== null &&
    supplierQuery.data?.averageDeliveryDays !== null &&
    supplierQuery.data?.safetyMarginDays !== null;

  return (
    <AppShell title="Compras · Por fornecedor" showBack backTo="/compras/fornecedor">
      <section>
        {supplierQuery.isPending || productsQuery.isPending || allProductsQuery.isPending ? (
          <Card className="p-5 text-sm text-zinc-600">Calculando simulação…</Card>
        ) : null}

        {supplierQuery.isError || productsQuery.isError || allProductsQuery.isError ? (
          <Card className="border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar esta simulação de compra.
          </Card>
        ) : null}

        {supplierQuery.data && productsQuery.data && allProductsQuery.data ? (
          <>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
              Por fornecedor
            </p>
            <h2 className="mt-1 text-2xl font-semibold tracking-tight">{supplierQuery.data.name}</h2>

            {!configurationReady ? (
              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
                Complete frequência de compra, prazo de entrega e margem de segurança no cadastro deste fornecedor para liberar recomendações automáticas.
              </div>
            ) : null}

            <div className="mt-5">
              <Button
                variant="secondary"
                onClick={() => setAddOpen((current) => !current)}
                aria-expanded={addOpen}
              >
                {addOpen ? "Fechar adicionar produto" : "Adicionar outro produto"}
              </Button>
            </div>

            {addOpen ? (
              <Card className="mt-4 p-5">
                <h3 className="font-semibold">Adicionar produto à simulação</h3>
                <p className="mt-1 text-xs leading-5 text-zinc-500">
                  Esta inclusão vale somente para a lista atual e não cria relação permanente com o fornecedor.
                </p>

                <div className="relative mt-4 max-w-md">
                  <TextField
                    id="purchase-add-product-search"
                    label="Pesquisar produto"
                    placeholder="Digite qualquer trecho do nome"
                    value={productSearch}
                    className={productSearch ? "pr-11" : ""}
                    onChange={(event) => setProductSearch(event.target.value)}
                  />
                  {productSearch ? (
                    <SearchClearButton
                      onClear={() => {
                        setProductSearch("");
                        document.getElementById("purchase-add-product-search")?.focus();
                      }}
                    />
                  ) : null}
                </div>

                <div className="mt-4 space-y-2">
                  {availableProducts.map((product) => (
                    <div
                      key={product.id}
                      className="flex flex-col gap-3 rounded-xl border border-zinc-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between"
                    >
                      <div>
                        <p className="font-semibold text-zinc-950">{product.name}</p>
                        <p className="mt-1 text-xs text-zinc-500">
                          {product.unit} · Estoque atual: {formatStock(product.currentQuantity, product.unit)}
                        </p>
                      </div>
                      <Button size="sm" variant="secondary" onClick={() => addManualProduct(product.id)}>
                        Adicionar
                      </Button>
                    </div>
                  ))}

                  {availableProducts.length === 0 ? (
                    <p className="text-sm text-zinc-500">
                      {productSearch
                        ? "Nenhum outro Produto encontrado."
                        : "Todos os Produtos ativos já estão no histórico deste fornecedor ou foram adicionados à simulação."}
                    </p>
                  ) : null}
                </div>
              </Card>
            ) : null}

            <PurchaseListEditor
              ref={editorRef}
              items={purchaseItems}
              orderTitle={`Pedido — ${supplierQuery.data.name}`}
              listTitle="Produtos desta simulação"
              intro="Produtos recomendados aparecem primeiro. A sugestão considera consumo, estoque, próximo ciclo, prazo e margem; qualquer quantidade pode ser alterada manualmente."
              emptyText="Ainda não há Produto ativo nesta simulação."
            />
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function normalize(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase("pt-BR")
    .trim();
}

function formatStock(value: number | null, unit: string) {
  if (value === null) return "Sem dados";
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(Number(value))} ${unit}`;
}

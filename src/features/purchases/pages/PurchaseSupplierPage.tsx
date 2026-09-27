import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { SearchClearButton } from "../../../shared/components/ui/SearchClearButton";
import { TextField } from "../../../shared/components/ui/TextField";
import { normalizeSearchText } from "../../../shared/lib/searchText";
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
  const [pendingManualFocusId, setPendingManualFocusId] = useState<string | null>(null);
  const editorRef = useRef<PurchaseListEditorHandle>(null);
  const restorePickerFocusRef = useRef(true);

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
    const term = normalizeSearchText(productSearch);
    const manualIds = new Set(manualProductIds);

    return (allProductsQuery.data ?? [])
      .filter((product) => !historicalProductIds.has(product.id) && !manualIds.has(product.id))
      .filter((product) => !term || normalizeSearchText(product.name).includes(term))
      .sort((a, b) =>
        a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" })
      );
  }, [allProductsQuery.data, historicalProductIds, manualProductIds, productSearch]);

  useEffect(() => {
    if (!addOpen) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    window.requestAnimationFrame(() => {
      document.getElementById("purchase-add-product-search")?.focus();
    });

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      restorePickerFocusRef.current = true;
      setAddOpen(false);
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;

      if (restorePickerFocusRef.current) {
        window.requestAnimationFrame(() => {
          document.getElementById("purchase-add-product-trigger")?.focus();
        });
      }
    };
  }, [addOpen]);

  useEffect(() => {
    if (!pendingManualFocusId) return;
    if (!purchaseItems.some((item) => item.productId === pendingManualFocusId)) return;

    editorRef.current?.selectProduct(pendingManualFocusId);
    setPendingManualFocusId(null);
  }, [pendingManualFocusId, purchaseItems]);

  const closeProductPicker = () => {
    restorePickerFocusRef.current = true;
    setAddOpen(false);
    setProductSearch("");
  };

  const addManualProduct = (productId: string) => {
    restorePickerFocusRef.current = false;
    setManualProductIds((current) =>
      current.includes(productId) ? current : [...current, productId]
    );
    setProductSearch("");
    setAddOpen(false);
    setPendingManualFocusId(productId);
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
                id="purchase-add-product-trigger"
                variant="secondary"
                onClick={() => {
                  restorePickerFocusRef.current = true;
                  setAddOpen(true);
                }}
                aria-haspopup="dialog"
                aria-expanded={addOpen}
              >
                Adicionar outro produto
              </Button>
            </div>

            {addOpen ? (
              <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="purchase-add-product-title"
                className="fixed inset-0 z-50 flex items-end justify-center bg-black/55 sm:items-center sm:px-4 sm:py-6"
                onMouseDown={(event) => {
                  if (event.target === event.currentTarget) {
                    closeProductPicker();
                  }
                }}
              >
                <Card className="flex max-h-[88dvh] w-full max-w-2xl flex-col overflow-hidden rounded-b-none shadow-xl sm:rounded-2xl">
                  <div className="shrink-0 border-b border-zinc-100 bg-white px-4 py-4 sm:px-5">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <h3
                          id="purchase-add-product-title"
                          className="text-lg font-semibold text-zinc-950"
                        >
                          Adicionar produto à simulação
                        </h3>
                        <p className="mt-1 text-xs leading-5 text-zinc-500">
                          Pesquise ou role a lista. Esta inclusão vale somente para a
                          simulação atual.
                        </p>
                      </div>

                      <button
                        type="button"
                        onClick={closeProductPicker}
                        aria-label="Fechar seleção de produto"
                        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border border-zinc-200 bg-white text-xl text-zinc-600 transition hover:bg-zinc-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600"
                      >
                        ×
                      </button>
                    </div>

                    <div className="relative mt-4">
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
                            document
                              .getElementById("purchase-add-product-search")
                              ?.focus();
                          }}
                        />
                      ) : null}
                    </div>

                    <p className="mt-2 text-xs text-zinc-500">
                      {availableProducts.length}{" "}
                      {availableProducts.length === 1
                        ? "Produto disponível"
                        : "Produtos disponíveis"}
                    </p>
                  </div>

                  <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain bg-zinc-50 p-3 sm:p-4">
                    <div className="space-y-2">
                      {availableProducts.map((product) => (
                        <button
                          key={product.id}
                          type="button"
                          onClick={() => addManualProduct(product.id)}
                          className="flex min-h-16 w-full items-center justify-between gap-3 rounded-xl border border-zinc-200 bg-white p-4 text-left transition hover:border-red-200 hover:bg-red-50/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600"
                        >
                          <div className="min-w-0">
                            <p className="truncate font-semibold text-zinc-950">
                              {product.name}
                            </p>
                            <p className="mt-1 text-xs text-zinc-500">
                              {product.unit} · Estoque atual:{" "}
                              {formatStock(product.currentQuantity, product.unit)}
                            </p>
                          </div>
                          <span className="shrink-0 text-xs font-semibold text-red-700">
                            Adicionar
                          </span>
                        </button>
                      ))}

                      {availableProducts.length === 0 ? (
                        <div className="rounded-xl border border-zinc-200 bg-white p-5 text-center text-sm text-zinc-500">
                          {productSearch
                            ? "Nenhum outro Produto encontrado."
                            : "Todos os Produtos ativos já estão no histórico deste fornecedor ou foram adicionados à simulação."}
                        </div>
                      ) : null}
                    </div>
                  </div>
                </Card>
              </div>
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

function formatStock(value: number | null, unit: string) {
  if (value === null) return "Sem dados";
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(Number(value))} ${unit}`;
}

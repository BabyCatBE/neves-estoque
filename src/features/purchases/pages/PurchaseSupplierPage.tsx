import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { useBlocker, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { getSupplierDetails } from "../../suppliers/api/suppliers";
import { listSupplierPurchaseProducts } from "../api/purchases";

export function PurchaseSupplierPage() {
  const { supplierId } = useParams();
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [invalidIds, setInvalidIds] = useState<Set<string>>(() => new Set());
  const [copyError, setCopyError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

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

  const dirty =
    selected.size > 0 ||
    Object.values(quantities).some((quantity) => quantity.trim().length > 0);

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty && currentLocation.pathname !== nextLocation.pathname
  );

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
    };

    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => {
      window.removeEventListener("beforeunload", handleBeforeUnload);
      if (toastTimer.current) clearTimeout(toastTimer.current);
    };
  }, [dirty]);

  const selectedProducts = useMemo(
    () =>
      (productsQuery.data ?? []).filter((product) => selected.has(product.productId)),
    [productsQuery.data, selected]
  );

  const toggleProduct = (productId: string) => {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(productId)) {
        next.delete(productId);
        setInvalidIds((invalid) => {
          const updated = new Set(invalid);
          updated.delete(productId);
          return updated;
        });
      } else {
        next.add(productId);
      }
      return next;
    });
    setCopyError(null);
  };

  const updateQuantity = (productId: string, value: string) => {
    setQuantities((current) => ({ ...current, [productId]: value }));
    setInvalidIds((current) => {
      const next = new Set(current);
      next.delete(productId);
      return next;
    });
    setCopyError(null);
  };

  const stepQuantity = (productId: string, direction: 1 | -1) => {
    const currentValue = parseQuantity(quantities[productId] ?? "") ?? 0;
    const nextValue = Math.max(0, currentValue + direction);
    updateQuantity(productId, formatEditableQuantity(nextValue));
  };

  const focusNextSelectedQuantity = (productId: string) => {
    const products = productsQuery.data ?? [];
    const currentIndex = products.findIndex((product) => product.productId === productId);
    if (currentIndex < 0) return;

    for (let index = currentIndex + 1; index < products.length; index += 1) {
      const nextProduct = products[index];
      if (nextProduct && selected.has(nextProduct.productId)) {
        document.getElementById(`purchase-quantity-${nextProduct.productId}`)?.focus();
        return;
      }
    }
  };

  const buildOrderText = () => {
    setCopyError(null);

    if (!selectedProducts.length) {
      setCopyError("Selecione pelo menos um produto.");
      return null;
    }

    const invalidProductIds = new Set<string>();
    for (const product of selectedProducts) {
      const value = parseQuantity(quantities[product.productId] ?? "");
      if (value === null || value <= 0) invalidProductIds.add(product.productId);
    }

    if (invalidProductIds.size > 0) {
      setInvalidIds(invalidProductIds);
      setCopyError("Informe uma quantidade maior que zero para todos os itens selecionados.");
      return null;
    }

    const supplierName = supplierQuery.data?.name ?? "Fornecedor";
    const lines = selectedProducts.map((product) => {
      const quantity = normalizeQuantityForCopy(quantities[product.productId] ?? "");
      return `${product.productName} — ${quantity} — ${product.unit}`;
    });

    return [`Pedido — ${supplierName}`, "", ...lines].join("\n");
  };

  const showToast = (message: string) => {
    setToastMessage(message);
    if (toastTimer.current) clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToastMessage(null), 2200);
  };

  const copyOrder = async () => {
    const text = buildOrderText();
    if (!text) return;

    try {
      await navigator.clipboard.writeText(text);
      showToast("Texto copiado");
    } catch {
      setCopyError("Não foi possível copiar o pedido neste dispositivo.");
    }
  };

  const shareOrder = async () => {
    const text = buildOrderText();
    if (!text) return;

    if (typeof navigator.share !== "function") {
      try {
        await navigator.clipboard.writeText(text);
        showToast("Compartilhamento indisponível · texto copiado");
      } catch {
        setCopyError("O compartilhamento não está disponível neste navegador.");
      }
      return;
    }

    try {
      await navigator.share({
        title: `Pedido — ${supplierQuery.data?.name ?? "Fornecedor"}`,
        text
      });
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setCopyError("Não foi possível compartilhar o pedido neste dispositivo.");
    }
  };

  return (
    <AppShell title="Compras · Por fornecedor" showBack backTo="/compras/fornecedor">
      <section>
        {supplierQuery.isPending || productsQuery.isPending ? (
          <Card className="p-5 text-sm text-zinc-600">Carregando simulação…</Card>
        ) : null}

        {supplierQuery.isError || productsQuery.isError ? (
          <Card className="border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar esta simulação de compra.
          </Card>
        ) : null}

        {supplierQuery.data && productsQuery.data ? (
          <>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
              Por fornecedor
            </p>
            <h2 className="mt-1 text-2xl font-semibold tracking-tight">
              {supplierQuery.data.name}
            </h2>
            <p className="mt-2 text-sm text-zinc-600">
              Selecione os itens e informe manualmente quanto deseja comprar.
            </p>

            <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
              A recomendação automática ainda não está ativa. Nesta etapa, a quantidade é sempre manual.
            </div>

            {productsQuery.data.length === 0 ? (
              <Card className="mt-5 p-6 text-center text-sm text-zinc-600">
                Ainda não há Produto ativo com Entrada válida registrada para este fornecedor.
              </Card>
            ) : (
              <Card className="mt-5 overflow-hidden">
                <div className="border-b border-zinc-100 px-5 py-4">
                  <h3 className="font-semibold">Produtos já comprados deste fornecedor</h3>
                  <p className="mt-1 text-xs text-zinc-500">Todos começam desmarcados.</p>
                </div>

                <div className="divide-y divide-zinc-100">
                  {productsQuery.data.map((product) => {
                    const checked = selected.has(product.productId);
                    const hasInvalidQuantity = invalidIds.has(product.productId);
                    return (
                      <div
                        key={product.productId}
                        className="grid gap-3 px-5 py-4 md:grid-cols-[42px_minmax(0,1fr)_120px_160px] md:items-center"
                      >
                        <label className="flex items-center">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleProduct(product.productId)}
                            className="h-5 w-5 accent-red-700"
                            aria-label={`Selecionar ${product.productName}`}
                          />
                        </label>

                        <div>
                          <p className="font-semibold text-zinc-950">{product.productName}</p>
                          <p className="mt-1 text-xs text-zinc-500">{product.unit}</p>
                        </div>

                        <div>
                          <p className="text-[10px] font-semibold uppercase tracking-wide text-zinc-400">
                            Estoque atual
                          </p>
                          <p className="mt-1 text-sm font-semibold text-zinc-900">
                            {formatQuantity(product.currentQuantity, product.unit)}
                          </p>
                        </div>

                        <div>
                          <span className="text-[10px] font-semibold uppercase tracking-wide text-zinc-400">
                            Qnt. a comprar
                          </span>
                          <div className="mt-1 grid grid-cols-[44px_minmax(72px,1fr)_44px] items-stretch gap-1">
                            <button
                              type="button"
                              disabled={!checked}
                              onClick={() => stepQuantity(product.productId, 1)}
                              aria-label={`Aumentar quantidade de ${product.productName}`}
                              className="min-h-11 rounded-xl border border-red-200 bg-white text-xl font-semibold text-red-700 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 disabled:border-zinc-200 disabled:bg-zinc-100 disabled:text-zinc-400"
                            >
                              +
                            </button>
                            <input
                              id={`purchase-quantity-${product.productId}`}
                              type="text"
                              inputMode="decimal"
                              enterKeyHint="next"
                              value={quantities[product.productId] ?? ""}
                              disabled={!checked}
                              onChange={(event) =>
                                updateQuantity(product.productId, event.target.value)
                              }
                              onKeyDown={(event) => {
                                if (!checked) return;

                                if (event.key === "ArrowUp") {
                                  event.preventDefault();
                                  stepQuantity(product.productId, 1);
                                  return;
                                }

                                if (event.key === "ArrowDown") {
                                  event.preventDefault();
                                  stepQuantity(product.productId, -1);
                                  return;
                                }

                                if (event.key === "Enter") {
                                  event.preventDefault();
                                  focusNextSelectedQuantity(product.productId);
                                }
                              }}
                              placeholder={checked ? "Ex.: 5" : "Selecione"}
                              className={`min-h-11 w-full rounded-xl border bg-white px-2 py-2 text-center text-sm font-semibold outline-none transition disabled:bg-zinc-100 disabled:text-zinc-400 ${hasInvalidQuantity
                                ? "border-red-400 focus:border-red-500 focus:ring-2 focus:ring-red-100"
                                : "border-zinc-300 focus:border-red-500 focus:ring-2 focus:ring-red-100"}`}
                            />
                            <button
                              type="button"
                              disabled={!checked}
                              onClick={() => stepQuantity(product.productId, -1)}
                              aria-label={`Reduzir quantidade de ${product.productName}`}
                              className="min-h-11 rounded-xl border border-red-200 bg-white text-xl font-semibold text-red-700 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 disabled:border-zinc-200 disabled:bg-zinc-100 disabled:text-zinc-400"
                            >
                              −
                            </button>
                          </div>
                          {hasInvalidQuantity ? (
                            <span className="mt-1 block text-xs font-medium text-red-700">
                              Informe uma quantidade maior que zero.
                            </span>
                          ) : null}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </Card>
            )}

            <div className="mt-5 flex flex-wrap items-center gap-3">
              <Button onClick={() => void copyOrder()} disabled={productsQuery.data.length === 0}>
                Copiar texto
              </Button>
              <Button
                variant="secondary"
                onClick={() => void shareOrder()}
                disabled={productsQuery.data.length === 0}
              >
                Compartilhar texto
              </Button>
              <span className="text-sm text-zinc-500">
                {selected.size} {selected.size === 1 ? "item selecionado" : "itens selecionados"}
              </span>
            </div>

            {copyError ? (
              <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
                {copyError}
              </div>
            ) : null}
          </>
        ) : null}
      </section>

      <ConfirmDialog
        open={blocker.state === "blocked"}
        variant="warning"
        title="Sair desta lista?"
        description="Esta lista não será salva. Deseja sair mesmo assim?"
        confirmLabel="Sair mesmo assim"
        onCancel={() => blocker.reset?.()}
        onConfirm={() => blocker.proceed?.()}
      />

      {toastMessage ? (
        <div
          role="status"
          className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-xl bg-zinc-950 px-4 py-3 text-sm font-semibold text-white shadow-lg"
        >
          {toastMessage}
        </div>
      ) : null}
    </AppShell>
  );
}

function parseQuantity(value: string) {
  const normalized = value.trim().replace(",", ".");
  if (!normalized) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function formatEditableQuantity(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 2,
    useGrouping: false
  }).format(value);
}

function normalizeQuantityForCopy(value: string) {
  const parsed = parseQuantity(value);
  if (parsed === null) return value.trim();
  return new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(parsed);
}

function formatQuantity(value: number | null, unit: string) {
  if (value === null) return "Sem dados";
  return `${new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 2
  }).format(Number(value))} ${unit}`;
}

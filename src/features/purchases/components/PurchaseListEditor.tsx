import { useEffect, useMemo, useRef, useState } from "react";
import { useBlocker } from "react-router-dom";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";

export type PurchaseListItem = {
  productId: string;
  productName: string;
  unit: string;
  currentQuantity: number | null;
};

type Props = {
  items: PurchaseListItem[];
  orderTitle: string;
  listTitle: string;
  intro: string;
  emptyText: string;
  warning?: string;
};

export function PurchaseListEditor({
  items,
  orderTitle,
  listTitle,
  intro,
  emptyText,
  warning = "A recomendação automática ainda não está ativa. Nesta etapa, a quantidade é sempre manual."
}: Props) {
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [invalidIds, setInvalidIds] = useState<Set<string>>(() => new Set());
  const [actionError, setActionError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

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
    () => items.filter((product) => selected.has(product.productId)),
    [items, selected]
  );

  const clearInvalid = (productId: string) => {
    setInvalidIds((current) => {
      const next = new Set(current);
      next.delete(productId);
      return next;
    });
  };

  const toggleProduct = (productId: string) => {
    const willSelect = !selected.has(productId);

    setSelected((current) => {
      const next = new Set(current);
      if (next.has(productId)) {
        next.delete(productId);
        clearInvalid(productId);
      } else {
        next.add(productId);
      }
      return next;
    });
    setActionError(null);

    if (willSelect && shouldAutoFocusQuantity()) {
      window.setTimeout(() => {
        document.getElementById(`purchase-quantity-${productId}`)?.focus();
      }, 0);
    }
  };

  const updateQuantity = (productId: string, value: string) => {
    setQuantities((current) => ({ ...current, [productId]: value }));
    clearInvalid(productId);
    setActionError(null);
  };

  const stepQuantity = (productId: string, direction: 1 | -1) => {
    const currentValue = parseQuantity(quantities[productId] ?? "") ?? 0;
    const nextValue = Math.max(0, currentValue + direction);
    updateQuantity(productId, formatEditableQuantity(nextValue));
  };

  const focusNextSelectedQuantity = (productId: string) => {
    const currentIndex = items.findIndex((product) => product.productId === productId);
    if (currentIndex < 0) return;

    for (let index = currentIndex + 1; index < items.length; index += 1) {
      const nextProduct = items[index];
      if (nextProduct && selected.has(nextProduct.productId)) {
        document.getElementById(`purchase-quantity-${nextProduct.productId}`)?.focus();
        return;
      }
    }
  };

  const buildOrderText = () => {
    setActionError(null);

    if (!selectedProducts.length) {
      setActionError("Selecione pelo menos um produto.");
      return null;
    }

    const invalidProductIds = new Set<string>();
    for (const product of selectedProducts) {
      const value = parseQuantity(quantities[product.productId] ?? "");
      if (value === null || value <= 0) invalidProductIds.add(product.productId);
    }

    if (invalidProductIds.size > 0) {
      setInvalidIds(invalidProductIds);
      setActionError("Informe uma quantidade maior que zero para todos os itens selecionados.");
      return null;
    }

    const lines = selectedProducts.map((product) => {
      const quantity = normalizeQuantityForCopy(quantities[product.productId] ?? "");
      return `${product.productName} — ${quantity} — ${product.unit}`;
    });

    return [orderTitle, "", ...lines].join("\n");
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
      setActionError("Não foi possível copiar o pedido neste dispositivo.");
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
        setActionError("O compartilhamento não está disponível neste navegador.");
      }
      return;
    }

    try {
      await navigator.share({ title: orderTitle, text });
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setActionError("Não foi possível compartilhar o pedido neste dispositivo.");
    }
  };

  return (
    <>
      <p className="mt-2 text-sm text-zinc-600">{intro}</p>

      {warning ? (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
          {warning}
        </div>
      ) : null}

      {items.length === 0 ? (
        <Card className="mt-5 p-6 text-center text-sm text-zinc-600">{emptyText}</Card>
      ) : (
        <Card className="mt-5 overflow-hidden">
          <div className="border-b border-zinc-100 px-5 py-4">
            <h3 className="font-semibold">{listTitle}</h3>
            <p className="mt-1 text-xs text-zinc-500">Todos começam desmarcados.</p>
          </div>

          <div className="divide-y divide-zinc-100">
            {items.map((product) => {
              const checked = selected.has(product.productId);
              const hasInvalidQuantity = invalidIds.has(product.productId);

              return (
                <div
                  key={product.productId}
                  className="grid gap-3 px-5 py-4 md:grid-cols-[42px_minmax(0,1fr)_120px_188px] md:items-center"
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
                        onChange={(event) => updateQuantity(product.productId, event.target.value)}
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
        <Button onClick={() => void copyOrder()} disabled={items.length === 0}>
          Copiar texto
        </Button>
        <Button variant="secondary" onClick={() => void shareOrder()} disabled={items.length === 0}>
          Compartilhar texto
        </Button>
        <span className="text-sm text-zinc-500">
          {selected.size} {selected.size === 1 ? "item selecionado" : "itens selecionados"}
        </span>
      </div>

      {actionError ? (
        <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {actionError}
        </div>
      ) : null}

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
    </>
  );
}

function shouldAutoFocusQuantity() {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return true;
  return !window.matchMedia("(pointer: coarse)").matches;
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
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(Number(value))} ${unit}`;
}

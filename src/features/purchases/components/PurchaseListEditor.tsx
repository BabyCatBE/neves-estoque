import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState
} from "react";
import { useBlocker } from "react-router-dom";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { copyText } from "../../../shared/lib/copyText";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import type { PurchaseProjection } from "../lib/purchaseProjection";

export type PurchaseListItem = {
  productId: string;
  productName: string;
  unit: string;
  currentQuantity: number | null;
  isManualAddition?: boolean;
  projection?: PurchaseProjection;
};

type Props = {
  items: PurchaseListItem[];
  orderTitle: string;
  listTitle: string;
  intro: string;
  emptyText: string;
  warning?: string | null;
};

export type PurchaseListEditorHandle = {
  selectProduct: (productId: string) => void;
};

export const PurchaseListEditor = forwardRef<PurchaseListEditorHandle, Props>(
function PurchaseListEditor({
  items,
  orderTitle,
  listTitle,
  intro,
  emptyText,
  warning = null
}: Props, ref) {
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [invalidIds, setInvalidIds] = useState<Set<string>>(() => new Set());
  const [actionError, setActionError] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<"copy" | "share" | null>(null);
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

  const applySuggestionIfAvailable = useCallback((productId: string) => {
    const product = items.find((item) => item.productId === productId);
    const suggestion =
      product?.projection?.status === "recommended"
        ? product.projection.suggestedQuantity
        : null;
    if (suggestion === null || suggestion === undefined || suggestion <= 0) return;

    setQuantities((current) => {
      if ((current[productId] ?? "").trim()) return current;
      return { ...current, [productId]: formatEditableQuantity(suggestion) };
    });
  }, [items]);

  useImperativeHandle(
    ref,
    () => ({
      selectProduct(productId: string) {
        setSelected((current) => {
          const next = new Set(current);
          next.add(productId);
          return next;
        });
        applySuggestionIfAvailable(productId);
        setActionError(null);

        focusQuantity(productId, true);
      }
    }),
    [applySuggestionIfAvailable]
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

    if (willSelect) {
      applySuggestionIfAvailable(productId);
    }

    if (willSelect && shouldAutoFocusQuantity()) {
      focusQuantity(productId, false);
    }
  };

  const updateQuantity = (productId: string, value: string) => {
    setQuantities((current) => ({ ...current, [productId]: value }));
    setSelected((current) => {
      const next = new Set(current);
      const quantity = parseQuantity(value);
      if (quantity !== null && quantity > 0) next.add(productId);
      else next.delete(productId);
      return next;
    });
    clearInvalid(productId);
    setActionError(null);
  };

  const stepQuantity = (productId: string, direction: 1 | -1) => {
    const currentValue = parseQuantity(quantities[productId] ?? "") ?? 0;
    const nextValue = Math.max(0, currentValue + direction);
    updateQuantity(productId, formatEditableQuantity(nextValue));
  };

  const focusNextQuantity = (productId: string) => {
    const currentIndex = items.findIndex((product) => product.productId === productId);
    if (currentIndex < 0) return;
    const nextProduct = items[currentIndex + 1];
    if (nextProduct) focusQuantity(nextProduct.productId, true);
    else document.getElementById(`purchase-quantity-${productId}`)?.blur();
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
    if (!text || pendingAction) return;

    setPendingAction("copy");
    setActionError(null);
    try {
      await copyText(text);
      showToast("Texto copiado.");
    } catch {
      setActionError("Não foi possível copiar o texto neste navegador.");
    } finally {
      setPendingAction(null);
    }
  };

  const shareOrder = async () => {
    const text = buildOrderText();
    if (!text || pendingAction) return;

    setPendingAction("share");
    setActionError(null);

    try {
      if (typeof navigator.share === "function") {
        try {
          await navigator.share({ title: orderTitle, text });
          showToast("Lista compartilhada.");
          return;
        } catch (error) {
          if (error instanceof DOMException && error.name === "AbortError") return;
          // Em contextos HTTP/LAN a API pode existir sem conseguir concluir.
          // Nesse caso, caímos para a cópia compatível.
        }
      }

      await copyText(text);
      showToast("Compartilhamento indisponível · texto copiado.");
    } catch {
      setActionError(
        "Não foi possível compartilhar nem copiar o texto neste navegador."
      );
    } finally {
      setPendingAction(null);
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
            <p className="mt-1 text-xs text-zinc-500">
              Todos começam desmarcados. Digite uma quantidade maior que zero para selecionar. Ao marcar uma recomendação, a sugestão é preenchida e continua editável.
            </p>
          </div>

          <div className="divide-y divide-zinc-100">
            {items.map((product) => {
              const checked = selected.has(product.productId);
              const hasInvalidQuantity = invalidIds.has(product.productId);
              const recommended = product.projection?.status === "recommended";

              return (
                <div
                  key={product.productId}
                  className={`grid gap-3 px-5 py-4 md:grid-cols-[42px_minmax(0,1fr)_120px_188px] md:items-center ${recommended ? "bg-red-50/40" : ""}`}
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
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-semibold text-zinc-950">{product.productName}</p>
                      {recommended ? (
                        <span className="inline-flex rounded-full bg-red-100 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-red-800">
                          Recomendado
                        </span>
                      ) : null}
                      {product.isManualAddition ? (
                        <span className="inline-flex rounded-full bg-red-50 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-red-700">
                          Nesta simulação
                        </span>
                      ) : null}
                      {product.projection ? <RiskBadge risk={product.projection.risk} /> : null}
                    </div>
                    <p className="mt-1 text-xs text-zinc-500">{product.unit}</p>
                    {product.projection ? (
                      <p className={`mt-1 text-xs ${recommended ? "font-semibold text-red-700" : "text-zinc-500"}`}>
                        {projectionMessage(product)}
                      </p>
                    ) : null}
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
                        onClick={() => stepQuantity(product.productId, -1)}
                        aria-label={`Reduzir quantidade de ${product.productName}`}
                        className="min-h-11 rounded-xl border border-red-200 bg-white text-xl font-semibold text-red-700 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 disabled:border-zinc-200 disabled:bg-zinc-100 disabled:text-zinc-400"
                      >
                        −
                      </button>
                      <input
                        id={`purchase-quantity-${product.productId}`}
                        type="text"
                        inputMode="decimal"
                        aria-label={`Quantidade de ${product.productName}`}
                        aria-invalid={hasInvalidQuantity}
                        enterKeyHint={product === items[items.length - 1] ? "done" : "next"}
                        value={quantities[product.productId] ?? ""}
                        onChange={(event) => updateQuantity(product.productId, event.target.value)}
                        onKeyDown={(event) => {
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
                            focusNextQuantity(product.productId);
                          }
                        }}
                        placeholder="Ex.: 5"
                        className={`min-h-11 w-full rounded-xl border bg-white px-2 py-2 text-center text-sm font-semibold outline-none transition disabled:bg-zinc-100 disabled:text-zinc-400 ${hasInvalidQuantity
                          ? "border-red-400 focus:border-red-500 focus:ring-2 focus:ring-red-100"
                          : "border-zinc-300 focus:border-red-500 focus:ring-2 focus:ring-red-100"}`}
                      />
                      <button
                        type="button"
                        onClick={() => stepQuantity(product.productId, 1)}
                        aria-label={`Aumentar quantidade de ${product.productName}`}
                        className="min-h-11 rounded-xl border border-red-200 bg-white text-xl font-semibold text-red-700 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 disabled:border-zinc-200 disabled:bg-zinc-100 disabled:text-zinc-400"
                      >
                        +
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
        <Button
          onClick={() => void copyOrder()}
          disabled={items.length === 0 || Boolean(pendingAction)}
          isLoading={pendingAction === "copy"}
          loadingLabel="Copiando…"
        >
          Copiar texto
        </Button>
        <Button
          variant="secondary"
          onClick={() => void shareOrder()}
          disabled={items.length === 0 || Boolean(pendingAction)}
          isLoading={pendingAction === "share"}
          loadingLabel="Compartilhando…"
        >
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
});

function focusQuantity(productId: string, force: boolean) {
  if (!force && !shouldAutoFocusQuantity()) return;

  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      const input = document.getElementById(
        `purchase-quantity-${productId}`
      ) as HTMLInputElement | null;

      if (!input || input.disabled) return;

      input.scrollIntoView({
        behavior: "smooth",
        block: "center",
        inline: "nearest"
      });
      input.focus({ preventScroll: true });
      input.select();
    });
  });
}

function shouldAutoFocusQuantity() {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return true;
  }
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

function projectionMessage(product: PurchaseListItem) {
  const projection = product.projection;
  if (!projection) return "";

  if (
    projection.status === "recommended" &&
    projection.suggestedQuantity !== null
  ) {
    return `Sugestão: ${formatQuantity(projection.suggestedQuantity, product.unit)} · cobertura planejada: ${formatDays(projection.cycleDays)}`;
  }

  if (projection.status === "stock_sufficient") return "Estoque suficiente para o ciclo planejado.";
  if (projection.status === "insufficient_history") return "Histórico insuficiente para recomendação automática.";
  if (projection.status === "missing_stock") return "Estoque atual ainda não está contabilizado.";
  if (projection.status === "missing_supplier") return "Sem fornecedor ativo de referência para projetar.";
  if (projection.status === "missing_supplier_config") return "Complete frequência, prazo e margem do fornecedor.";
  if (projection.status === "no_consumption") return "Sem consumo médio positivo para sugerir compra.";
  if (projection.status === "other_supplier") return "A recomendação automática pertence ao fornecedor da Entrada mais recente.";
  return "";
}

function RiskBadge({ risk }: { risk: PurchaseProjection["risk"] }) {
  if (risk === "risk") {
    return (
      <span className="inline-flex rounded-full bg-red-100 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-red-800">
        Risco de falta
      </span>
    );
  }

  if (risk === "vulnerable") {
    return (
      <span className="inline-flex rounded-full bg-amber-100 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-amber-800">
        Vulnerável a atraso
      </span>
    );
  }

  if (risk === "protected") {
    return (
      <span className="inline-flex rounded-full bg-emerald-50 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-emerald-800">
        Protegido
      </span>
    );
  }

  return null;
}

function formatDays(value: number | null) {
  if (value === null) return "—";
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(value)} dias`;
}

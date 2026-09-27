import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { SearchClearButton } from "../../../shared/components/ui/SearchClearButton";
import {
  listCurrentStock,
  type CurrentStockItem
} from "../api/stock";
import {
  buildAlphabeticalStockView,
  buildCategoryStockView,
  buildSupplierStockView,
  calculateStockValueSummary
} from "../lib/stockView";

const stockKey = ["stock", "current"] as const;
type ViewMode = "category" | "supplier" | "alphabetical";

export function StockCurrentPage() {
  const [search, setSearch] = useState("");
  const [viewMode, setViewMode] = useState<ViewMode>("category");
  const [showValues, setShowValues] = useState(false);
  const [searchFeedback, setSearchFeedback] = useState(false);
  const searchFeedbackTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const stockQuery = useQuery({
    queryKey: stockKey,
    queryFn: listCurrentStock,
    staleTime: 15_000
  });

  useEffect(() => {
    return () => {
      if (searchFeedbackTimer.current) clearTimeout(searchFeedbackTimer.current);
    };
  }, []);

  const pulseSearchFeedback = () => {
    setSearchFeedback(true);
    if (searchFeedbackTimer.current) clearTimeout(searchFeedbackTimer.current);
    searchFeedbackTimer.current = setTimeout(() => setSearchFeedback(false), 400);
  };

  const categoryView = useMemo(
    () =>
      buildCategoryStockView(
        stockQuery.data?.categories ?? [],
        stockQuery.data?.items ?? [],
        search
      ),
    [search, stockQuery.data]
  );

  const supplierView = useMemo(
    () =>
      buildSupplierStockView(
        stockQuery.data?.suppliers ?? [],
        stockQuery.data?.items ?? [],
        search
      ),
    [search, stockQuery.data]
  );

  const alphabeticalItems = useMemo(
    () => buildAlphabeticalStockView(stockQuery.data?.items ?? [], search),
    [search, stockQuery.data]
  );

  const valueSummary = useMemo(
    () => calculateStockValueSummary(stockQuery.data?.items ?? []),
    [stockQuery.data]
  );
  const pendingConferenceCount = useMemo(
    () => (stockQuery.data?.items ?? []).filter((item) => item.stockRequiresConference).length,
    [stockQuery.data]
  );

  const searching = search.trim().length > 0;
  const categoryVisibleCount =
    categoryView.groups.reduce((total, group) => total + group.items.length, 0) +
    categoryView.pending.length;
  const supplierVisibleCount =
    supplierView.groups.reduce((total, group) => total + group.items.length, 0) +
    supplierView.withoutSupplier.length +
    supplierView.unavailableSupplier.length;
  const visibleCount =
    viewMode === "category"
      ? categoryVisibleCount
      : viewMode === "supplier"
        ? supplierVisibleCount
        : alphabeticalItems.length;

  return (
    <AppShell title="Estoque Atual" showBack backTo="/">
      <section>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
              Posição atual
            </p>
            <h2 className="mt-1 text-2xl font-semibold tracking-tight">
              Estoque atual
            </h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-zinc-600">
              Calculado pela última Conferência física válida + Entradas posteriores.
            </p>
          </div>

          <Button
            variant={showValues ? "primary" : "secondary"}
            onClick={() => setShowValues((current) => !current)}
            aria-pressed={showValues}
            title={showValues ? "Ocultar valores" : "Mostrar valores"}
            className="gap-2 self-start"
          >
            <EyeMoneyIcon />
            {showValues ? "Ocultar valores" : "Mostrar valores"}
          </Button>
        </div>

        <div className="mt-5 max-w-2xl">
          <label className="block" htmlFor="stock-search">
            <span className="text-sm font-medium text-zinc-800">Pesquisar</span>
            <div className="relative mt-2">
              <SearchIcon />
              <input
                id="stock-search"
                type="text"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    pulseSearchFeedback();
                  }
                }}
                placeholder="Buscar produto por nome"
                className="min-h-11 w-full rounded-xl border border-zinc-300 bg-white py-2 pl-10 pr-20 text-sm text-zinc-900 outline-none transition placeholder:text-zinc-400 focus:border-red-500 focus:ring-2 focus:ring-red-100"
              />
              {searchFeedback ? (
                <span
                  aria-label="Pesquisa atualizada"
                  className={`absolute top-1/2 inline-block h-5 w-5 -translate-y-1/2 animate-spin rounded-full border-2 border-zinc-300 border-t-red-700 ${search ? "right-10" : "right-3"}`}
                />
              ) : null}
              {search ? (
                <SearchClearButton
                  placement="input"
                  onClear={() => {
                    setSearch("");
                    document.getElementById("stock-search")?.focus();
                  }}
                />
              ) : null}
            </div>
          </label>
        </div>

        <div className="mt-4">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-500">
            Organizar por
          </p>
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant={viewMode === "category" ? "primary" : "secondary"}
              onClick={() => setViewMode("category")}
            >
              Por categoria
            </Button>
            <Button
              size="sm"
              variant={viewMode === "supplier" ? "primary" : "secondary"}
              onClick={() => setViewMode("supplier")}
            >
              Por fornecedor
            </Button>
            <Button
              size="sm"
              variant={viewMode === "alphabetical" ? "primary" : "secondary"}
              onClick={() => setViewMode("alphabetical")}
            >
              Alfabética
            </Button>
          </div>
        </div>

        {showValues && !stockQuery.isPending && !stockQuery.isError ? (
          <Card className="mt-5 p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">
              Valor total do estoque
            </p>
            <p className="mt-1 text-xl font-semibold text-zinc-950">
              {formatMoney(valueSummary.totalKnown)}
              {valueSummary.hasMissingPrice ? " *" : ""}
            </p>
            {valueSummary.hasMissingPrice ? (
              <p className="mt-2 text-xs text-amber-700">
                * Existem produtos com estoque positivo e sem preço informado.
              </p>
            ) : null}
            {pendingConferenceCount > 0 ? (
              <p className="mt-2 text-xs text-amber-700">
                {pendingConferenceCount} {pendingConferenceCount === 1 ? "produto aguarda" : "produtos aguardam"} Conferência física após mescla e não {pendingConferenceCount === 1 ? "entra" : "entram"} no valor conhecido.
              </p>
            ) : null}
          </Card>
        ) : null}

        {stockQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">
            Carregando estoque atual…
          </Card>
        ) : null}

        {stockQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar o Estoque Atual.
          </Card>
        ) : null}

        {!stockQuery.isPending && !stockQuery.isError ? (
          <>
            {visibleCount === 0 ? (
              <Card className="mt-6 p-5 text-sm text-zinc-600">
                {searching
                  ? "Nenhum produto encontrado para esta pesquisa."
                  : "Ainda não há produtos para exibir no Estoque Atual."}
              </Card>
            ) : null}

            {visibleCount > 0 && viewMode === "category" ? (
              <div className="mt-6 space-y-3">
                {categoryView.pending.length > 0 ? (
                  <StockGroup
                    name="Cadastro pendente"
                    subtitle="Falta definir categoria"
                    items={categoryView.pending}
                    forceOpen={searching}
                    showValues={showValues}
                    accentClassName="bg-amber-500"
                  />
                ) : null}

                {categoryView.groups.map((group) => (
                  <StockGroup
                    key={group.id}
                    name={group.name}
                    items={group.items}
                    forceOpen={searching}
                    showValues={showValues}
                  />
                ))}
              </div>
            ) : null}

            {visibleCount > 0 && viewMode === "supplier" ? (
              <div className="mt-6 space-y-3">
                {supplierView.groups.map((group) => (
                  <StockGroup
                    key={group.id}
                    name={group.name}
                    subtitle="Fornecedor da Entrada mais recente"
                    items={group.items}
                    forceOpen={searching}
                    showValues={showValues}
                  />
                ))}

                {supplierView.unavailableSupplier.length > 0 ? (
                  <StockGroup
                    name="Fornecedor indisponível"
                    subtitle="Há referência histórica, mas o cadastro não pôde ser carregado"
                    items={supplierView.unavailableSupplier}
                    forceOpen={searching}
                    showValues={showValues}
                    accentClassName="bg-amber-500"
                  />
                ) : null}

                {supplierView.withoutSupplier.length > 0 ? (
                  <StockGroup
                    name="Sem fornecedor"
                    subtitle="Sem Entrada válida registrada"
                    items={supplierView.withoutSupplier}
                    forceOpen={searching}
                    showValues={showValues}
                    accentClassName="bg-zinc-400"
                  />
                ) : null}
              </div>
            ) : null}

            {visibleCount > 0 && viewMode === "alphabetical" ? (
              <Card className="mt-6 overflow-hidden">
                <div className="border-b border-zinc-100 px-5 py-4">
                  <h3 className="font-semibold text-zinc-950">Ordem alfabética</h3>
                  <p className="mt-1 text-xs text-zinc-500">
                    {alphabeticalItems.length}{" "}
                    {alphabeticalItems.length === 1 ? "produto" : "produtos"}
                  </p>
                </div>
                <div>
                  {alphabeticalItems.map((item) => (
                    <StockRow
                      key={item.productId}
                      item={item}
                      showValues={showValues}
                    />
                  ))}
                </div>
              </Card>
            ) : null}
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function StockGroup({
  name,
  subtitle,
  items,
  forceOpen,
  showValues,
  accentClassName = "bg-red-700"
}: {
  name: string;
  subtitle?: string;
  items: CurrentStockItem[];
  forceOpen: boolean;
  showValues: boolean;
  accentClassName?: string;
}) {
  return (
    <details
      open={forceOpen}
      className="group relative overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm"
    >
      <span
        aria-hidden="true"
        className={`absolute inset-y-0 left-0 w-1 ${accentClassName}`}
      />
      <summary className="flex min-h-16 cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 pl-6 marker:hidden">
        <div>
          <h3 className="font-semibold text-zinc-950">{name}</h3>
          <p className="mt-1 text-xs text-zinc-500">
            {subtitle ? `${subtitle} · ` : ""}
            {items.length} {items.length === 1 ? "produto" : "produtos"}
          </p>
        </div>
        <ChevronIcon />
      </summary>

      <div className="border-t border-zinc-100">
        {items.map((item) => (
          <StockRow
            key={item.productId}
            item={item}
            showValues={showValues}
          />
        ))}
      </div>
    </details>
  );
}

function StockRow({
  item,
  showValues
}: {
  item: CurrentStockItem;
  showValues: boolean;
}) {
  return (
    <Link
      to={`/produtos/${item.productId}`}
      className="block border-b border-zinc-100 px-5 py-3 pl-6 transition last:border-b-0 hover:bg-red-50/60"
    >
      <div
        className={
          showValues
            ? "grid gap-3 md:grid-cols-[minmax(0,1fr)_120px_140px_150px] md:items-center"
            : "grid min-h-8 grid-cols-[1fr_auto] items-center gap-4"
        }
      >
        <span className="font-medium text-zinc-900">{item.productName}</span>

        {showValues ? (
          <>
            <StockMetric
              label="Quantidade"
              value={
                item.stockRequiresConference
                  ? "Conferência necessária"
                  : formatQuantity(item.currentQuantity, item.unit)
              }
            />
            <StockMetric
              label="Preço unitário"
              value={formatPrice(item.currentPrice)}
            />
            <StockMetric
              label="Valor em estoque"
              value={formatItemValue(item)}
            />
          </>
        ) : (
          <span className="text-right font-semibold text-zinc-950">
            {item.stockRequiresConference
              ? "Conferência necessária"
              : formatQuantity(item.currentQuantity, item.unit)}
          </span>
        )}
      </div>
    </Link>
  );
}

function StockMetric({ label, value }: { label: string; value: string }) {
  return (
    <span className="md:text-right">
      <span className="block text-[10px] font-semibold uppercase tracking-wide text-zinc-400">
        {label}
      </span>
      <span className="mt-0.5 block text-sm font-semibold text-zinc-900">
        {value}
      </span>
    </span>
  );
}

function formatQuantity(value: number | null, unit: string) {
  if (value === null) return "Sem dados";
  const quantity = new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 2
  }).format(Number(value));
  return `${quantity} ${unit}`;
}

function formatPrice(value: number | null) {
  return value === null ? "Sem preço" : formatMoney(Number(value));
}

function formatItemValue(item: CurrentStockItem) {
  if (item.stockRequiresConference) return "Conferência necessária";
  if (item.currentQuantity === null) return "Sem dados";
  if (Number(item.currentQuantity) === 0) return formatMoney(0);
  if (item.currentValue === null) return "Sem preço";
  return formatMoney(Number(item.currentValue));
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
  }).format(value);
}

function SearchIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400"
      fill="none"
    >
      <circle cx="11" cy="11" r="6.5" stroke="currentColor" strokeWidth="1.8" />
      <path d="m16 16 4 4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

function EyeMoneyIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-4 w-4" fill="none">
      <path
        d="M2.8 12s3.2-5 9.2-5 9.2 5 9.2 5-3.2 5-9.2 5-9.2-5-9.2-5Z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="12" r="2.2" stroke="currentColor" strokeWidth="1.7" />
      <path d="M18.2 5.2v3.5M16.8 7h2.8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

function ChevronIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="h-5 w-5 shrink-0 text-zinc-400 transition group-open:rotate-180"
      fill="none"
    >
      <path
        d="m7 10 5 5 5-5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

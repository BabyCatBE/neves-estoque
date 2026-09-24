import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { listCurrentStock } from "../api/stock";
import { buildCategoryStockView } from "../lib/stockView";

const stockKey = ["stock", "current"] as const;

export function StockCurrentPage() {
  const [search, setSearch] = useState("");
  const stockQuery = useQuery({
    queryKey: stockKey,
    queryFn: listCurrentStock,
    staleTime: 15_000
  });

  const view = useMemo(
    () =>
      buildCategoryStockView(
        stockQuery.data?.categories ?? [],
        stockQuery.data?.items ?? [],
        search
      ),
    [search, stockQuery.data]
  );

  const searching = search.trim().length > 0;
  const visibleCount =
    view.groups.reduce((total, group) => total + group.items.length, 0) +
    view.pending.length;

  return (
    <AppShell title="Estoque Atual" showBack backTo="/">
      <section>
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

        <div className="mt-5 max-w-2xl">
          <label className="block" htmlFor="stock-search">
            <span className="text-sm font-medium text-zinc-800">Pesquisar</span>
            <div className="relative mt-2">
              <SearchIcon />
              <input
                id="stock-search"
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Buscar produto por nome"
                className="min-h-11 w-full rounded-xl border border-zinc-300 bg-white py-2 pl-10 pr-3 text-sm text-zinc-900 outline-none transition placeholder:text-zinc-400 focus:border-red-500 focus:ring-2 focus:ring-red-100"
              />
            </div>
          </label>
        </div>

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
          <div className="mt-6 space-y-3">
            {view.pending.length > 0 ? (
              <StockGroup
                name="Cadastro pendente"
                subtitle="Falta definir categoria"
                items={view.pending}
                forceOpen={searching}
                accentClassName="bg-amber-500"
              />
            ) : null}

            {view.groups.map((group) => (
              <StockGroup
                key={group.id}
                name={group.name}
                items={group.items}
                forceOpen={searching}
              />
            ))}

            {visibleCount === 0 ? (
              <Card className="p-5 text-sm text-zinc-600">
                {searching
                  ? "Nenhum produto encontrado para esta pesquisa."
                  : "Ainda não há produtos para exibir no Estoque Atual."}
              </Card>
            ) : null}
          </div>
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
  accentClassName = "bg-red-700"
}: {
  name: string;
  subtitle?: string;
  items: Array<{
    productId: string;
    productName: string;
    unit: string;
    currentQuantity: number | null;
  }>;
  forceOpen: boolean;
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
          <Link
            key={item.productId}
            to={`/produtos/${item.productId}`}
            className="grid min-h-14 grid-cols-[1fr_auto] items-center gap-4 border-b border-zinc-100 px-5 py-3 pl-6 transition last:border-b-0 hover:bg-red-50/60"
          >
            <span className="font-medium text-zinc-900">{item.productName}</span>
            <span className="text-right">
              <span className="font-semibold text-zinc-950">
                {formatQuantity(item.currentQuantity)}
              </span>
              {item.currentQuantity === null ? null : (
                <span className="ml-1.5 text-xs font-medium text-zinc-500">
                  {item.unit}
                </span>
              )}
            </span>
          </Link>
        ))}
      </div>
    </details>
  );
}

function formatQuantity(value: number | null) {
  if (value === null) return "Sem dados";
  return new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 3
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

import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { TextField } from "../../../shared/components/ui/TextField";
import { SearchClearButton } from "../../../shared/components/ui/SearchClearButton";
import { listEntryHistory } from "../api/entries";
import { formatMoney } from "../lib/entryValidation";

export function EntriesHistoryPage() {
  const [search, setSearch] = useState("");
  const [searchFeedback, setSearchFeedback] = useState(false);
  const searchFeedbackTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const historyQuery = useQuery({
    queryKey: ["entries", "history"],
    queryFn: listEntryHistory
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

  const filtered = useMemo(() => {
    const term = normalize(search);
    return (historyQuery.data ?? []).filter((entry) => {
      if (!term) return true;
      return [entry.supplierName, ...entry.productNames].some((value) => normalize(value).includes(term));
    });
  }, [historyQuery.data, search]);

  return (
    <AppShell title="Histórico de Entradas" showBack backTo="/entradas">
      <section>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Histórico</h2>
            <p className="mt-1 text-sm text-zinc-600">Pesquise por fornecedor ou produto. O V1 não usa filtro por período.</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link
              to="/alertas/lixeira?filter=entry"
              className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border border-red-200 bg-white px-4 py-2.5 text-sm font-semibold text-red-700 transition hover:bg-red-50"
            >
              <TrashIcon />
              Lixeira
            </Link>
            <Link
              to="/entradas/nova"
              className="inline-flex min-h-11 items-center justify-center rounded-xl bg-red-700 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-red-800"
            >
              + Nova Entrada
            </Link>
          </div>
        </div>

        <div className="mt-5 max-w-md">
          <div className="relative">
            <TextField
              id="entries-history-search"
              label="Pesquisar"
              placeholder="Fornecedor ou produto"
              value={search}
              className={search ? "pr-20" : "pr-10"}
              onChange={(event) => setSearch(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  pulseSearchFeedback();
                }
              }}
            />
            {searchFeedback ? (
              <span
                aria-label="Pesquisa atualizada"
                className={`absolute bottom-3 inline-block h-5 w-5 animate-spin rounded-full border-2 border-zinc-300 border-t-red-700 ${search ? "right-11" : "right-3"}`}
              />
            ) : null}
            {search ? (
              <SearchClearButton
                onClear={() => {
                  setSearch("");
                  document.getElementById("entries-history-search")?.focus();
                }}
              />
            ) : null}
          </div>
        </div>

        {historyQuery.isPending ? <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando histórico…</Card> : null}
        {historyQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5">
            <p className="text-sm font-medium text-red-800">Não foi possível carregar o histórico.</p>
            <Button className="mt-4" variant="secondary" onClick={() => void historyQuery.refetch()}>Tentar novamente</Button>
          </Card>
        ) : null}

        {!historyQuery.isPending && !historyQuery.isError && filtered.length === 0 ? (
          <Card className="mt-5 p-7 text-center">
            <h3 className="font-semibold">{search ? "Nenhuma Entrada encontrada" : "Nenhuma Entrada registrada"}</h3>
          </Card>
        ) : null}

        <div className="mt-5 space-y-3">
          {filtered.map((entry) => (
            <Link key={entry.id} to={`/entradas/${entry.id}`} className="block">
              <InteractiveCard className="p-5">
                <div className="grid gap-3 sm:grid-cols-[170px_1fr_auto] sm:items-center">
                  <div>
                    <p className="text-xs uppercase tracking-wide text-zinc-400">Data</p>
                    <p className="mt-1 font-semibold">{formatDate(entry.effectiveAt)}</p>
                  </div>
                  <div>
                    <p className="text-xs uppercase tracking-wide text-zinc-400">Fornecedor</p>
                    <p className="mt-1 font-semibold">{entry.supplierName}</p>
                    <p className="mt-1 truncate text-xs text-zinc-500">{entry.productNames.join(" · ")}</p>
                  </div>
                  <div className="sm:text-right">
                    <p className="text-xs uppercase tracking-wide text-zinc-400">Total conhecido</p>
                    <p className="mt-1 font-semibold">{formatMoney(entry.totalKnown)}{entry.hasMissingPrice ? " *" : ""}</p>
                    {entry.hasMissingPrice ? <p className="mt-1 text-xs text-amber-700">Há item sem preço</p> : null}
                  </div>
                </div>
              </InteractiveCard>
            </Link>
          ))}
        </div>
      </section>
    </AppShell>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(value));
}

function normalize(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR").trim();
}


function TrashIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-4 w-4" fill="none">
      <path d="M5 7h14M9 7V4h6v3M8 10v7M12 10v7M16 10v7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M7 7l1 13h8l1-13" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </svg>
  );
}

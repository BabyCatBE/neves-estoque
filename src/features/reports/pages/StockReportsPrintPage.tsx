import { useQuery } from "@tanstack/react-query";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import nevesPrintLogo from "../../../assets/neves-logo-print.webp";
import { getMonthlyStockValueHistory } from "../api/reports";
import type { MonthlyStockSeriesPoint } from "../lib/monthlyStockSeries";

const printHistoryKey = ["reports", "stock-value-history", "print"] as const;

export function StockReportsPrintPage() {
  const historyQuery = useQuery({
    queryKey: printHistoryKey,
    queryFn: () => getMonthlyStockValueHistory(),
    staleTime: 15_000
  });

  const points = historyQuery.data?.points ?? [];
  const latest = points.at(-1) ?? null;

  return (
    <AppShell title="Imprimir Relatório" showBack backTo="/estoque/relatorios">
      <style>{`
        @media print {
          @page { size: A4 portrait; margin: 10mm; }
          html, body { background: white !important; }
          header { display: none !important; }
          main { max-width: none !important; padding: 0 !important; margin: 0 !important; }
          .reports-print-controls { display: none !important; }
          .reports-print-sheet {
            width: auto !important;
            max-width: none !important;
            margin: 0 !important;
            border: 0 !important;
            border-radius: 0 !important;
            box-shadow: none !important;
          }
          .reports-print-avoid { break-inside: avoid; page-break-inside: avoid; }
          .reports-print-table thead { display: table-header-group; }
          .reports-print-table tr { break-inside: avoid; page-break-inside: avoid; }
          .reports-print-chart { break-inside: avoid; page-break-inside: avoid; }
          .reports-print-notes { break-inside: avoid; page-break-inside: avoid; }
          * { print-color-adjust: exact !important; -webkit-print-color-adjust: exact !important; }
        }
      `}</style>

      <section className="reports-print-controls">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Pré-visualização A4</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
              O relatório abaixo pode ser impresso em papel ou salvo em PDF pelo diálogo
              do navegador. Ele usa os mesmos dados do Relatório de Estoque.
            </p>
          </div>
          <Button
            disabled={historyQuery.isPending || points.length === 0}
            onClick={() => window.print()}
          >
            Imprimir / Salvar PDF
          </Button>
        </div>
      </section>

      {historyQuery.isPending ? (
        <Card className="reports-print-controls mt-5 p-5 text-sm text-zinc-600">
          Gerando pré-visualização…
        </Card>
      ) : null}

      {historyQuery.isError ? (
        <Card className="reports-print-controls mt-5 border-red-200 p-5 text-sm text-red-800">
          Não foi possível gerar o relatório para impressão.
        </Card>
      ) : null}

      {!historyQuery.isPending && !historyQuery.isError && latest ? (
        <article className="reports-print-sheet mt-6 mx-auto w-full max-w-[210mm] rounded-xl border border-zinc-200 bg-white p-[10mm] shadow-sm">
          <PrintHeader latest={latest} />
          <PrintSummary latest={latest} />

          <section className="reports-print-chart mt-7">
            <div className="border-b border-zinc-300 pb-2">
              <p className="text-xs font-bold uppercase tracking-[0.12em] text-red-700">
                Evolução mensal
              </p>
              <h2 className="mt-1 text-lg font-bold text-zinc-950">
                Valor conhecido do estoque
              </h2>
            </div>
            <div className="mt-3">
              <PrintLineChart points={points} />
            </div>
          </section>

          <section className="mt-7">
            <div className="border-b border-zinc-300 pb-2">
              <p className="text-xs font-bold uppercase tracking-[0.12em] text-red-700">
                Histórico
              </p>
              <h2 className="mt-1 text-lg font-bold text-zinc-950">
                Fechamentos mensais
              </h2>
            </div>

            <table className="reports-print-table mt-3 w-full border-collapse text-[10px]">
              <thead>
                <tr className="bg-red-700 text-white">
                  <th className="border border-red-800 px-2 py-2 text-left">Mês</th>
                  <th className="border border-red-800 px-2 py-2 text-left">Referência</th>
                  <th className="border border-red-800 px-2 py-2 text-left">Situação</th>
                  <th className="border border-red-800 px-2 py-2 text-right">Produtos</th>
                  <th className="border border-red-800 px-2 py-2 text-right">Valorados</th>
                  <th className="border border-red-800 px-2 py-2 text-right">Sem preço</th>
                  <th className="border border-red-800 px-2 py-2 text-right">Valor conhecido</th>
                </tr>
              </thead>
              <tbody>
                {[...points].reverse().map((point, index) => (
                  <tr key={point.month} className={index % 2 ? "bg-zinc-50" : "bg-white"}>
                    <td className="border border-zinc-300 px-2 py-2 font-semibold">
                      {formatMonth(point.month)}
                    </td>
                    <td className="border border-zinc-300 px-2 py-2">
                      {formatDate(point.referenceDate)}
                    </td>
                    <td className="border border-zinc-300 px-2 py-2">
                      {buildStatusLabel(point)}
                    </td>
                    <td className="border border-zinc-300 px-2 py-2 text-right">
                      {point.report.productCount}
                    </td>
                    <td className="border border-zinc-300 px-2 py-2 text-right">
                      {point.report.valuedProductCount}
                    </td>
                    <td className="border border-zinc-300 px-2 py-2 text-right">
                      {point.report.missingPriceProductCount}
                    </td>
                    <td className="border border-zinc-300 px-2 py-2 text-right font-bold">
                      {formatMoney(point.report.totalKnown)}
                      {hasIncompleteData(point) ? " *" : ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {points.some(hasIncompleteData) ? (
              <p className="mt-2 text-[10px] leading-4 text-zinc-600">
                * O valor conhecido não inclui Produtos com quantidade positiva sem preço
                histórico válido, estoque aguardando Conferência pós-mescla ou Produtos sem
                dados de estoque.
              </p>
            ) : null}
          </section>

          <section className="reports-print-notes mt-7 rounded-lg border border-zinc-400 p-3">
            <p className="text-xs font-bold uppercase tracking-wide text-zinc-600">
              Observações da análise
            </p>
            <div className="mt-4 space-y-5">
              <div className="border-b border-zinc-300" />
              <div className="border-b border-zinc-300" />
              <div className="border-b border-zinc-300" />
              <div className="border-b border-zinc-300" />
            </div>
          </section>

          <p className="mt-5 text-center text-[9px] text-zinc-400">
            Neves Estoque · Relatório gerado a partir dos registros disponíveis no sistema
          </p>
        </article>
      ) : null}

      {!historyQuery.isPending && !historyQuery.isError && !latest ? (
        <Card className="reports-print-controls mt-5 p-5 text-sm text-zinc-600">
          Ainda não existem dados suficientes para imprimir este relatório.
        </Card>
      ) : null}
    </AppShell>
  );
}

function PrintHeader({ latest }: { latest: MonthlyStockSeriesPoint }) {
  return (
    <header className="reports-print-avoid flex items-center gap-5 border-b-2 border-red-700 pb-4">
      <img
        src={nevesPrintLogo}
        alt="Panificadora Neves"
        className="h-16 w-auto max-w-40 shrink-0 object-contain"
      />
      <div className="min-w-0 flex-1">
        <p className="text-xs font-bold uppercase tracking-[0.14em] text-red-700">
          Neves Estoque
        </p>
        <h1 className="mt-1 text-2xl font-extrabold tracking-tight text-zinc-950">
          Relatório de Valor do Estoque
        </h1>
        <p className="mt-1 text-xs text-zinc-500">
          Período disponível: {formatMonth(latest.month)} como ponto mais recente
        </p>
      </div>
    </header>
  );
}

function PrintSummary({ latest }: { latest: MonthlyStockSeriesPoint }) {
  const report = latest.report;

  return (
    <section className="reports-print-avoid mt-5">
      <div className="grid grid-cols-[1.4fr_1fr] gap-3">
        <div className="rounded-lg border border-zinc-300 p-4">
          <p className="text-[10px] font-bold uppercase tracking-wide text-zinc-500">
            Posição mais recente
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className="text-sm font-bold text-zinc-950">
              {formatMonth(latest.month)}
            </span>
            <span className="rounded bg-zinc-100 px-2 py-1 text-[9px] font-semibold text-zinc-700">
              {buildStatusLabel(latest)}
            </span>
          </div>
          <p className="mt-3 text-[10px] uppercase tracking-wide text-zinc-500">
            Valor conhecido
          </p>
          <p className="mt-1 text-2xl font-extrabold text-zinc-950">
            {formatMoney(report.totalKnown)}
            {hasIncompleteData(latest) ? " *" : ""}
          </p>
          <p className="mt-2 text-[10px] leading-4 text-zinc-500">
            Referência: {formatDate(latest.referenceDate)}.
            {latest.isProvisional
              ? " Mês atual provisório e sujeito a novas Entradas, Conferências ou correções."
              : ""}
          </p>
        </div>

        <div className="rounded-lg border border-zinc-300 p-4">
          <p className="text-[10px] font-bold uppercase tracking-wide text-zinc-500">
            Cobertura do cálculo
          </p>
          <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
            <Metric label="Produtos" value={report.productCount} />
            <Metric label="Valorados" value={report.valuedProductCount} />
            <Metric label="Sem preço" value={report.missingPriceProductCount} />
            <Metric label="Estoque incerto" value={report.unknownStockProductCount} />
          </dl>
        </div>
      </div>

      {hasIncompleteData(latest) ? (
        <div className="mt-3 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-[10px] leading-4 text-amber-950">
          <strong>Atenção:</strong> {buildIncompleteMessage(latest)}
        </div>
      ) : null}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt className="text-[9px] uppercase tracking-wide text-zinc-500">{label}</dt>
      <dd className="mt-0.5 font-bold text-zinc-950">{value}</dd>
    </div>
  );
}

function PrintLineChart({ points }: { points: MonthlyStockSeriesPoint[] }) {
  const width = 720;
  const height = 240;
  const padding = { top: 22, right: 20, bottom: 40, left: 70 };
  const values = points.map((point) => point.report.totalKnown);
  const max = Math.max(...values, 0);
  const min = Math.min(...values, 0);
  const range = Math.max(max - min, Math.max(max * 0.08, 1));
  const yMin = Math.max(0, min - range * 0.12);
  const yMax = max + range * 0.12;
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const chartPoints = points.map((point, index) => {
    const x =
      points.length === 1
        ? padding.left + plotWidth / 2
        : padding.left + (index / (points.length - 1)) * plotWidth;
    const normalized = (point.report.totalKnown - yMin) / Math.max(yMax - yMin, 1);
    const y = padding.top + plotHeight - normalized * plotHeight;
    return { x, y, point };
  });

  const polyline = chartPoints.map(({ x, y }) => `${x},${y}`).join(" ");
  const gridValues = [0, 0.5, 1].map((ratio) => yMin + (yMax - yMin) * ratio);

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label="Gráfico impresso do valor conhecido do estoque por mês"
      className="w-full"
    >
      {gridValues.map((value) => {
        const normalized = (value - yMin) / Math.max(yMax - yMin, 1);
        const y = padding.top + plotHeight - normalized * plotHeight;
        return (
          <g key={value}>
            <line
              x1={padding.left}
              x2={width - padding.right}
              y1={y}
              y2={y}
              stroke="#d4d4d8"
              strokeDasharray="4 5"
            />
            <text
              x={padding.left - 10}
              y={y + 4}
              textAnchor="end"
              fill="#71717a"
              fontSize="11"
            >
              {formatCompactMoney(value)}
            </text>
          </g>
        );
      })}

      {chartPoints.length > 1 ? (
        <polyline
          points={polyline}
          fill="none"
          stroke="#b91c1c"
          strokeWidth="3"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      ) : null}

      {chartPoints.map(({ x, y, point }) => (
        <g key={point.month}>
          <circle cx={x} cy={y} r="5" fill="#b91c1c" />
          <circle cx={x} cy={y} r="9" fill="none" stroke="#fee2e2" strokeWidth="5" />
          <text
            x={x}
            y={height - 14}
            textAnchor="middle"
            fill="#52525b"
            fontSize="11"
          >
            {formatShortMonth(point.month)}
          </text>
        </g>
      ))}
    </svg>
  );
}

function hasIncompleteData(point: MonthlyStockSeriesPoint) {
  return (
    point.report.hasMissingPrice ||
    point.report.hasUnknownStock ||
    point.report.hasMissingStockData
  );
}

function buildStatusLabel(point: MonthlyStockSeriesPoint) {
  const parts: string[] = [];
  if (point.isProvisional) parts.push("Provisório");
  parts.push(
    point.report.hasExactPhysicalClose
      ? "Fechamento físico exato"
      : "Estimado pelos registros disponíveis"
  );
  return parts.join(" · ");
}

function buildIncompleteMessage(point: MonthlyStockSeriesPoint) {
  const report = point.report;
  const messages: string[] = [];

  if (report.missingPriceProductCount > 0) {
    messages.push(
      `${report.missingPriceProductCount} ${report.missingPriceProductCount === 1 ? "Produto tem" : "Produtos têm"} quantidade positiva sem preço histórico válido`
    );
  }

  if (report.unknownStockProductCount > 0) {
    messages.push(
      `${report.unknownStockProductCount} ${report.unknownStockProductCount === 1 ? "Produto aguarda" : "Produtos aguardam"} Conferência física após mescla`
    );
  }

  if (report.noStockDataProductCount > 0) {
    messages.push(
      `${report.noStockDataProductCount} ${report.noStockDataProductCount === 1 ? "Produto está" : "Produtos estão"} sem dados de estoque`
    );
  }

  return messages.join(". ") + (messages.length ? "." : "");
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
  }).format(value);
}

function formatCompactMoney(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    notation: "compact",
    maximumFractionDigits: 1,
    style: "currency",
    currency: "BRL"
  }).format(value);
}

function parseDateParts(value: string, expectedParts: number) {
  const parts = value.split("-").map(Number);
  if (
    parts.length !== expectedParts ||
    parts.some((part) => !Number.isFinite(part))
  ) {
    throw new Error("Data de relatório inválida.");
  }
  return parts;
}

function formatMonth(month: string) {
  const parts = parseDateParts(month, 2);
  const year = parts[0];
  const monthNumber = parts[1];
  if (year === undefined || monthNumber === undefined) {
    throw new Error("Mês de relatório inválido.");
  }

  return new Intl.DateTimeFormat("pt-BR", {
    month: "long",
    year: "numeric",
    timeZone: "UTC"
  }).format(new Date(Date.UTC(year, monthNumber - 1, 1)));
}

function formatShortMonth(month: string) {
  const parts = parseDateParts(month, 2);
  const year = parts[0];
  const monthNumber = parts[1];
  if (year === undefined || monthNumber === undefined) {
    throw new Error("Mês de relatório inválido.");
  }

  return new Intl.DateTimeFormat("pt-BR", {
    month: "short",
    year: "2-digit",
    timeZone: "UTC"
  })
    .format(new Date(Date.UTC(year, monthNumber - 1, 1)))
    .replace(".", "");
}

function formatDate(date: string) {
  const parts = parseDateParts(date, 3);
  const year = parts[0];
  const month = parts[1];
  const day = parts[2];
  if (year === undefined || month === undefined || day === undefined) {
    throw new Error("Data de relatório inválida.");
  }

  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "UTC"
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

import { useQuery } from "@tanstack/react-query";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { getMonthlyStockValueHistory } from "../api/reports";
import type { MonthlyStockSeriesPoint } from "../lib/monthlyStockSeries";

const historyKey = ["reports", "stock-value-history"] as const;

export function StockReportsPage() {
  const historyQuery = useQuery({
    queryKey: historyKey,
    queryFn: () => getMonthlyStockValueHistory(),
    staleTime: 15_000
  });

  const series = historyQuery.data;
  const latest = series?.points.at(-1) ?? null;

  return (
    <AppShell title="Relatórios" showBack backTo="/estoque">
      <section>
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
            Estoque
          </p>
          <h2 className="mt-1 text-2xl font-semibold tracking-tight">
            Valor do estoque ao longo do tempo
          </h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-zinc-600">
            Histórico mensal calculado pelas Conferências físicas e Entradas registradas.
            Quando não existe Conferência exatamente no fechamento, o sistema usa os
            registros disponíveis sem inventar consumo ou saída.
          </p>
        </div>

        {historyQuery.isPending ? (
          <Card className="mt-6 p-5 text-sm text-zinc-600">
            Carregando Relatórios…
          </Card>
        ) : null}

        {historyQuery.isError ? (
          <Card className="mt-6 border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar os Relatórios.
          </Card>
        ) : null}

        {!historyQuery.isPending && !historyQuery.isError && series ? (
          <>
            {series.points.length === 0 ? (
              <Card className="mt-6 p-5 text-sm text-zinc-600">
                Ainda não existem registros suficientes para montar o histórico mensal.
              </Card>
            ) : (
              <>
                {latest ? <CurrentSummary point={latest} /> : null}

                <Card className="mt-6 overflow-hidden">
                  <div className="border-b border-zinc-100 px-5 py-4">
                    <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">
                      Evolução mensal
                    </p>
                    <h3 className="mt-1 font-semibold text-zinc-950">
                      Valor conhecido do estoque
                    </h3>
                    <p className="mt-1 text-xs leading-5 text-zinc-500">
                      O gráfico soma somente valores calculáveis. Produtos sem preço ou com
                      estoque ainda não confiável ficam sinalizados na lista abaixo.
                    </p>
                  </div>

                  <div className="p-4 sm:p-5">
                    <StockValueLineChart points={series.points} />
                  </div>
                </Card>

                <div className="mt-7">
                  <div className="flex flex-wrap items-end justify-between gap-2">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">
                        Fechamentos
                      </p>
                      <h3 className="mt-1 text-lg font-semibold text-zinc-950">
                        Valores mensais
                      </h3>
                    </div>
                    <p className="text-xs text-zinc-500">
                      {series.points.length}{" "}
                      {series.points.length === 1 ? "mês disponível" : "meses disponíveis"}
                    </p>
                  </div>

                  <div className="mt-3 space-y-3">
                    {[...series.points].reverse().map((point) => (
                      <MonthlyValueCard key={point.month} point={point} />
                    ))}
                  </div>
                </div>
              </>
            )}
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function CurrentSummary({ point }: { point: MonthlyStockSeriesPoint }) {
  const report = point.report;

  return (
    <Card className="mt-6 overflow-hidden">
      <div className="grid gap-0 md:grid-cols-[minmax(0,1.35fr)_minmax(260px,0.65fr)]">
        <div className="p-5 sm:p-6">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-red-50 px-2.5 py-1 text-xs font-semibold text-red-700">
              {formatMonth(point.month)}
            </span>
            {point.isProvisional ? (
              <StatusBadge tone="amber">Provisório</StatusBadge>
            ) : null}
            {report.isEstimatedFromAvailableRecords ? (
              <StatusBadge tone="zinc">Estimado pelos registros disponíveis</StatusBadge>
            ) : (
              <StatusBadge tone="green">Fechamento físico exato</StatusBadge>
            )}
          </div>

          <p className="mt-5 text-xs font-semibold uppercase tracking-wide text-zinc-400">
            Valor conhecido
          </p>
          <p className="mt-1 text-3xl font-semibold tracking-tight text-zinc-950">
            {formatMoney(report.totalKnown)}
            {report.hasMissingPrice || report.hasUnknownStock ? " *" : ""}
          </p>
          <p className="mt-2 max-w-2xl text-xs leading-5 text-zinc-500">
            Referência em {formatDate(point.referenceDate)}.
            {point.isProvisional
              ? " Este mês ainda pode mudar com novas Entradas, Conferências ou correções."
              : ""}
          </p>
        </div>

        <div className="border-t border-zinc-100 bg-zinc-50/70 p-5 md:border-l md:border-t-0">
          <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">
            Cobertura do cálculo
          </p>
          <div className="mt-3 grid grid-cols-2 gap-3">
            <SummaryMetric label="Produtos" value={report.productCount} />
            <SummaryMetric label="Valorados" value={report.valuedProductCount} />
            <SummaryMetric label="Sem preço" value={report.missingPriceProductCount} />
            <SummaryMetric label="Estoque incerto" value={report.unknownStockProductCount} />
          </div>
        </div>
      </div>

      {report.hasMissingPrice || report.hasUnknownStock || report.hasMissingStockData ? (
        <div className="border-t border-amber-100 bg-amber-50 px-5 py-4 text-xs leading-5 text-amber-900 sm:px-6">
          <strong>* Atenção:</strong>{" "}
          {buildIncompleteMessage(report)}
        </div>
      ) : null}
    </Card>
  );
}

function MonthlyValueCard({ point }: { point: MonthlyStockSeriesPoint }) {
  const report = point.report;
  const incomplete =
    report.hasMissingPrice || report.hasUnknownStock || report.hasMissingStockData;

  return (
    <Card className="overflow-hidden">
      <div className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h4 className="font-semibold text-zinc-950">{formatMonth(point.month)}</h4>
            {point.isProvisional ? (
              <StatusBadge tone="amber">Provisório</StatusBadge>
            ) : null}
            {report.hasExactPhysicalClose ? (
              <StatusBadge tone="green">Fechamento físico exato</StatusBadge>
            ) : (
              <StatusBadge tone="zinc">Estimado</StatusBadge>
            )}
          </div>

          <p className="mt-1 text-xs text-zinc-500">
            Referência: {formatDate(point.referenceDate)} · {report.productCount}{" "}
            {report.productCount === 1 ? "Produto" : "Produtos"}
          </p>

          {incomplete ? (
            <p className="mt-2 text-xs leading-5 text-amber-700">
              {buildIncompleteMessage(report)}
            </p>
          ) : null}
        </div>

        <div className="sm:text-right">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-zinc-400">
            Valor conhecido
          </p>
          <p className="mt-0.5 text-xl font-semibold text-zinc-950">
            {formatMoney(report.totalKnown)}
            {incomplete ? " *" : ""}
          </p>
        </div>
      </div>
    </Card>
  );
}

function StockValueLineChart({ points }: { points: MonthlyStockSeriesPoint[] }) {
  const width = 720;
  const height = 280;
  const padding = { top: 24, right: 24, bottom: 44, left: 72 };
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

  const gridValues = [0, 0.5, 1].map((ratio) => yMin + (yMax - yMin) * ratio);
  const polyline = chartPoints.map(({ x, y }) => `${x},${y}`).join(" ");

  return (
    <div>
      <div className="overflow-x-auto">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          role="img"
          aria-label="Gráfico de linha do valor conhecido do estoque por mês"
          className="min-w-[620px] w-full"
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
                  stroke="currentColor"
                  className="text-zinc-200"
                  strokeDasharray="4 5"
                />
                <text
                  x={padding.left - 10}
                  y={y + 4}
                  textAnchor="end"
                  className="fill-zinc-400 text-[11px]"
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
              stroke="currentColor"
              className="text-red-700"
              strokeWidth="3"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ) : null}

          {chartPoints.map(({ x, y, point }) => (
            <g key={point.month}>
              <circle
                cx={x}
                cy={y}
                r="5"
                fill="currentColor"
                className="text-red-700"
              />
              <circle
                cx={x}
                cy={y}
                r="9"
                fill="none"
                stroke="currentColor"
                className="text-red-100"
                strokeWidth="5"
              />
              <text
                x={x}
                y={height - 16}
                textAnchor="middle"
                className="fill-zinc-500 text-[11px]"
              >
                {formatShortMonth(point.month)}
              </text>
            </g>
          ))}
        </svg>
      </div>

      {points.length === 1 ? (
        <p className="mt-2 text-center text-xs text-zinc-500">
          O histórico começa em {formatMonth(points[0].month)}. O gráfico ganhará linha
          quando houver mais de um mês disponível.
        </p>
      ) : null}
    </div>
  );
}

function SummaryMetric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-zinc-200 bg-white px-3 py-3">
      <p className="text-[10px] font-semibold uppercase tracking-wide text-zinc-400">
        {label}
      </p>
      <p className="mt-1 text-lg font-semibold text-zinc-950">{value}</p>
    </div>
  );
}

function StatusBadge({
  children,
  tone
}: {
  children: string;
  tone: "amber" | "green" | "zinc";
}) {
  const classes =
    tone === "amber"
      ? "bg-amber-100 text-amber-800"
      : tone === "green"
        ? "bg-emerald-100 text-emerald-800"
        : "bg-zinc-100 text-zinc-700";

  return (
    <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${classes}`}>
      {children}
    </span>
  );
}

function buildIncompleteMessage(
  report: MonthlyStockSeriesPoint["report"]
) {
  const messages: string[] = [];

  if (report.missingPriceProductCount > 0) {
    messages.push(
      `${report.missingPriceProductCount} ${report.missingPriceProductCount === 1 ? "Produto tem" : "Produtos têm"} quantidade positiva sem preço histórico válido e não ${report.missingPriceProductCount === 1 ? "entra" : "entram"} no valor conhecido`
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

function formatMonth(month: string) {
  const [year, monthNumber] = month.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", {
    month: "long",
    year: "numeric",
    timeZone: "UTC"
  }).format(new Date(Date.UTC(year, monthNumber - 1, 1)));
}

function formatShortMonth(month: string) {
  const [year, monthNumber] = month.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", {
    month: "short",
    year: "2-digit",
    timeZone: "UTC"
  })
    .format(new Date(Date.UTC(year, monthNumber - 1, 1)))
    .replace(".", "");
}

function formatDate(date: string) {
  const [year, month, day] = date.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "UTC"
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

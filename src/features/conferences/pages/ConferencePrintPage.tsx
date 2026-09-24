import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { CategoryIllustrationVisual } from "../../categories/components/CategoryIllustrationVisual";
import { PrintNevesLogo } from "../components/PrintNevesLogo";
import { listConferencePrintData, type ConferencePrintCategory } from "../api/conferences";

const ROWS_PER_PAGE = 18;

type PrintPage = {
  category: ConferencePrintCategory;
  products: ConferencePrintCategory["products"];
  categoryPage: number;
  categoryPages: number;
};

export function ConferencePrintPage() {
  const printQuery = useQuery({
    queryKey: ["conferences", "print-data"],
    queryFn: listConferencePrintData
  });

  const pages = useMemo<PrintPage[]>(() => {
    const result: PrintPage[] = [];
    for (const category of printQuery.data?.categories ?? []) {
      if (!category.products.length) continue;
      const categoryPages = Math.ceil(category.products.length / ROWS_PER_PAGE);
      for (let index = 0; index < categoryPages; index += 1) {
        result.push({
          category,
          products: category.products.slice(index * ROWS_PER_PAGE, (index + 1) * ROWS_PER_PAGE),
          categoryPage: index + 1,
          categoryPages
        });
      }
    }
    return result;
  }, [printQuery.data?.categories]);

  const splitCategories = (printQuery.data?.categories ?? []).filter(
    (category) => category.products.length > ROWS_PER_PAGE
  );

  return (
    <AppShell title="Papéis de Conferência" showBack backTo="/conferencias">
      <style>{`
        @media print {
          @page { size: A4 portrait; margin: 0; }
          body { background: white !important; }
          header { display: none !important; }
          main { max-width: none !important; padding: 0 !important; margin: 0 !important; }
          .conference-print-controls { display: none !important; }
          .conference-print-stack { display: block !important; }
          .conference-print-page {
            width: 210mm !important;
            height: 297mm !important;
            min-height: 297mm !important;
            margin: 0 !important;
            border: 0 !important;
            border-radius: 0 !important;
            box-shadow: none !important;
            page-break-after: always;
            break-after: page;
          }
          .conference-print-page:last-child { page-break-after: auto; break-after: auto; }
        }
      `}</style>

      <section className="conference-print-controls">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Pré-visualização A4</h2>
            <p className="mt-1 text-sm text-zinc-600">
              As folhas são geradas diretamente do cadastro atual. O diálogo do navegador escolhe impressora, páginas, cópias ou PDF.
            </p>
          </div>
          <Button disabled={printQuery.isPending || pages.length === 0} onClick={() => window.print()}>
            Imprimir
          </Button>
        </div>

        {(printQuery.data?.pendingProductCount ?? 0) > 0 ? (
          <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            {printQuery.data?.pendingProductCount} produto(s) pendente(s) sem Categoria não aparecerão nos papéis.
          </div>
        ) : null}

        {splitCategories.length > 0 ? (
          <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            Algumas categorias são grandes e foram divididas em mais de uma página:{" "}
            {splitCategories.map((category) => category.name).join(", ")}.
          </div>
        ) : null}
      </section>

      {printQuery.isPending ? (
        <Card className="conference-print-controls mt-5 p-5 text-sm text-zinc-600">
          Gerando pré-visualização…
        </Card>
      ) : null}

      <div className="conference-print-stack mt-6 flex flex-col items-center gap-6">
        {pages.map((page, pageIndex) => (
          <article
            key={`${page.category.id}-${page.categoryPage}`}
            className="conference-print-page relative flex w-full max-w-[210mm] flex-col overflow-hidden rounded-xl border border-zinc-200 bg-white p-[10mm] shadow-sm"
            style={{ minHeight: "297mm" }}
          >
            <div className="flex items-center gap-4 border-b-2 border-red-700 pb-4">
              <CategoryIllustrationVisual
                source={page.category.illustration_source}
                illustrationKey={page.category.illustration_key}
                illustrationUrl={page.category.illustrationUrl}
                positionX={page.category.illustration_position_x}
                positionY={page.category.illustration_position_y}
                monochrome
                className="h-16 w-16"
              />
              <div className="min-w-0 flex-1">
                <h2 className="text-2xl font-extrabold tracking-tight text-zinc-950">
                  {page.category.name}
                </h2>
                <p className="mt-1 text-sm font-semibold uppercase tracking-[0.12em] text-red-700">
                  Conferência de Estoque
                  {page.categoryPages > 1 ? ` · parte ${page.categoryPage}` : ""}
                </p>
              </div>
              <PrintNevesLogo className="h-14 w-28 shrink-0" />
            </div>

            <div className="mt-4 grid grid-cols-[150px_1fr] gap-3 text-sm">
              <div className="rounded-lg border border-zinc-400 px-3 py-3">
                <span className="font-semibold">Data:</span>
              </div>
              <div className="rounded-lg border border-zinc-400 px-3 py-3">
                <span className="font-semibold">Responsável:</span>
              </div>
            </div>

            <table className="mt-4 w-full table-fixed border-collapse text-sm">
              <thead>
                <tr className="bg-red-700 text-white">
                  <th className="w-9 border border-red-800 px-2 py-2">✓</th>
                  <th className="border border-red-800 px-3 py-2 text-left">Produto</th>
                  <th className="w-16 border border-red-800 px-2 py-2">Und.</th>
                  <th className="w-28 border border-red-800 px-2 py-2">Quantidade</th>
                </tr>
              </thead>
              <tbody>
                {page.products.map((product, index) => (
                  <tr key={product.id} className={index % 2 ? "bg-zinc-50" : "bg-white"}>
                    <td className="h-10 border border-zinc-300" />
                    <td className="border border-zinc-300 px-3 py-2 font-medium leading-tight">
                      {product.name}
                    </td>
                    <td className="border border-zinc-300 px-2 py-2 text-center">{product.unit}</td>
                    <td className="border border-zinc-400" />
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="mt-4 rounded-lg border border-zinc-400 p-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-zinc-600">Observações</p>
              <div className="mt-2 space-y-3">
                <div className="border-b border-zinc-300" />
                <div className="border-b border-zinc-300" />
                <div className="border-b border-zinc-300" />
              </div>
            </div>

            <p className="mt-auto pt-3 text-center text-xs text-zinc-500">{pageIndex + 1}</p>
          </article>
        ))}
      </div>

      {!printQuery.isPending && pages.length === 0 ? (
        <Card className="conference-print-controls mt-5 p-5 text-sm text-zinc-600">
          Não existem categorias com produtos ativos para imprimir.
        </Card>
      ) : null}
    </AppShell>
  );
}

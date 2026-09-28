package com.babycatbe.nevesestoque.feature.reports

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.babycatbe.nevesestoque.feature.conferences.PRINT_LOGO_DATA_URI
import java.util.Locale

/**
 * Impressão / PDF do Relatório de Valor do Estoque pelo PrintManager do Android.
 * O WebView é usado somente como motor interno de renderização do documento A4,
 * no mesmo padrão já aprovado para os Papéis de Conferência.
 */
fun printStockReport(context: Context, points: List<MonthlyStockSeriesPoint>) {
    if (points.isEmpty()) return
    val html = buildStockReportPrintHtml(points)
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = view.createPrintDocumentAdapter("Relatório de Valor do Estoque")
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
            printManager.print("Relatório de Estoque - Panificadora Neves", adapter, attributes)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

internal fun buildStockReportPrintHtml(points: List<MonthlyStockSeriesPoint>): String {
    require(points.isNotEmpty()) { "Nenhum mês selecionado para impressão." }
    val latest = points.last()
    val report = latest.report
    val period = reportPeriodLabel(points)

    val rows = points.reversed().mapIndexed { index, point ->
        val rowClass = if (index % 2 == 1) " class=\"alternate\"" else ""
        "<tr$rowClass>" +
            "<td class=\"strong\">${esc(capitalize(formatReportMonth(point.month)))}</td>" +
            "<td>${esc(formatReportDate(point.referenceDate))}</td>" +
            "<td>${esc(reportStatusLabel(point))}</td>" +
            "<td class=\"num\">${point.report.productCount}</td>" +
            "<td class=\"num\">${point.report.valuedProductCount}</td>" +
            "<td class=\"num\">${point.report.missingPriceProductCount}</td>" +
            "<td class=\"num strong\">${esc(reportValueLabel(point.report))}</td>" +
            "</tr>"
    }.joinToString("")

    val incompleteNote = if (points.any { it.report.isIncomplete }) {
        "<p class=\"footnote\">* O valor conhecido não inclui Produtos com quantidade positiva sem preço " +
            "histórico válido, estoque aguardando Conferência pós-mescla ou Produtos sem dados de estoque.</p>"
    } else {
        ""
    }

    val attention = if (report.isIncomplete) {
        "<div class=\"attention\"><strong>Atenção:</strong> ${esc(reportIncompleteMessage(report))}</div>"
    } else {
        ""
    }

    val provisional = if (latest.isProvisional) {
        " Mês atual provisório e sujeito a novas Entradas, Conferências ou correções."
    } else {
        ""
    }

    return """
        <!doctype html>
        <html lang="pt-BR">
        <head>
        <meta charset="utf-8" />
        <style>
          @page { size: A4 portrait; margin: 10mm; }
          * { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
          body { margin: 0; font-family: sans-serif; color: #18181b; }
          header { display: flex; align-items: center; gap: 5mm; border-bottom: .6mm solid #b91c1c; padding-bottom: 4mm; }
          header img { height: 16mm; width: auto; max-width: 40mm; object-fit: contain; }
          .eyebrow { margin: 0; color: #b91c1c; font-size: 8pt; font-weight: bold; letter-spacing: 1.2pt; text-transform: uppercase; }
          h1 { margin: 1mm 0 0; font-size: 18pt; }
          .period { margin: 1mm 0 0; color: #71717a; font-size: 9pt; }
          .summary { display: grid; grid-template-columns: 1.4fr 1fr; gap: 3mm; margin-top: 5mm; }
          .box { border: .3mm solid #d4d4d8; border-radius: 2mm; padding: 4mm; }
          .label { margin: 0; color: #71717a; font-size: 7pt; font-weight: bold; letter-spacing: .6pt; text-transform: uppercase; }
          .month { margin: 2mm 0 0; font-size: 11pt; font-weight: bold; }
          .badge { display: inline-block; margin-top: 1.5mm; background: #f4f4f5; border-radius: 1mm; padding: 1mm 2mm; font-size: 7pt; color: #3f3f46; }
          .value { margin: 1mm 0 0; font-size: 20pt; font-weight: 800; }
          .small { margin: 2mm 0 0; color: #71717a; font-size: 7.5pt; line-height: 1.35; }
          .metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 2mm 4mm; margin-top: 2mm; font-size: 9pt; }
          .metrics span { display: block; color: #71717a; font-size: 6.5pt; text-transform: uppercase; }
          .metrics b { font-size: 11pt; }
          .attention { margin-top: 3mm; border: .3mm solid #fcd34d; background: #fffbeb; border-radius: 2mm; padding: 2mm 3mm; font-size: 7.5pt; color: #451a03; }
          section { margin-top: 6mm; break-inside: avoid; }
          .section-title { border-bottom: .3mm solid #d4d4d8; padding-bottom: 2mm; }
          h2 { margin: 1mm 0 0; font-size: 12pt; }
          svg { width: 100%; height: auto; margin-top: 2mm; }
          table { width: 100%; border-collapse: collapse; margin-top: 3mm; font-size: 7.5pt; }
          thead { display: table-header-group; }
          tr { break-inside: avoid; }
          th { background: #b91c1c; color: white; border: .3mm solid #991b1b; padding: 1.8mm; text-align: left; }
          td { border: .3mm solid #d4d4d8; padding: 1.8mm; }
          tr.alternate td { background: #fafafa; }
          .num { text-align: right; }
          .strong { font-weight: bold; }
          .footnote { margin: 2mm 0 0; color: #52525b; font-size: 7pt; line-height: 1.35; }
          .notes { border: .3mm solid #a1a1aa; border-radius: 2mm; padding: 3mm; }
          .notes .line { border-bottom: .3mm solid #d4d4d8; height: 7mm; }
          footer { margin-top: 5mm; text-align: center; color: #a1a1aa; font-size: 6.5pt; }
        </style>
        </head>
        <body>
          <header>
            <img src="$PRINT_LOGO_DATA_URI" alt="Panificadora Neves" />
            <div>
              <p class="eyebrow">Neves Estoque</p>
              <h1>Relatório de Valor do Estoque</h1>
              <p class="period">Período do relatório: ${esc(period)}</p>
            </div>
          </header>

          <div class="summary">
            <div class="box">
              <p class="label">Posição mais recente</p>
              <p class="month">${esc(capitalize(formatReportMonth(latest.month)))}</p>
              <span class="badge">${esc(reportStatusLabel(latest))}</span>
              <p class="label" style="margin-top:3mm">Valor conhecido</p>
              <p class="value">${esc(reportValueLabel(report))}</p>
              <p class="small">Referência: ${esc(formatReportDate(latest.referenceDate))}.$provisional</p>
            </div>
            <div class="box">
              <p class="label">Cobertura do cálculo</p>
              <div class="metrics">
                <div><span>Produtos</span><b>${report.productCount}</b></div>
                <div><span>Valorados</span><b>${report.valuedProductCount}</b></div>
                <div><span>Sem preço</span><b>${report.missingPriceProductCount}</b></div>
                <div><span>Estoque incerto</span><b>${report.unknownStockProductCount}</b></div>
              </div>
            </div>
          </div>
          $attention

          <section>
            <div class="section-title">
              <p class="eyebrow">Evolução mensal</p>
              <h2>Valor conhecido do estoque</h2>
            </div>
            ${buildReportChartSvg(points)}
          </section>

          <section>
            <div class="section-title">
              <p class="eyebrow">Histórico</p>
              <h2>Fechamentos mensais</h2>
            </div>
            <table>
              <thead><tr>
                <th>Mês</th><th>Referência</th><th>Situação</th>
                <th class="num">Produtos</th><th class="num">Valorados</th>
                <th class="num">Sem preço</th><th class="num">Valor conhecido</th>
              </tr></thead>
              <tbody>$rows</tbody>
            </table>
            $incompleteNote
          </section>

          <section class="notes">
            <p class="label">Observações da análise</p>
            <div class="line"></div><div class="line"></div><div class="line"></div><div class="line"></div>
          </section>

          <footer>Neves Estoque · Relatório gerado a partir dos registros disponíveis no sistema</footer>
        </body>
        </html>
    """.trimIndent()
}

/** Gráfico de linha em SVG com a mesma geometria da Web (720 × 240). */
internal fun buildReportChartSvg(points: List<MonthlyStockSeriesPoint>): String {
    val width = 720.0
    val height = 240.0
    val left = 70.0
    val right = 20.0
    val top = 22.0
    val bottom = 40.0
    val plotWidth = width - left - right
    val plotHeight = height - top - bottom
    val scale = reportChartScale(points.map { it.report.totalKnown })

    fun y(value: Double) = top + plotHeight - scale.ratio(value) * plotHeight
    fun x(index: Int) = if (points.size == 1) left + plotWidth / 2 else left + index.toDouble() / (points.size - 1) * plotWidth

    val grid = scale.gridValues.joinToString("") { value ->
        val gy = fmt(y(value))
        "<line x1=\"${fmt(left)}\" x2=\"${fmt(width - right)}\" y1=\"$gy\" y2=\"$gy\" stroke=\"#d4d4d8\" stroke-dasharray=\"4 5\"/>" +
            "<text x=\"${fmt(left - 10)}\" y=\"${fmt(y(value) + 4)}\" text-anchor=\"end\" fill=\"#71717a\" font-size=\"11\">" +
            esc(formatReportCompactMoney(value)) + "</text>"
    }

    val line = if (points.size > 1) {
        val polyline = points.indices.joinToString(" ") { "${fmt(x(it))},${fmt(y(points[it].report.totalKnown))}" }
        "<polyline points=\"$polyline\" fill=\"none\" stroke=\"#b91c1c\" stroke-width=\"3\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>"
    } else {
        ""
    }

    val dots = points.indices.joinToString("") { index ->
        val cx = fmt(x(index))
        val cy = fmt(y(points[index].report.totalKnown))
        "<circle cx=\"$cx\" cy=\"$cy\" r=\"9\" fill=\"none\" stroke=\"#fee2e2\" stroke-width=\"5\"/>" +
            "<circle cx=\"$cx\" cy=\"$cy\" r=\"5\" fill=\"#b91c1c\"/>" +
            "<text x=\"$cx\" y=\"${fmt(height - 14)}\" text-anchor=\"middle\" fill=\"#52525b\" font-size=\"11\">" +
            esc(formatReportShortMonth(points[index].month)) + "</text>"
    }

    return "<svg viewBox=\"0 0 ${fmt(width)} ${fmt(height)}\" xmlns=\"http://www.w3.org/2000/svg\">$grid$line$dots</svg>"
}

private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun capitalize(value: String): String = value.replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) }

private fun esc(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

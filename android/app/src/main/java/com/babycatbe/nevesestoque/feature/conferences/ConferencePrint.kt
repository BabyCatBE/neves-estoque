package com.babycatbe.nevesestoque.feature.conferences

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.Base64

private const val ROWS_PER_PRINT_PAGE = 18
internal const val PRINT_LOGO_DATA_URI = "data:image/webp;base64,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"

fun printConferenceSheets(context: Context, data: ConferencePrintData) {
    val html = buildConferencePrintHtml(data)
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = view.createPrintDocumentAdapter("Papéis de Conferência")
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
            printManager.print("Conferência - Panificadora Neves", adapter, attributes)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

internal fun buildConferencePrintHtml(data: ConferencePrintData): String {
    data class PrintPage(
        val category: ConferencePrintCategory,
        val products: List<ConferenceProduct>,
        val part: Int,
        val parts: Int,
    )

    val pages = buildList {
        data.categories.forEach { category ->
            if (category.products.isEmpty()) return@forEach
            val chunks = category.products.chunked(ROWS_PER_PRINT_PAGE)
            chunks.forEachIndexed { index, products ->
                add(PrintPage(category, products, index + 1, chunks.size))
            }
        }
    }

    val body = pages.mapIndexed { pageIndex, page ->
        val rows = page.products.mapIndexed { index, product ->
            val rowClass = if (index % 2 == 1) "alternate" else ""
            "<tr class=\"" + rowClass + "\"><td class=\"check\"></td><td class=\"product\">" +
                htmlEscape(product.name) + "</td><td class=\"unit\">" +
                htmlEscape(product.unit) + "</td><td class=\"qty\"></td></tr>"
        }.joinToString("")

        val partLabel = if (page.parts > 1) " · parte " + page.part else ""
        val illustration = illustrationHtml(page.category)

        """
        <section class="page">
          <header>
            $illustration
            <div class="heading">
              <h1>${htmlEscape(page.category.name)}</h1>
              <p>CONFERÊNCIA DE ESTOQUE$partLabel</p>
            </div>
            <img class="logo" src="$PRINT_LOGO_DATA_URI" />
          </header>
          <div class="meta">
            <div><strong>Data:</strong></div>
            <div><strong>Responsável:</strong></div>
          </div>
          <table>
            <thead><tr><th class="check">✓</th><th>Produto</th><th class="unit">Und.</th><th class="qty">Quantidade</th></tr></thead>
            <tbody>$rows</tbody>
          </table>
          <div class="notes"><strong>OBSERVAÇÕES</strong><div></div><div></div><div></div></div>
          <footer>${pageIndex + 1}</footer>
        </section>
        """.trimIndent()
    }.joinToString("")

    return """
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width,initial-scale=1">
      <style>
        @page { size: A4 portrait; margin: 0; }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; background: white; font-family: Arial, sans-serif; color: #18181b; }
        .page { width: 210mm; height: 297mm; padding: 10mm; page-break-after: always; display: flex; flex-direction: column; overflow: hidden; }
        .page:last-child { page-break-after: auto; }
        header { display: flex; align-items: center; gap: 4mm; border-bottom: 1mm solid #b91c1c; padding-bottom: 4mm; }
        .glyph { width: 16mm; height: 16mm; display: flex; align-items: center; justify-content: center; font-size: 11mm; filter: grayscale(1); }
        .category-art { width: 16mm; height: 16mm; object-fit: cover; filter: grayscale(1); }
        .heading { flex: 1; min-width: 0; }
        h1 { margin: 0; font-size: 20pt; line-height: 1.05; }
        .heading p { margin: 2mm 0 0; color: #b91c1c; font-size: 10pt; font-weight: bold; letter-spacing: .8pt; }
        .logo { height: 16mm; max-width: 38mm; object-fit: contain; }
        .meta { display: grid; grid-template-columns: 40mm 1fr; gap: 3mm; margin-top: 4mm; font-size: 10pt; }
        .meta > div { border: .35mm solid #71717a; border-radius: 2mm; padding: 3mm; height: 12mm; }
        table { width: 100%; border-collapse: collapse; margin-top: 4mm; table-layout: fixed; font-size: 9.5pt; }
        th { background: #b91c1c; color: white; border: .35mm solid #991b1b; padding: 2.2mm; text-align: left; }
        td { border: .3mm solid #d4d4d8; height: 10mm; padding: 2mm 3mm; }
        tr.alternate td { background: #fafafa; }
        .check { width: 10mm; text-align: center; }
        .unit { width: 17mm; text-align: center; }
        .qty { width: 31mm; text-align: center; border-color: #a1a1aa; }
        .product { font-weight: 600; }
        .notes { margin-top: 4mm; border: .35mm solid #71717a; border-radius: 2mm; padding: 3mm; font-size: 8pt; color: #52525b; }
        .notes div { border-bottom: .3mm solid #d4d4d8; height: 7mm; }
        footer { margin-top: auto; padding-top: 3mm; text-align: center; font-size: 8pt; color: #71717a; }
      </style>
    </head>
    <body>$body</body>
    </html>
    """.trimIndent()
}

private fun illustrationHtml(category: ConferencePrintCategory): String {
    val bytes = category.illustrationBytes
    if (category.illustrationSource == "upload" && bytes != null && bytes.isNotEmpty()) {
        val mime = detectImageMime(bytes)
        val data = Base64.getEncoder().encodeToString(bytes)
        return "<img class=\"category-art\" src=\"data:" + mime + ";base64," + data +
            "\" style=\"object-position:" + category.illustrationPositionX + "% " +
            category.illustrationPositionY + "%\" />"
    }
    return "<div class=\"glyph\">" +
        illustrationGlyph(category.illustrationSource, category.illustrationKey) +
        "</div>"
}

private fun detectImageMime(bytes: ByteArray): String =
    when {
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> "image/webp"
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"
        else -> "image/png"
    }

private fun illustrationGlyph(source: String?, key: String?): String {
    if (source != "library") return "□"
    return when (key) {
        "panificacao" -> "🥖"
        "boleria" -> "🎂"
        "confeitaria" -> "🧁"
        "frios" -> "🧀"
        "manteigas" -> "🧈"
        "embalagens" -> "📦"
        "etiquetas" -> "🏷️"
        "descartaveis" -> "🥤"
        "higiene" -> "🧤"
        "flexiveis" -> "🛍️"
        "conveniencia" -> "🛒"
        "limpeza" -> "🧹"
        else -> "□"
    }
}

private fun htmlEscape(value: String): String =
    value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

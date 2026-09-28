package com.babycatbe.nevesestoque.feature.reports

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Textos e formatos compartilhados pela tela e pela impressão — mesmos da Web. */

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val MONTH_NAMES = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)
private val SHORT_MONTH_NAMES = listOf(
    "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez",
)

/** "2026-09" → "setembro de 2026". */
fun formatReportMonth(month: String): String {
    val ym = YearMonth.parse(month)
    return "${MONTH_NAMES[ym.monthValue - 1]} de ${ym.year}"
}

/** "2026-09" → "set/26" (eixo do gráfico). */
fun formatReportShortMonth(month: String): String {
    val ym = YearMonth.parse(month)
    return "${SHORT_MONTH_NAMES[ym.monthValue - 1]}/${(ym.year % 100).toString().padStart(2, '0')}"
}

/** "2026-09-30" → "30/09/2026". */
fun formatReportDate(date: String): String =
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

fun formatReportMoney(value: Double): String =
    NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(' ', ' ')

/** Valor compacto para o eixo do gráfico: R$ 950, R$ 12,5 mil, R$ 1,2 mi. */
fun formatReportCompactMoney(value: Double): String {
    val number = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 1 }
    return when {
        value >= 1_000_000 -> "R$ ${number.format(value / 1_000_000)} mi"
        value >= 1_000 -> "R$ ${number.format(value / 1_000)} mil"
        else -> "R$ ${number.format(value)}"
    }
}

fun reportValueLabel(report: MonthlyStockValueReport): String =
    formatReportMoney(report.totalKnown) + if (report.isIncomplete) " *" else ""

fun reportStatusLabel(point: MonthlyStockSeriesPoint): String = buildList {
    if (point.isProvisional) add("Provisório")
    add(if (point.report.hasExactPhysicalClose) "Fechamento físico exato" else "Estimado pelos registros disponíveis")
}.joinToString(" · ")

fun reportIncompleteMessage(report: MonthlyStockValueReport): String {
    val messages = buildList {
        val missing = report.missingPriceProductCount
        if (missing > 0) {
            add(
                "$missing ${if (missing == 1) "Produto tem" else "Produtos têm"} quantidade positiva sem preço " +
                    "histórico válido e não ${if (missing == 1) "entra" else "entram"} no valor conhecido"
            )
        }
        val unknown = report.unknownStockProductCount
        if (unknown > 0) {
            add("$unknown ${if (unknown == 1) "Produto aguarda" else "Produtos aguardam"} Conferência física após mescla")
        }
        val noData = report.noStockDataProductCount
        if (noData > 0) {
            add("$noData ${if (noData == 1) "Produto está" else "Produtos estão"} sem dados de estoque")
        }
    }
    return if (messages.isEmpty()) "" else messages.joinToString(". ") + "."
}

fun reportPeriodLabel(points: List<MonthlyStockSeriesPoint>): String {
    val first = points.firstOrNull() ?: return ""
    val last = points.last()
    return if (first.month == last.month) {
        formatReportMonth(first.month)
    } else {
        "${formatReportMonth(first.month)} a ${formatReportMonth(last.month)}"
    }
}

/** Escala vertical igual à da Web: começa em zero ou pouco abaixo do mínimo, com folga de 12%. */
data class ReportChartScale(val min: Double, val max: Double) {
    fun ratio(value: Double): Double = (value - min) / maxOf(max - min, 1.0)
    val gridValues: List<Double> get() = listOf(0.0, 0.5, 1.0).map { min + (max - min) * it }
}

fun reportChartScale(values: List<Double>): ReportChartScale {
    val max = maxOf(values.maxOrNull() ?: 0.0, 0.0)
    val min = minOf(values.minOrNull() ?: 0.0, 0.0)
    val range = maxOf(max - min, maxOf(max * 0.08, 1.0))
    return ReportChartScale(min = maxOf(0.0, min - range * 0.12), max = max + range * 0.12)
}

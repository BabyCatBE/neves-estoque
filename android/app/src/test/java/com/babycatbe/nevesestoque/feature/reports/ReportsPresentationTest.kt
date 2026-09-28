package com.babycatbe.nevesestoque.feature.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsPresentationTest {

    private fun report(
        total: Double = 100.0,
        missingPrice: Int = 0,
        unknown: Int = 0,
        noData: Int = 0,
        exact: Boolean = false,
    ) = MonthlyStockValueReport(
        referenceDate = "2026-09-30",
        totalKnown = total,
        productCount = 3,
        valuedProductCount = 1,
        missingPriceProductCount = missingPrice,
        unknownStockProductCount = unknown,
        noStockDataProductCount = noData,
        hasMissingPrice = missingPrice > 0,
        hasUnknownStock = unknown > 0,
        hasMissingStockData = noData > 0,
        hasExactPhysicalClose = exact,
        isEstimatedFromAvailableRecords = !exact,
        products = emptyList(),
    )

    private fun point(month: String, provisional: Boolean = false, report: MonthlyStockValueReport = report()) =
        MonthlyStockSeriesPoint(month, "$month-28", provisional, report)

    @Test
    fun monthAndDateFormatsFollowPortuguese() {
        assertEquals("setembro de 2026", formatReportMonth("2026-09"))
        assertEquals("mar/27", formatReportShortMonth("2027-03"))
        assertEquals("30/09/2026", formatReportDate("2026-09-30"))
        assertTrue(formatReportMoney(27744.0).contains("27.744,00"))
        assertEquals("R$ 12,5 mil", formatReportCompactMoney(12500.0))
        assertEquals("R$ 950", formatReportCompactMoney(950.0))
    }

    @Test
    fun statusAndIncompleteMessagesMatchWeb() {
        assertEquals(
            "Provisório · Estimado pelos registros disponíveis",
            reportStatusLabel(point("2026-09", provisional = true)),
        )
        assertEquals("Fechamento físico exato", reportStatusLabel(point("2026-08", report = report(exact = true))))

        val incomplete = report(missingPrice = 1, unknown = 2, noData = 1)
        assertEquals(
            "1 Produto tem quantidade positiva sem preço histórico válido e não entra no valor conhecido. " +
                "2 Produtos aguardam Conferência física após mescla. 1 Produto está sem dados de estoque.",
            reportIncompleteMessage(incomplete),
        )
        assertTrue(reportValueLabel(incomplete).endsWith(" *"))
        assertFalse(reportValueLabel(report()).endsWith(" *"))
        assertEquals("", reportIncompleteMessage(report()))
    }

    @Test
    fun periodLabelAndChartScale() {
        assertEquals("setembro de 2026", reportPeriodLabel(listOf(point("2026-09"))))
        assertEquals(
            "agosto de 2026 a setembro de 2026",
            reportPeriodLabel(listOf(point("2026-08"), point("2026-09"))),
        )
        assertEquals("", reportPeriodLabel(emptyList()))

        val scale = reportChartScale(listOf(100.0, 200.0))
        assertEquals(0.0, scale.min, 0.0)
        assertEquals(224.0, scale.max, 0.0001)
        assertEquals(0.0, scale.ratio(0.0), 0.0)
    }

    @Test
    fun printHtmlContainsSelectedPeriodTableAndChart() {
        val html = buildStockReportPrintHtml(
            listOf(point("2026-08"), point("2026-09", provisional = true, report = report(missingPrice = 1)))
        )
        assertTrue(html.contains("Relatório de Valor do Estoque"))
        assertTrue(html.contains("Período do relatório: agosto de 2026 a setembro de 2026"))
        assertTrue(html.contains("Setembro de 2026"))
        assertTrue(html.contains("<polyline"))
        assertTrue(html.contains("Mês atual provisório"))
        assertTrue(html.contains("* O valor conhecido não inclui"))
        assertTrue(html.contains("Observações da análise"))
    }
}

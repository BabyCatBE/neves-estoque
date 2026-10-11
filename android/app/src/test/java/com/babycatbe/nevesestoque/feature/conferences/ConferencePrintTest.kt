package com.babycatbe.nevesestoque.feature.conferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConferencePrintTest {
    private fun printData(productCount: Int) = ConferencePrintData(
        categories = listOf(
            ConferencePrintCategory(
                id = "panificacao",
                name = "Panificação",
                illustrationSource = null,
                illustrationKey = null,
                products = (1..productCount).map { index ->
                    ConferenceProduct("p$index", "Produto $index", "UN", index)
                },
            ),
        ),
        pendingProductCount = 0,
    )

    @Test
    fun twentyProductsFitOneCompactPage() {
        val html = buildConferencePrintHtml(printData(20))

        assertEquals(1, Regex("<section class=\"page").findAll(html).count())
        assertTrue(html.contains("<section class=\"page compact\">"))
        for (index in 1..20) assertTrue(html.contains(">Produto $index</td>"))
    }

    @Test
    fun twentyTwoProductsFitOneDensePage() {
        val html = buildConferencePrintHtml(printData(22))

        assertEquals(1, Regex("<section class=\"page").findAll(html).count())
        assertTrue(html.contains("<section class=\"page dense\">"))
        assertTrue(html.contains(">Produto 22</td>"))
    }

    @Test
    fun twentyThreeProductsAreSplitWithoutDroppingItems() {
        val html = buildConferencePrintHtml(printData(23))

        assertEquals(2, Regex("<section class=\"page").findAll(html).count())
        assertTrue(html.contains("parte 1"))
        assertTrue(html.contains("parte 2"))
        assertTrue(html.contains(">Produto 23</td>"))
    }
}

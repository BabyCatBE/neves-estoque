package com.babycatbe.nevesestoque.feature.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockViewTest {
    private val categories = listOf(
        StockCategoryRow("cat-b", "Boleria", 2),
        StockCategoryRow("cat-a", "Panificação", 1),
    )
    private val suppliers = listOf(
        StockSupplierRow("sup-b", "Zeta Distribuidora"),
        StockSupplierRow("sup-a", "Alfa Alimentos"),
    )
    private val items = listOf(
        CurrentStockRow("p2", "Farinha", "cat-a", "sup-b", "KG", 3.0, 5.0, 15.0, 2),
        CurrentStockRow("p1", "Açúcar", "cat-a", "sup-a", "KG", 1.0, 4.0, 4.0, 1),
        CurrentStockRow("p3", "Fermento", null, null, "PCT", 2.0, null, null, null),
    )

    @Test
    fun categoryViewKeepsManualOrderAndPendingGroup() {
        val groups = buildCategoryStockGroups(categories, items)
        assertEquals("Cadastro pendente", groups[0].name)
        assertEquals(listOf("Açúcar", "Farinha"), groups[1].items.map { it.productName })
    }

    @Test
    fun searchIgnoresAccents() {
        val groups = buildCategoryStockGroups(categories, items, "acu")
        assertEquals(listOf("Açúcar"), groups.single().items.map { it.productName })
    }

    @Test
    fun supplierViewSeparatesWithoutSupplier() {
        val groups = buildSupplierStockGroups(suppliers, items)
        assertEquals("Alfa Alimentos", groups[0].name)
        assertEquals("Zeta Distribuidora", groups[1].name)
        assertEquals("Sem fornecedor", groups.last().name)
    }

    @Test
    fun alphabeticalViewSortsByName() {
        assertEquals(
            listOf("Açúcar", "Farinha", "Fermento"),
            buildAlphabeticalStockView(items).map { it.productName },
        )
    }

    @Test
    fun valueSummaryMatchesWebRule() {
        val summary = calculateStockValueSummary(items)
        assertEquals(19.0, summary.totalKnown, 0.001)
        assertTrue(summary.hasMissingPrice)
    }

    @Test
    fun missingPriceOnlyWarnsWhenPositiveStock() {
        val summary = calculateStockValueSummary(
            listOf(CurrentStockRow("x", "Zero", null, null, "UN", 0.0, null, null))
        )
        assertFalse(summary.hasMissingPrice)
    }
}

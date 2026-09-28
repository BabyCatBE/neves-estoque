package com.babycatbe.nevesestoque.feature.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogReorderTest {
    private val categoryRows = listOf(
        ProductCategoryRow("b", "Boleria", 2),
        ProductCategoryRow("a", "Panificação", 1),
    )

    private val categoryItems = listOf(
        CategoryListItem("b", "Boleria", 2, 1, null, null),
        CategoryListItem("a", "Panificação", 1, 2, null, null),
    )

    private val products = listOf(
        ProductListItem("p2", "Farinha", "a", "KG", 2, "", null, null, false),
        ProductListItem("p1", "Açúcar", "a", "KG", 1, "", null, null, false),
        ProductListItem("p3", "Bolo", "b", "UN", 1, "", null, null, false),
        ProductListItem("pending", "Pendente", null, "UN", null, "", null, null, false),
    )

    @Test
    fun categoryDraftUsesOfficialSortOrderAndDetectsChange() {
        val ordered = orderedCategories(categoryItems)
        assertEquals(listOf("a", "b"), ordered.map { it.id })
        assertFalse(categoryOrderChanged(ordered, ordered))

        val moved = moveCategoryOrder(ordered, "b", -1)
        assertEquals(listOf("b", "a"), moved.map { it.id })
        assertTrue(categoryOrderChanged(ordered, moved))
    }

    @Test
    fun categoryMoveIsSafeAtBoundsAndForUnknownId() {
        val ordered = orderedCategories(categoryItems)
        assertEquals(ordered, moveCategoryOrder(ordered, "a", -1))
        assertEquals(ordered, moveCategoryOrder(ordered, "missing", 1))
        assertEquals(ordered, moveCategoryOrder(ordered, "a", 2))
    }

    @Test
    fun productDraftKeepsCategoryOrderAndExcludesPendingProducts() {
        val groups = buildProductOrderGroups(categoryRows, products)
        assertEquals(listOf("a", "b"), groups.map { it.categoryId })
        assertEquals(listOf("p1", "p2"), groups.first().items.map { it.id })
        assertTrue(groups.none { group -> group.items.any { it.id == "pending" } })
    }

    @Test
    fun productMoveNeverCrossesCategories() {
        val groups = buildProductOrderGroups(categoryRows, products)
        val moved = moveProductOrder(groups, "a", "p2", -1)
        assertEquals(listOf("p2", "p1"), moved.first().items.map { it.id })
        assertEquals(listOf("p3"), moved[1].items.map { it.id })

        assertEquals(moved, moveProductOrder(moved, "b", "p1", 1))
    }

    @Test
    fun productChangesContainOnlyChangedCategoryWithCompleteOrder() {
        val original = buildProductOrderGroups(categoryRows, products)
        assertTrue(changedProductOrders(original, original).isEmpty())

        val draft = moveProductOrder(original, "a", "p2", -1)
        val changes = changedProductOrders(original, draft)

        assertEquals(1, changes.size)
        assertEquals("a", changes.single().categoryId)
        assertEquals(listOf("p2", "p1"), changes.single().productIds)
    }
}

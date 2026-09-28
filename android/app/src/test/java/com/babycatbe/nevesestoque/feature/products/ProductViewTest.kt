package com.babycatbe.nevesestoque.feature.products

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class ProductViewTest {
    private val categories = listOf(
        ProductCategoryRow("b", "Boleria", 2),
        ProductCategoryRow("a", "Panificação", 1),
    )
    private val products = listOf(
        ProductListItem("2", "Farinha", "a", "KG", 2, "", 3.0, 5.0, false),
        ProductListItem("1", "Açúcar", "a", "KG", 1, "", 1.0, 4.0, false),
        ProductListItem("3", "Fermento", null, "PCT", null, "", 2.0, null, false),
    )

    @Test
    fun searchIgnoresAccents() {
        assertEquals(
            listOf("Açúcar"),
            filterAndSortProducts(products, "acu").map { it.name },
        )
    }

    @Test
    fun categoryGroupingKeepsManualOrderAndPendingFirst() {
        val groups = buildProductGroups(categories, products)
        assertEquals("Cadastro pendente", groups.first().name)
        assertEquals(listOf("Açúcar", "Farinha"), groups[1].items.map { it.name })
    }

    @Test
    fun usageRequiresAtLeastTwoConferences() {
        val result = calculateProductUsageInsights(
            conferences = listOf(
                UsageConferencePoint("2026-09-01T10:00:00Z", "2026-09-01T10:00:00Z", 10.0)
            ),
            entries = emptyList(),
            currentQuantity = 5.0,
            referenceTimeMillis = OffsetDateTime.parse("2026-09-28T10:00:00Z").toInstant().toEpochMilli(),
        )
        assertEquals(UsageReason.NeedsTwoConferences, result.reason)
    }

    @Test
    fun usageCalculatesReadyHistoryLikeWebRule() {
        val conferences = listOf(
            UsageConferencePoint("2026-08-01T10:00:00Z", "2026-08-01T10:00:00Z", 100.0),
            UsageConferencePoint("2026-08-15T10:00:00Z", "2026-08-15T10:00:00Z", 80.0),
            UsageConferencePoint("2026-09-01T10:00:00Z", "2026-09-01T10:00:00Z", 60.0),
            UsageConferencePoint("2026-09-20T10:00:00Z", "2026-09-20T10:00:00Z", 40.0),
        )
        val result = calculateProductUsageInsights(
            conferences = conferences,
            entries = emptyList(),
            currentQuantity = 30.0,
            referenceTimeMillis = OffsetDateTime.parse("2026-09-28T10:00:00Z").toInstant().toEpochMilli(),
        )
        assertEquals(UsageStatus.Ready, result.status)
        assertEquals(3, result.validIntervals)
    }
}

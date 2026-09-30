package com.babycatbe.nevesestoque.feature.purchases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Inclui os casos de src/features/purchases/lib/purchaseProjection.test.ts (Web) para garantir paridade. */
class PurchasesLogicTest {

    private fun input(
        usageReady: Boolean = true,
        dailyAverage: Double? = 3.0,
        currentQuantity: Double? = 30.0,
        unit: String = "UN",
        hasSupplier: Boolean = true,
        supplierActive: Boolean = true,
        frequency: Int? = 7,
        weekday: Int? = null,
        delivery: Int? = 2,
        margin: Int? = 2,
        today: Int = 5,
    ) = PurchaseProjectionInput(
        usageReady, dailyAverage, currentQuantity, unit, hasSupplier, supplierActive,
        frequency, weekday, delivery, margin, today,
    )

    @Test
    fun projectionTargetsStockUntilNextOrderArrivesWithMargin() {
        val result = calculatePurchaseProjection(input())
        assertEquals(11.0, result.cycleDays!!, 0.0)
        assertEquals(33.0, result.targetStock!!, 0.0)
        assertEquals(3.0, result.suggestedQuantity!!, 0.0)
        assertEquals(PurchaseProjectionStatus.Recommended, result.status)
    }

    @Test
    fun weeklyFrequencyUsesNextRealOccurrenceAndTodayMeansNextWeek() {
        assertEquals(5, daysUntilNextOrder(7, 2, 4)) // quinta → terça
        assertEquals(7, daysUntilNextOrder(7, 2, 2)) // terça → próxima terça
        assertEquals(1, daysUntilNextOrder(7, 1, 7)) // domingo → segunda
        assertEquals(14, daysUntilNextOrder(14, 2, 4)) // dia fixo só vale para frequência semanal
    }

    @Test
    fun riskClassification() {
        assertEquals(PurchaseRisk.Protected, classifyPurchaseRisk(10.0, 5, 3))
        assertEquals(PurchaseRisk.Vulnerable, classifyPurchaseRisk(6.0, 5, 3))
        assertEquals(PurchaseRisk.Risk, classifyPurchaseRisk(4.0, 5, 3))
        assertEquals(PurchaseRisk.Unknown, classifyPurchaseRisk(4.0, null, 3))
    }

    @Test
    fun roundingWholePackagesUpAndKgToTwoDecimals() {
        assertEquals(13.0, roundPurchaseSuggestion(12.01, "UN"), 0.0)
        assertEquals(12.35, roundPurchaseSuggestion(12.341, "KG"), 1e-9)
        assertEquals(12.0, roundPurchaseSuggestion(12.0, "UN"), 0.0)
        assertEquals(0.0, roundPurchaseSuggestion(-1.0, "UN"), 0.0)
    }

    @Test
    fun noAutomaticRecommendationWithoutRequirements() {
        val noHistory = calculatePurchaseProjection(input(usageReady = false))
        assertEquals(PurchaseProjectionStatus.InsufficientHistory, noHistory.status)
        assertNull(noHistory.suggestedQuantity)

        assertEquals(PurchaseProjectionStatus.NoConsumption, calculatePurchaseProjection(input(dailyAverage = 0.0)).status)
        assertEquals(PurchaseProjectionStatus.MissingStock, calculatePurchaseProjection(input(currentQuantity = null)).status)

        val noSupplier = calculatePurchaseProjection(input(hasSupplier = false))
        assertEquals(PurchaseProjectionStatus.MissingSupplier, noSupplier.status)
        assertEquals(10.0, noSupplier.coverageDays!!, 0.0)
        assertEquals(PurchaseRisk.Protected, noSupplier.risk)

        assertEquals(PurchaseProjectionStatus.MissingSupplierConfig, calculatePurchaseProjection(input(frequency = null)).status)
        assertEquals(PurchaseProjectionStatus.StockSufficient, calculatePurchaseProjection(input(currentQuantity = 40.0)).status)
    }

    private fun product(
        id: String,
        name: String,
        status: PurchaseProjectionStatus,
        risk: PurchaseRisk = PurchaseRisk.Unknown,
        coverage: Double? = null,
        quantity: Double? = 1.0,
        categoryId: String? = "c1",
        sortOrder: Int? = null,
        currentSupplier: String? = "s1",
        suppliers: Set<String> = setOf("s1"),
    ) = PurchaseProduct(
        id, name, "UN", categoryId, sortOrder, quantity, currentSupplier, suppliers, null,
        PurchaseProjection(status, suggestedQuantity = if (status == PurchaseProjectionStatus.Recommended) 2.0 else null, coverageDays = coverage, risk = risk),
    )

    @Test
    fun stockGroupsOrderRecommendedByRiskAndRemainingByStock() {
        val (recommended, remaining) = stockPurchaseGroups(
            listOf(
                product("a", "Açúcar", PurchaseProjectionStatus.Recommended, PurchaseRisk.Protected, 9.0),
                product("b", "Batata", PurchaseProjectionStatus.Recommended, PurchaseRisk.Risk, 1.0),
                product("c", "Café", PurchaseProjectionStatus.Recommended, PurchaseRisk.Risk, 0.5),
                product("d", "Doce", PurchaseProjectionStatus.InsufficientHistory, quantity = null),
                product("e", "Erva", PurchaseProjectionStatus.InsufficientHistory, quantity = 5.0),
                product("f", "Farinha", PurchaseProjectionStatus.StockSufficient, quantity = 2.0),
            )
        )
        assertEquals(listOf("c", "b", "a"), recommended.map { it.productId })
        assertEquals(listOf("f", "e", "d"), remaining.map { it.productId })
    }

    @Test
    fun supplierModeKeepsHistoryAndDefersToLatestSupplier() {
        val items = supplierPurchaseProducts(
            listOf(
                product("a", "Açúcar", PurchaseProjectionStatus.Recommended),
                product("b", "Batata", PurchaseProjectionStatus.Recommended, currentSupplier = "s2", suppliers = setOf("s1", "s2")),
                product("c", "Café", PurchaseProjectionStatus.Recommended, currentSupplier = "s2", suppliers = setOf("s2")),
            ),
            "s1",
        )
        assertEquals(listOf("a", "b"), items.map { it.productId })
        assertEquals(PurchaseProjectionStatus.OtherSupplier, items[1].projection.status)
        assertNull(items[1].projection.suggestedQuantity)

        val sorted = sortSupplierItems(
            listOf(
                PurchaseListItem("z", "Zinco", "UN", 1.0, null, isManualAddition = true),
                items[1].toListItem(),
                items[0].toListItem(),
            )
        )
        assertEquals(listOf("a", "b", "z"), sorted.map { it.productId })
    }

    @Test
    fun categoryModePreservesManualOrder() {
        val items = categoryPurchaseItems(
            listOf(
                product("a", "Açúcar", PurchaseProjectionStatus.StockSufficient, sortOrder = 2),
                product("b", "Batata", PurchaseProjectionStatus.StockSufficient, sortOrder = 1),
                product("x", "Outro", PurchaseProjectionStatus.StockSufficient, categoryId = "c2", sortOrder = 0),
            ),
            "c1",
        )
        assertEquals(listOf("b", "a"), items.map { it.productId })
    }

    @Test
    fun quantitiesAndOrderText() {
        assertEquals(2.5, parsePurchaseQuantity(" 2,5 ")!!, 0.0)
        assertNull(parsePurchaseQuantity("abc"))
        assertEquals("3", stepPurchaseQuantity("2", 1))
        assertEquals("0", stepPurchaseQuantity("0,5", -1))
        assertEquals("1,5", formatEditablePurchaseQuantity(1.5))
        assertEquals("12,35", formatEditablePurchaseQuantity(12.35))

        val items = listOf(
            PurchaseListItem("a", "Farinha", "SC", 1.0, null),
            PurchaseListItem("b", "Óleo", "CX", null, null),
        )
        val empty = buildPurchaseOrderText("Lista", items, emptySet(), emptyMap())
        assertEquals("Selecione pelo menos um produto.", (empty as PurchaseOrderResult.Invalid).message)

        val invalid = buildPurchaseOrderText("Lista", items, setOf("a", "b"), mapOf("a" to "2", "b" to "0"))
        assertEquals(setOf("b"), (invalid as PurchaseOrderResult.Invalid).invalidProductIds)

        val ready = buildPurchaseOrderText("Lista — Moinho", items, setOf("a", "b"), mapOf("a" to "2,5", "b" to "1"))
        assertEquals(
            "Lista — Moinho\n\nFarinha — 2,5 — SC\nÓleo — 1 — CX",
            (ready as PurchaseOrderResult.Ready).text,
        )
    }

    @Test
    fun typedQuantitiesSelectAndBuildTheSameCopyAndShareText() {
        val items = listOf(
            PurchaseListItem("a", "Farinha", "KG", 10.0, null),
            PurchaseListItem("b", "Açúcar", "KG", 5.0, null),
        )
        var selected = selectPurchaseQuantity(emptySet(), "a", "10")
        selected = selectPurchaseQuantity(selected, "b", "5,5")
        val result = buildPurchaseOrderText("Lista", items, selected, mapOf("a" to "10", "b" to "5,5"))
        assertEquals("Lista\n\nFarinha — 10 — KG\nAçúcar — 5,5 — KG", (result as PurchaseOrderResult.Ready).text)
        for (invalid in listOf("", "0", "-1", "abc", "NaN", "Infinity")) {
            assertEquals(setOf("b"), selectPurchaseQuantity(selected, "a", invalid))
        }
        assertEquals(setOf("a", "b"), selectPurchaseQuantity(setOf("b"), "a", "0,5"))
    }

    @Test
    fun messagesAndSuggestionFill() {
        val recommended = PurchaseListItem(
            "a", "Farinha", "KG", 1.0,
            PurchaseProjection(PurchaseProjectionStatus.Recommended, suggestedQuantity = 2.5, cycleDays = 11.0),
        )
        assertEquals("Sugestão: 2,5 KG · cobertura planejada: 11 dias", purchaseProjectionMessage(recommended))
        assertEquals("2,5", purchaseSuggestionToFill(recommended))
        assertNull(purchaseSuggestionToFill(recommended.copy(projection = null)))
        assertEquals("Risco de falta", purchaseRiskLabel(PurchaseRisk.Risk))
        assertNull(purchaseRiskLabel(PurchaseRisk.Unknown))
        assertTrue(matchesPurchaseSearch(listOf("Moinho Nordeste", null), "nordeste"))
    }
}

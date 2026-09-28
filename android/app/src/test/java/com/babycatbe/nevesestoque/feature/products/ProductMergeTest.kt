package com.babycatbe.nevesestoque.feature.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMergeTest {
    private fun product(
        id: String,
        createdAt: String,
        unit: String = "UN",
        categoryId: String? = "category",
        initialPrice: Double? = null,
        name: String = id,
        stockRequiresConference: Boolean = false,
    ) = ProductDetails(
        id = id,
        name = name,
        categoryId = categoryId,
        unit = unit,
        createdAt = createdAt,
        initialStockQuantity = null,
        initialStockAt = null,
        initialPrice = initialPrice,
        initialPriceAt = null,
        currentQuantity = null,
        currentPrice = null,
        currentValue = null,
        stockRequiresConference = stockRequiresConference,
        priceHistory = emptyList(),
        usageInsights = ProductUsageInsights(
            status = UsageStatus.Insufficient,
            reason = UsageReason.NeedsTwoConferences,
            conferencesUsed = 0,
            validIntervals = 0,
            ignoredNegativeIntervals = 0,
            historyDays = null,
            intervalStart = null,
            intervalEnd = null,
            entriesDuringInterval = null,
            estimatedConsumption = null,
            dailyAverage = null,
            weeklyAverage = null,
            coverageDays = null,
        ),
    )

    @Test
    fun olderProductAlwaysSurvives() {
        val older = product("a", "2026-09-01T10:00:00Z")
        val newer = product("b", "2026-09-02T10:00:00Z")
        assertEquals("a", determineMergePair(newer, older).survivor.id)
    }

    @Test
    fun equalTimestampUsesIdAsTieBreaker() {
        val a = product("a", "2026-09-01T10:00:00Z")
        val b = product("b", "2026-09-01T10:00:00Z")
        assertEquals("a", determineMergePair(b, a).survivor.id)
    }

    @Test
    fun sameUnitKeepsBothFactorsAtOne() {
        assertEquals(
            ProductMergeFactors(1.0, 1.0),
            calculateMergeUnitFactors("UN", "UN", "UN", null, null),
        )
    }

    @Test
    fun absorbedHistoryConvertsToSurvivorUnit() {
        val factors = calculateMergeUnitFactors("CX", "UN", "CX", 1.0, 12.0)
        assertEquals(1.0, factors.survivorFactor, 0.0000001)
        assertEquals(1.0 / 12.0, factors.absorbedFactor, 0.0000001)
    }

    @Test
    fun survivorHistoryConvertsWhenFinalUnitIsAbsorbedUnit() {
        val factors = calculateMergeUnitFactors("CX", "UN", "UN", 1.0, 12.0)
        assertEquals(12.0, factors.survivorFactor, 0.0000001)
        assertEquals(1.0, factors.absorbedFactor, 0.0000001)
    }

    @Test
    fun validationRejectsUnitOutsideCurrentPair() {
        val pair = determineMergePair(
            product("a", "2026-09-01T10:00:00Z", unit = "CX"),
            product("b", "2026-09-02T10:00:00Z", unit = "UN"),
        )
        val result = validateProductMergeDraft(
            pair = pair,
            finalName = "Produto",
            finalCategoryId = "category",
            finalUnit = "KG",
            survivorEquivalentQuantity = "1",
            absorbedEquivalentQuantity = "12",
            initialPriceSource = MergeInitialPriceSource.None,
        )
        assertNull(result.draft)
        assertEquals("A unidade final deve ser uma das unidades atuais.", result.error)
    }

    @Test
    fun validationRequiresEquivalenceWhenUnitsDiffer() {
        val pair = determineMergePair(
            product("a", "2026-09-01T10:00:00Z", unit = "CX"),
            product("b", "2026-09-02T10:00:00Z", unit = "UN"),
        )
        val result = validateProductMergeDraft(
            pair = pair,
            finalName = "Produto",
            finalCategoryId = "category",
            finalUnit = "CX",
            survivorEquivalentQuantity = "",
            absorbedEquivalentQuantity = "",
            initialPriceSource = MergeInitialPriceSource.None,
        )
        assertNull(result.draft)
        assertNotNull(result.error)
    }

    @Test
    fun defaultPriceSourcePrefersOldestAvailableReference() {
        val pair = determineMergePair(
            product("a", "2026-09-01T10:00:00Z", initialPrice = 10.0),
            product("b", "2026-09-02T10:00:00Z", initialPrice = 20.0),
        )
        assertEquals(MergeInitialPriceSource.Survivor, defaultMergeInitialPriceSource(pair))
    }

    @Test
    fun searchIgnoresCaseAndAccents() {
        assertTrue(matchesMergeSearch("Açúcar Cristal", "acucar"))
        assertTrue(matchesMergeSearch("Farinha de Trigo", "TRIGO"))
        assertFalse(matchesMergeSearch("Farinha de Trigo", "cafe"))
    }

    @Test
    fun ambiguousFailureReconciliationRequiresAbsorbedToDisappearAndConferenceFlag() {
        val survivor = product(
            id = "a",
            createdAt = "2026-09-01T10:00:00Z",
            name = "Produto Final",
            stockRequiresConference = true,
        )
        val absorbed = product("b", "2026-09-02T10:00:00Z")
        val pair = ProductMergePair(survivor, absorbed)
        val input = ProductMergeDraft(
            productAId = "a",
            productBId = "b",
            finalName = "Produto Final",
            finalCategoryId = "category",
            finalUnit = "UN",
            survivorEquivalentQuantity = null,
            absorbedEquivalentQuantity = null,
            initialPriceSource = MergeInitialPriceSource.None,
        )
        val active = listOf(
            ProductListItem(
                id = "a",
                name = "Produto Final",
                categoryId = "category",
                unit = "UN",
                sortOrder = 1,
                createdAt = survivor.createdAt,
                currentQuantity = null,
                currentPrice = null,
                stockRequiresConference = true,
            )
        )
        assertTrue(isMergeAppliedSnapshot(input, pair, active, survivor))
        assertFalse(
            isMergeAppliedSnapshot(
                input,
                pair,
                active + ProductListItem(
                    id = "b",
                    name = "b",
                    categoryId = "category",
                    unit = "UN",
                    sortOrder = 2,
                    createdAt = absorbed.createdAt,
                    currentQuantity = null,
                    currentPrice = null,
                    stockRequiresConference = false,
                ),
                survivor,
            )
        )
    }
}

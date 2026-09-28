package com.babycatbe.nevesestoque.feature.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Casos portados dos testes Web (src/features/reports/lib, arquivos .test.ts) para garantir paridade. */
class ReportsCalculationTest {

    private fun product(
        id: String = "p1",
        createdAt: String = "2026-08-01T12:00:00-03:00",
        deletedAt: String? = null,
        initialStockQuantity: Double? = null,
        initialStockAt: String? = null,
        initialPrice: Double? = null,
        initialPriceAt: String? = null,
    ) = HistoricalReportProduct(
        id, "Produto", "UN", createdAt, deletedAt,
        initialStockQuantity, initialStockAt, initialPrice, initialPriceAt,
    )

    private fun entry(
        id: String = "ei1",
        effectiveAt: String = "2026-09-28T12:00:00-03:00",
        quantity: Double = 5.0,
        unitPrice: Double? = 8.0,
        createdAt: String = effectiveAt,
        position: Int = 1,
        productId: String = "p1",
    ) = HistoricalEntryFact(id, productId, effectiveAt, createdAt, quantity, unitPrice, position)

    private fun conference(
        id: String = "c1",
        effectiveAt: String = "2026-09-25T12:00:00-03:00",
        quantity: Double = 10.0,
        createdAt: String = effectiveAt,
        productId: String = "p1",
    ) = HistoricalConferenceFact(id, productId, effectiveAt, createdAt, quantity)

    // ---------- Snapshot ----------

    @Test
    fun snapshotUsesConferencePlusLaterEntriesWithoutInventingConsumption() {
        val result = calculateProductStockSnapshot(
            "2026-09-30", product(),
            listOf(conference()),
            listOf(entry(quantity = 5.0, unitPrice = 8.5)),
        )
        assertEquals(15.0, result.quantity!!, 0.0)
        assertEquals(HistoricalQuantitySource.ConferencePlusEntries, result.quantitySource)
        assertEquals(127.5, result.value!!, 0.0)
        assertFalse(result.hasExactPhysicalClose)
    }

    @Test
    fun snapshotDoesNotAddEntryOlderThanLastConference() {
        val result = calculateProductStockSnapshot(
            "2026-09-30", product(),
            listOf(conference()),
            listOf(
                entry("e-old", "2026-09-20T12:00:00-03:00", 7.0, null),
                entry("e-new", "2026-09-28T12:00:00-03:00", 5.0, null),
            ),
        )
        assertEquals(15.0, result.quantity!!, 0.0)
    }

    @Test
    fun snapshotMarksExactPhysicalCloseOnReferenceDay() {
        val result = calculateProductStockSnapshot(
            "2026-09-30", product(), listOf(conference(effectiveAt = "2026-09-30T10:00:00-03:00", quantity = 12.0)), emptyList(),
        )
        assertEquals(12.0, result.quantity!!, 0.0)
        assertTrue(result.hasExactPhysicalClose)
    }

    @Test
    fun snapshotBreaksConferenceTiesByEffectiveCreatedAndId() {
        val result = calculateProductStockSnapshot(
            "2026-09-30", product(),
            listOf(
                conference("a", "2026-09-25T12:00:00-03:00", 1.0, "2026-09-25T12:00:00-03:00"),
                conference("b", "2026-09-25T12:00:00-03:00", 2.0, "2026-09-25T13:00:00-03:00"),
                conference("c", "2026-09-25T12:00:00-03:00", 3.0, "2026-09-25T13:00:00-03:00"),
            ),
            emptyList(),
        )
        assertEquals("c", result.checkpointConferenceId)
        assertEquals(3.0, result.quantity!!, 0.0)
    }

    @Test
    fun snapshotUsesInitialStockAsCheckpointAndEntriesOnlyWithoutCheckpoint() {
        val withInitial = calculateProductStockSnapshot(
            "2026-09-30",
            product(initialStockQuantity = 4.0, initialStockAt = "2026-09-01T12:00:00-03:00"),
            emptyList(),
            listOf(entry(effectiveAt = "2026-09-10T12:00:00-03:00", quantity = 2.0)),
        )
        assertEquals(6.0, withInitial.quantity!!, 0.0)
        assertEquals(HistoricalQuantitySource.InitialStockPlusEntries, withInitial.quantitySource)

        val entriesOnly = calculateProductStockSnapshot(
            "2026-09-30", product(), emptyList(), listOf(entry(quantity = 3.0)),
        )
        assertEquals(3.0, entriesOnly.quantity!!, 0.0)
        assertEquals(HistoricalQuantitySource.EntriesOnly, entriesOnly.quantitySource)

        val none = calculateProductStockSnapshot("2026-09-30", product(), emptyList(), emptyList())
        assertNull(none.quantity)
        assertFalse(none.hasStockData)
        assertNull(none.value)
    }

    @Test
    fun snapshotIgnoresBlankPriceBonusAndFuturePrice() {
        val result = calculateProductStockSnapshot(
            "2026-09-30", product(),
            listOf(conference()),
            listOf(
                entry("real", "2026-09-26T12:00:00-03:00", 1.0, 6.0),
                entry("blank", "2026-09-27T12:00:00-03:00", 1.0, null),
                entry("bonus", "2026-09-28T12:00:00-03:00", 1.0, 0.0),
                entry("future", "2026-10-02T12:00:00-03:00", 1.0, 99.0),
            ),
        )
        assertEquals(13.0, result.quantity!!, 0.0)
        assertEquals(6.0, result.price!!, 0.0)
        assertEquals(78.0, result.value!!, 0.0)
    }

    @Test
    fun snapshotUsesInitialPriceOnlyWhenAlreadyAvailable() {
        val p = product(initialPrice = 4.0, initialPriceAt = "2026-10-01T12:00:00-03:00")
        val before = calculateProductStockSnapshot("2026-09-30", p, listOf(conference()), emptyList())
        assertNull(before.price)
        assertTrue(before.hasMissingPrice)
        assertNull(before.value)

        val after = calculateProductStockSnapshot("2026-10-31", p, listOf(conference()), emptyList())
        assertEquals(HistoricalPriceSource.Initial, after.priceSource)
        assertEquals(40.0, after.value!!, 0.0)
    }

    @Test
    fun snapshotZeroQuantityIsZeroValueEvenWithoutPrice() {
        val result = calculateProductStockSnapshot(
            "2026-09-30", product(), listOf(conference(quantity = 0.0)), emptyList(),
        )
        assertEquals(0.0, result.value!!, 0.0)
        assertFalse(result.hasMissingPrice)
    }

    @Test
    fun dateKeyUsesBahiaTimeZone() {
        assertEquals("2026-09-30", historicalDateKey("2026-10-01T02:30:00Z"))
        assertEquals("2026-10-01", historicalDateKey("2026-10-01T03:30:00Z"))
        assertEquals("2026-12-31", reportCurrentDateKey(Instant.parse("2027-01-01T01:30:00Z")))
        assertEquals("2027-01-01", reportCurrentDateKey(Instant.parse("2027-01-01T03:30:00Z")))
    }

    // ---------- Relatório ----------

    @Test
    fun productLifecycleControlsInclusion() {
        val deleted = product(deletedAt = "2026-09-15T12:00:00-03:00")
        assertTrue(isProductIncludedAtReference(deleted, emptyList(), emptyList(), "2026-08-31"))
        assertFalse(isProductIncludedAtReference(deleted, emptyList(), emptyList(), "2026-09-30"))

        val lifecycle = listOf(
            HistoricalProductLifecycleFact(10, "p1", LifecycleAction.SoftDelete, "2026-09-10T12:00:00-03:00"),
            HistoricalProductLifecycleFact(11, "p1", LifecycleAction.Restore, "2026-10-05T12:00:00-03:00"),
        )
        assertFalse(isProductIncludedAtReference(product(), emptyList(), emptyList(), "2026-09-30", lifecycle))
        assertTrue(isProductIncludedAtReference(product(), emptyList(), emptyList(), "2026-10-31", lifecycle))

        val sameDay = listOf(
            HistoricalProductLifecycleFact(20, "p1", LifecycleAction.SoftDelete, "2026-09-27T10:00:00-03:00"),
            HistoricalProductLifecycleFact(21, "p1", LifecycleAction.Restore, "2026-09-27T10:05:00-03:00"),
        )
        assertTrue(isProductIncludedAtReference(product(), emptyList(), emptyList(), "2026-09-27", sameDay))

        val future = product(createdAt = "2026-10-10T12:00:00-03:00")
        assertFalse(isProductIncludedAtReference(future, emptyList(), emptyList(), "2026-09-30"))
        assertTrue(
            isProductIncludedAtReference(
                future, listOf(entry(effectiveAt = "2026-09-20T12:00:00-03:00")), emptyList(), "2026-09-30",
            )
        )
    }

    @Test
    fun mergeRequiresNewConferenceOnlyAfterTheMerge() {
        val merges = listOf(HistoricalProductMergeFact(10, "p1", "2026-09-27T10:00:00-03:00"))
        assertTrue(requiresMergeReconfirmationAtReference("p1", "2026-09-30", merges, listOf(conference())))
        assertFalse(
            requiresMergeReconfirmationAtReference(
                "p1", "2026-09-30", merges,
                listOf(conference(effectiveAt = "2026-09-27T10:05:00-03:00", createdAt = "2026-09-27T10:05:10-03:00")),
            )
        )
        assertFalse(requiresMergeReconfirmationAtReference("p1", "2026-08-31", merges, emptyList()))
    }

    @Test
    fun reportAggregatesKnownValueAndFlagsIncompleteItems() {
        val facts = MonthlyStockReportFacts(
            products = listOf(
                product("p1"),
                product("p2"),
                product("p3"),
                product("p4"),
            ),
            conferences = listOf(
                conference(productId = "p1"),
                conference(id = "c2", productId = "p2", quantity = 3.0),
                conference(id = "c4", productId = "p4", quantity = 2.0),
            ),
            entries = listOf(entry(productId = "p1"), entry(id = "e4", productId = "p4", unitPrice = 5.0)),
            merges = listOf(HistoricalProductMergeFact(1, "p4", "2026-09-29T10:00:00-03:00")),
        )

        val report = calculateMonthlyStockValueReport("2026-09-30", facts)

        assertEquals(4, report.productCount)
        assertEquals(120.0, report.totalKnown, 0.0) // p1: (10 + 5) × 8
        assertEquals(1, report.valuedProductCount)
        assertEquals(1, report.missingPriceProductCount) // p2
        assertEquals(1, report.noStockDataProductCount) // p3
        assertEquals(1, report.unknownStockProductCount) // p4 aguardando Conferência pós-mescla
        assertTrue(report.isIncomplete)
        assertTrue(report.isEstimatedFromAvailableRecords)
    }

    // ---------- Série ----------

    private fun seriesFacts(
        entries: List<HistoricalEntryFact> = listOf(
            entry(effectiveAt = "2026-11-20T12:00:00-03:00", quantity = 5.0, unitPrice = 3.0),
        ),
        conferences: List<HistoricalConferenceFact> = listOf(
            conference(effectiveAt = "2026-11-30T12:00:00-03:00", quantity = 12.0),
        ),
    ) = MonthlyStockReportFacts(
        products = listOf(
            product(
                createdAt = "2026-11-10T12:00:00-03:00",
                initialStockQuantity = 10.0, initialStockAt = "2026-11-10T12:00:00-03:00",
                initialPrice = 2.0, initialPriceAt = "2026-11-10T12:00:00-03:00",
            )
        ),
        entries = entries,
        conferences = conferences,
    )

    @Test
    fun seriesGeneratesClosedMonthsUntilProvisionalCurrentMonth() {
        val series = calculateMonthlyStockValueSeries("2027-01-15", seriesFacts())

        assertEquals("2026-11", series.startMonth)
        assertEquals("2027-01", series.endMonth)
        assertEquals(listOf("2026-11", "2026-12", "2027-01"), series.points.map { it.month })
        assertEquals(listOf("2026-11-30", "2026-12-31", "2027-01-15"), series.points.map { it.referenceDate })
        assertEquals(listOf(false, false, true), series.points.map { it.isProvisional })
        series.points.forEach { assertEquals(36.0, it.report.totalKnown, 0.0) }
    }

    @Test
    fun seriesHandlesLeapYearAndPeriodFilter() {
        assertEquals("2028-02-29", lastDayOfMonth("2028-02"))
        assertEquals("2027-02-28", lastDayOfMonth("2027-02"))

        val points = calculateMonthlyStockValueSeries("2027-01-15", seriesFacts()).points
        assertEquals(listOf("2026-12", "2027-01"), filterSeriesPoints(points, "2026-12", "2027-01").map { it.month })
        assertTrue(runCatching { filterSeriesPoints(points, "2027-01", "2026-12") }.isFailure)
    }

    @Test
    fun factsFromTheFutureAreNotAnticipated() {
        val facts = seriesFacts(
            entries = listOf(
                entry("past", "2026-11-20T14:00:00-03:00", 5.0, 3.0),
                entry("future-same-day", "2026-11-20T18:00:00-03:00", 100.0, 50.0),
            ),
            conferences = emptyList(),
        )
        val available = filterFactsAvailableAt(facts, Instant.parse("2026-11-20T19:00:00Z"))
        assertEquals(listOf("past"), available.entries.map { it.id })

        val series = calculateMonthlyStockValueSeries("2026-11-20", available)
        assertEquals(45.0, series.points.single().report.totalKnown, 0.0) // (10 + 5) × 3
    }

    @Test
    fun emptyFactsProduceEmptySeries() {
        val series = calculateMonthlyStockValueSeries("2026-09-27", MonthlyStockReportFacts())
        assertNull(series.startMonth)
        assertTrue(series.points.isEmpty())
    }
}

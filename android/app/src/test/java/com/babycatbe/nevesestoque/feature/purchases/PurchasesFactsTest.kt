package com.babycatbe.nevesestoque.feature.purchases

import com.babycatbe.nevesestoque.data.supabase.FakeKeysetTable
import com.babycatbe.nevesestoque.data.supabase.testUuid
import com.babycatbe.nevesestoque.feature.products.UsageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

/**
 * Leitura histórica completa de Compras (Android). Mesmo cenário de
 * src/features/purchases/api/purchases.test.ts (Web).
 *
 * P1 tem 4 conferências válidas a cada 14 dias (consumo de 2/dia) e uma única Entrada, do
 * fornecedor S2, gravada por último (maior id): ela só chega numa página posterior quando o
 * servidor corta as respostas em 2 linhas. Com a Entrada: média ponderada 168/70 = 2,4/dia.
 */
class PurchasesFactsTest {

    private val now = Instant.parse("2026-09-20T12:00:00Z").toEpochMilli()
    private val dayMs = 24L * 60 * 60 * 1000
    private fun daysAgo(days: Int) = Instant.ofEpochMilli(now - days * dayMs).toString()
    private fun daysAhead(days: Int) = Instant.ofEpochMilli(now + days * dayMs).toString()

    private val p1 = testUuid(10, 1)
    private val p2 = testUuid(10, 2)
    private val s1 = testUuid(20, 1)
    private val s2 = testUuid(20, 2)

    // Conferências na Lixeira não chegam do servidor (filtro deleted_at is null na consulta);
    // a de data futura chega e é descartada pelo cálculo.
    private val conferences = listOf(
        PurchaseConferenceRow(testUuid(30, 1), daysAgo(50), daysAgo(50)),
        PurchaseConferenceRow(testUuid(30, 2), daysAgo(36), daysAgo(36)),
        PurchaseConferenceRow(testUuid(30, 3), daysAgo(22), daysAgo(22)),
        PurchaseConferenceRow(testUuid(30, 4), daysAgo(8), daysAgo(8)),
        PurchaseConferenceRow(testUuid(30, 6), daysAhead(3), daysAgo(1)),
    )
    private val conferenceItems = listOf(
        PurchaseConferenceItemRow(testUuid(31, 1), testUuid(30, 1), p1, 100.0),
        PurchaseConferenceItemRow(testUuid(31, 2), testUuid(30, 2), p1, 72.0),
        PurchaseConferenceItemRow(testUuid(31, 3), testUuid(30, 3), p1, 44.0),
        PurchaseConferenceItemRow(testUuid(31, 4), testUuid(30, 4), p1, 16.0),
        // Item de conferência na Lixeira (cabeçalho não veio): ignorado.
        PurchaseConferenceItemRow(testUuid(31, 5), testUuid(30, 5), p1, 999.0),
        PurchaseConferenceItemRow(testUuid(31, 6), testUuid(30, 6), p1, 0.0),
    )
    private val entries = listOf(
        PurchaseEntryRow(testUuid(40, 1), s1, daysAgo(40)),
        PurchaseEntryRow(testUuid(40, 2), s1, daysAgo(30)),
        PurchaseEntryRow(testUuid(40, 3), s2, daysAgo(15)),
    )
    private val entryItems = listOf(
        PurchaseEntryItemRow(testUuid(41, 1), testUuid(40, 1), p2, 5.0),
        PurchaseEntryItemRow(testUuid(41, 2), testUuid(40, 2), p2, 5.0),
        PurchaseEntryItemRow(testUuid(41, 3), testUuid(40, 3), p1, 14.0),
    )

    private fun supplier(id: String) = PurchaseSupplierRow(
        id = id, name = id, purchaseFrequencyDays = 7, averageDeliveryDays = 2, safetyMarginDays = 2,
    )

    private fun facts(
        conferences: List<PurchaseConferenceRow>,
        conferenceItems: List<PurchaseConferenceItemRow>,
        entries: List<PurchaseEntryRow>,
        entryItems: List<PurchaseEntryItemRow>,
    ) = PurchaseFacts(
        products = listOf(
            PurchaseProductRow(p1, "Farinha", "KG", null, 1),
            PurchaseProductRow(p2, "Açúcar", "KG", null, 2),
        ),
        stock = listOf(PurchaseStockRow(p1, 16.0, s1), PurchaseStockRow(p2, 10.0, s1)),
        suppliers = listOf(supplier(s1), supplier(s2)),
        conferences = conferences,
        conferenceItems = conferenceItems,
        entries = entries,
        entryItems = entryItems,
    )

    private class Tables(maxRows: Int?, failEntryItemsOn: Int? = null, source: PurchasesFactsTest) {
        val conferences = FakeKeysetTable(source.conferences.shuffled(java.util.Random(7)), PurchaseConferenceRow::id, maxRows)
        val conferenceItems = FakeKeysetTable(source.conferenceItems.shuffled(java.util.Random(7)), PurchaseConferenceItemRow::id, maxRows)
        val entries = FakeKeysetTable(source.entries.shuffled(java.util.Random(7)), PurchaseEntryRow::id, maxRows)
        val entryItems = FakeKeysetTable(source.entryItems.shuffled(java.util.Random(7)), PurchaseEntryItemRow::id, maxRows, failEntryItemsOn)
    }

    private fun readProducts(maxRows: Int?): Pair<List<PurchaseProduct>, Tables> {
        val tables = Tables(maxRows, source = this)
        val products = assemblePurchaseProducts(
            facts(
                tables.conferences.readAll(),
                tables.conferenceItems.readAll(),
                tables.entries.readAll(),
                tables.entryItems.readAll(),
            ),
            nowMillis = now,
            weekday = 7,
        )
        return products to tables
    }

    @Test
    fun recordOnlyOnLaterPageChangesUsageAndHistoricalSuppliers() {
        val (products, tables) = readProducts(maxRows = 2)
        val flour = products.first { it.productId == p1 }

        assertEquals(UsageStatus.Ready, flour.usageInsights!!.status)
        assertEquals(3, flour.usageInsights!!.validIntervals)
        assertEquals(2.4, flour.usageInsights!!.dailyAverage!!, 1e-9)
        assertEquals(setOf(s2), flour.historicalSupplierIds)

        assertEquals(listOf(2, 1, 0), tables.entryItems.returned)
        assertEquals(listOf(2, 2, 2, 0), tables.conferenceItems.returned)
    }

    @Test
    fun firstPageAloneWouldGiveADifferentResult() {
        // Demonstra o risco corrigido: só a 1ª página (2 linhas) não basta para o cálculo.
        val truncated = assemblePurchaseProducts(
            facts(conferences.take(2), conferenceItems.take(2), entries.take(2), entryItems.take(2)),
            nowMillis = now,
            weekday = 7,
        ).first { it.productId == p1 }
        val (complete, _) = readProducts(maxRows = 2)
        val flour = complete.first { it.productId == p1 }

        assertNotEquals(flour.usageInsights!!.status, truncated.usageInsights!!.status)
        assertNotEquals(flour.historicalSupplierIds, truncated.historicalSupplierIds)
    }

    @Test
    fun smallBaseSinglePageGivesSameResultAsPagedRead() {
        val (singlePage, single) = readProducts(maxRows = null)
        val (paged, _) = readProducts(maxRows = 2)
        assertEquals(singlePage, paged)
        assertEquals(listOf(3, 0), single.entryItems.returned)
    }

    @Test
    fun supplierSeenOnlyOnLaterPageListsProductAsOtherSupplier() {
        val (products, _) = readProducts(maxRows = 2)
        val forS2 = supplierPurchaseProducts(products, s2)
        assertEquals(listOf(p1), forS2.map { it.productId })
        // Fornecedor atual é S1: regra comercial preservada.
        assertEquals(PurchaseProjectionStatus.OtherSupplier, forS2.single().projection.status)
    }

    @Test
    fun errorOnIntermediatePageFailsTheWholeRead() {
        val tables = Tables(maxRows = 2, failEntryItemsOn = 2, source = this)
        try {
            tables.entryItems.readAll()
            fail("deveria falhar")
        } catch (error: IllegalStateException) {
            assertEquals("falha simulada", error.message)
        }
    }
}

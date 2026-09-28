package com.babycatbe.nevesestoque.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Paridade com src/shared/lib/keysetPagination.test.ts (Web). */
class KeysetPaginationTest {

    private data class Row(val id: String, val value: Int)

    // Criadas fora de ordem de propósito: a leitura deve devolver em ordem de id.
    private fun rows(count: Int) = (1..count).map { Row(testUuid(1, it), it) }.reversed()

    private fun table(count: Int, maxRows: Int? = null, failOn: Int? = null) =
        FakeKeysetTable(rows(count), Row::id, maxRows, failOn)

    private fun sortedIds(count: Int) = rows(count).map { it.id }.sorted()

    @Test
    fun zeroRowsMakesSingleRequest() {
        val table = table(0)
        assertEquals(emptyList<Row>(), table.readAll(pageSize = 3))
        assertEquals(listOf(0), table.returned)
    }

    @Test
    fun lessThanOnePageStillConfirmsEndWithEmptyPage() {
        val table = table(2)
        assertEquals(sortedIds(2), table.readAll(pageSize = 3).map { it.id })
        assertEquals(listOf(2, 0), table.returned)
    }

    @Test
    fun exactlyOnePage() {
        val table = table(3)
        assertEquals(sortedIds(3), table.readAll(pageSize = 3).map { it.id })
        assertEquals(listOf(3, 0), table.returned)
    }

    @Test
    fun moreThanOnePage() {
        val table = table(4)
        assertEquals(sortedIds(4), table.readAll(pageSize = 3).map { it.id })
        assertEquals(listOf(3, 1, 0), table.returned)
    }

    @Test
    fun threeFullPagesWithoutLossOrDuplicatesInStableOrder() {
        val table = table(9)
        val ids = table.readAll(pageSize = 3).map { it.id }
        assertEquals(sortedIds(9), ids)
        assertEquals(9, ids.toSet().size)
        assertEquals(listOf(3, 3, 3, 0), table.returned)
        // Cada página continua exatamente de onde a anterior parou.
        assertEquals(listOf(null, ids[2], ids[5], ids[8]), table.afterIds)
    }

    @Test
    fun serverReturningLessThanRequestedKeepsReadingUntilEmptyPage() {
        // Pedido de 500, servidor corta em 2 (teto do servidor menor que o pedido).
        val table = table(7, maxRows = 2)
        assertEquals(sortedIds(7), table.readAll(pageSize = 500).map { it.id })
        assertEquals(listOf(2, 2, 2, 1, 0), table.returned)
    }

    @Test
    fun readIsDeterministic() {
        val first = table(8).readAll(pageSize = 3)
        val second = FakeKeysetTable(rows(8).reversed(), Row::id).readAll(pageSize = 3)
        assertEquals(first, second)
    }

    @Test
    fun errorOnIntermediatePageFailsWholeRead() {
        val table = table(9, failOn = 2)
        try {
            table.readAll(pageSize = 3)
            fail("deveria falhar")
        } catch (error: IllegalStateException) {
            assertEquals("falha simulada", error.message)
        }
        assertEquals(2, table.returned.size)
    }

    @Test
    fun outOfOrderServerFailsInsteadOfRiskingLossOrDuplicates() {
        try {
            runSuspend {
                fetchAllByIdKeyset(Row::id, pageSize = 3) { afterId, _ ->
                    if (afterId == null) listOf(Row(testUuid(1, 2), 2), Row(testUuid(1, 1), 1)) else emptyList()
                }
            }
            fail("deveria falhar")
        } catch (error: IncompletePaginationException) {
            assertTrue(error.message!!.contains("ordem"))
        }
    }

    @Test
    fun serverIgnoringCursorFailsInsteadOfRepeatingRows() {
        try {
            runSuspend { fetchAllByIdKeyset(Row::id, pageSize = 3) { _, _ -> listOf(Row(testUuid(1, 1), 1)) } }
            fail("deveria falhar")
        } catch (_: IncompletePaginationException) {
        }
    }

    @Test
    fun pageLimitFailsInsteadOfReturningIncompleteHistory() {
        try {
            table(10).readAll(pageSize = 2, maxPages = 3)
            fail("deveria falhar")
        } catch (_: IncompletePaginationException) {
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPageSize() {
        table(1).readAll(pageSize = 0)
    }
}

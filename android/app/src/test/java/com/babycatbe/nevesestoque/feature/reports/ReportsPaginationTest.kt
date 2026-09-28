package com.babycatbe.nevesestoque.feature.reports

import com.babycatbe.nevesestoque.data.supabase.FakeKeysetTable
import com.babycatbe.nevesestoque.data.supabase.IncompletePaginationException
import com.babycatbe.nevesestoque.data.supabase.fetchAllByKeyset
import com.babycatbe.nevesestoque.data.supabase.runSuspend
import com.babycatbe.nevesestoque.data.supabase.testUuid
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * 11.4 — Relatórios não podem parar na primeira página curta devolvida pelo teto
 * `max-rows` do servidor. Paridade com src/features/reports/api/reports.test.ts (Web).
 */
class ReportsPaginationTest {

    private fun p(i: Int) = testUuid(10, i)
    private fun e(i: Int) = testUuid(20, i)
    private fun c(i: Int) = testUuid(30, i)

    private val products = (1..5).map {
        ReportProductRow(p(it), "Produto $it", "kg", "2026-01-0${it}T10:00:00Z")
    }.reversed()
    private val entries = (1..4).map { ReportHeaderRow(e(it), "2026-02-0${it}T12:00:00Z", "2026-02-0${it}T12:00:00Z") }
    private val entryItems = (1..5).map {
        ReportEntryItemRow(testUuid(21, it), e(minOf(it, 4)), p(it), it.toDouble(), it * 2.0, 0)
    }
    private val conferences = (1..3).map { ReportHeaderRow(c(it), "2026-02-2${it}T12:00:00Z", "2026-02-2${it}T12:00:00Z") }
    private val conferenceItems = listOf(
        ReportConferenceItemRow(testUuid(31, 1), c(3), p(1), 7.0),
        ReportConferenceItemRow(testUuid(31, 2), c(1), p(2), 3.0),
        ReportConferenceItemRow(testUuid(31, 3), c(2), p(3), 4.0),
        ReportConferenceItemRow(testUuid(31, 4), c(2), p(4), 5.0),
        ReportConferenceItemRow(testUuid(31, 5), c(1), p(5), 6.0),
    )
    private val audits = listOf(
        ReportProductAuditRow(6, p(5), "SOFT_DELETE", "2026-03-06T00:00:00Z"),
        ReportProductAuditRow(1, p(5), "SOFT_DELETE", "2026-03-01T00:00:00Z"),
        ReportProductAuditRow(2, p(1), "UPDATE", "2026-03-02T00:00:00Z"),
        ReportProductAuditRow(3, p(2), "PRODUCT_MERGE", "2026-03-03T00:00:00Z"),
        ReportProductAuditRow(4, p(5), "RESTORE", "2026-03-04T00:00:00Z"),
        ReportProductAuditRow(12, p(4), "RESTORE", "2026-03-12T00:00:00Z"),
    )

    /** Tabela numérica falsa (audit_log): cursor `id > afterKey`, ordem e teto de linhas. */
    private class FakeLongTable(rows: List<ReportProductAuditRow>, private val maxRows: Int?, private val failOn: Int? = null) {
        private val sorted = rows.sortedBy { it.id }
        val returned = mutableListOf<Int>()
        fun page(afterKey: Long?, limit: Long): List<ReportProductAuditRow> {
            if (failOn == returned.size + 1) {
                returned += 0
                throw IllegalStateException("falha simulada")
            }
            var page = sorted.filter { afterKey == null || it.id > afterKey }.take(limit.toInt())
            if (maxRows != null) page = page.take(maxRows)
            returned += page.size
            return page
        }
    }

    private fun readFacts(maxRows: Int?): MonthlyStockReportFacts {
        fun <T> read(rows: List<T>, idOf: (T) -> String) = FakeKeysetTable(rows, idOf, maxRows).readAll()
        val auditTable = FakeLongTable(audits, maxRows)
        return assembleReportFacts(
            read(products, ReportProductRow::id),
            read(entries, ReportHeaderRow::id),
            read(entryItems, ReportEntryItemRow::id),
            read(conferences, ReportHeaderRow::id),
            read(conferenceItems, ReportConferenceItemRow::id),
            runSuspend { fetchAllByKeyset(ReportProductAuditRow::id) { key, limit -> auditTable.page(key, limit) } },
        )
    }

    @Test
    fun cappedServerReadsEveryPageOfEveryTable() {
        val reference = readFacts(maxRows = null)
        val capped = readFacts(maxRows = 2)
        assertEquals(reference, capped)
        assertEquals(5, capped.products.size)
        assertEquals(5, capped.entries.size)
        assertEquals(5, capped.conferences.size)
        assertEquals(listOf(3L), capped.merges.map { it.id })
        assertEquals(listOf(1L, 4L, 6L, 12L), capped.lifecycle.map { it.id })
    }

    @Test
    fun productsKeepIdOrder() {
        assertEquals((1..5).map { p(it) }, readFacts(maxRows = 1).products.map { it.id })
    }

    @Test
    fun factOnlyOnLaterPageIsIncluded() {
        val facts = readFacts(maxRows = 1)
        assertEquals(6.0, facts.conferences.single { it.productId == p(5) }.quantity, 0.0)
        assertEquals(LifecycleAction.Restore, facts.lifecycle.last().action)
    }

    @Test
    fun numericCursorEndsOnlyOnEmptyPage() {
        val table = FakeLongTable(audits, maxRows = 2)
        val rows = runSuspend { fetchAllByKeyset(ReportProductAuditRow::id, pageSize = 500) { key, limit -> table.page(key, limit) } }
        assertEquals(listOf(1L, 2L, 3L, 4L, 6L, 12L), rows.map { it.id })
        assertEquals(listOf(2, 2, 2, 0), table.returned)
    }

    @Test
    fun intermediateFailureFailsWholeRead() {
        val table = FakeLongTable(audits, maxRows = 1, failOn = 3)
        try {
            runSuspend { fetchAllByKeyset(ReportProductAuditRow::id) { key, limit -> table.page(key, limit) } }
            fail("deveria falhar")
        } catch (error: IllegalStateException) {
            assertEquals("falha simulada", error.message)
        }
    }

    @Test
    fun numericCursorRejectsOutOfOrderRows() {
        try {
            runSuspend {
                fetchAllByKeyset<ReportProductAuditRow, Long>({ it.id }) { key, _ ->
                    if (key == null) audits.take(2) else emptyList()
                }
            }
            fail("deveria falhar")
        } catch (_: IncompletePaginationException) {
        }
    }
}

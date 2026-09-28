package com.babycatbe.nevesestoque.feature.offline

import com.babycatbe.nevesestoque.data.offline.OfflineStore
import com.babycatbe.nevesestoque.feature.conferences.CategoryConferenceWriteInput
import com.babycatbe.nevesestoque.feature.conferences.CategoryConferenceWriteItem
import com.babycatbe.nevesestoque.feature.entries.EntryCreateInput
import com.babycatbe.nevesestoque.feature.entries.EntryCreateItem
import com.babycatbe.nevesestoque.feature.entries.EntryDraftProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PendingOperationsTest {
    private val metadata = PendingMetadata("auth-1", "app-1", "device-1", "Elias")
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun entryOperation(): PendingOperation = buildPendingEntry(
        input = EntryCreateInput(
            supplierId = "s1",
            newSupplier = null,
            effectiveAt = "2026-09-28T10:00:00-03:00",
            idempotencyKey = "idem-1",
            observation = "Nota 123",
            items = listOf(
                EntryCreateItem(productId = "p1", quantity = 2.0, unitPrice = 10.0),
                EntryCreateItem(newProduct = EntryDraftProduct("c1", "Fermento", "PCT", null), quantity = 1.0, unitPrice = null),
            ),
        ),
        supplierLabel = "Moinho",
        productLabel = { item -> (if (item.productId == "p1") "Farinha" else "?") to "SC" },
        metadata = metadata,
        localId = "local-1",
        now = "2026-09-28T10:01:00-03:00",
    )

    @Test
    fun entryPendingKeepsPayloadAndIdempotencyThroughSerialization() {
        val operation = entryOperation()
        val json = OfflineStore.json.encodeToString(PendingOperation.serializer(), operation)
        val restored = OfflineStore.json.decodeFromString(PendingOperation.serializer(), json)
        assertEquals(operation, restored)
        assertEquals("Moinho", restored.summaryTitle)
        assertEquals("2 itens · Nota 123", restored.summarySubtitle)

        val input = restored.toEntryCreateInput()
        assertEquals("idem-1", input.idempotencyKey)
        assertEquals("s1", input.supplierId)
        assertEquals("p1", input.items[0].productId)
        assertEquals("Fermento", input.items[1].newProduct?.name)
        assertEquals("2026-09-28T12:00:00Z", restored.toEntryCreateInput("2026-09-28T12:00:00Z").effectiveAt)
    }

    @Test
    fun categoryConferenceConversionUsesCurrentDevice() {
        val operation = buildPendingCategoryConference(
            input = CategoryConferenceWriteInput(
                "cat1", "2026-09-28T09:00:00-03:00", "Eychila", "device-old", "idem-2", null,
                listOf(CategoryConferenceWriteItem("p1", 0.0), CategoryConferenceWriteItem("p2", 3.5)),
            ),
            categoryLabel = "Laticínios",
            productLabel = { id -> (if (id == "p1") "Leite" else "Queijo") to "UN" },
            metadata = metadata,
        )
        val input = operation.toCategoryConferenceInput("device-now")
        assertEquals("device-now", input.deviceId)
        assertEquals("idem-2", input.idempotencyKey)
        assertEquals(listOf(0.0, 3.5), input.items.map { it.quantity })
        assertEquals("Responsável físico: Eychila", operation.summarySubtitle)
    }

    @Test
    fun editPreservesIdentityAndValidatesFields() {
        val operation = entryOperation()
        val draft = initialEditDraft(operation)
        assertEquals("2026-09-28", draft.date)
        assertEquals("2", draft.quantities["0"])
        assertEquals("10", draft.prices["0"])
        assertEquals("", draft.prices["1"])

        val edited = applyPendingEdit(
            operation,
            draft.copy(date = "2026-09-27", quantities = draft.quantities + ("0" to "3,5"), prices = draft.prices + ("1" to "0"), removedItems = emptySet()),
            now = "2026-09-28T11:00:00-03:00",
        ).operation!!
        assertEquals("local-1", edited.localId)
        assertEquals("idem-1", edited.idempotencyKey)
        assertEquals(3.5, edited.entry!!.items[0].quantity, 0.0)
        assertEquals(0.0, edited.entry!!.items[1].unitPrice!!, 0.0)
        assertTrue(edited.entry!!.effectiveAt.startsWith("2026-09-27"))

        val invalid = applyPendingEdit(operation, draft.copy(quantities = draft.quantities + ("0" to "0"), removedItems = setOf(1)))
        assertNull(invalid.operation)
        assertTrue(invalid.errors.any { it.contains("maior que zero") })

        val allRemoved = applyPendingEdit(operation, draft.copy(removedItems = setOf(0, 1)))
        assertTrue(allRemoved.errors.any { it.contains("pelo menos um item") })

        val future = applyPendingEdit(operation, draft.copy(date = "2999-01-01"))
        assertTrue(future.errors.contains("A data não pode ser futura."))
    }

    @Test
    fun conflictPositionsCoverBeforeBetweenAndAfter() {
        val conflicts = listOf(
            EntryConferenceConflict("c1", "2026-09-28T09:00:00-03:00", "A", emptyList()),
            EntryConferenceConflict("c2", "2026-09-28T15:00:00-03:00", "B", emptyList()),
        )
        val positions = buildEntryConflictPositions(conflicts, "2026-09-28T10:00:00-03:00", zone)
        assertEquals(listOf("before-first", "between-0", "after-last"), positions.map { it.id })
        assertEquals("Antes de todas", positions[0].label)
        assertEquals("Entre 09:00 e 15:00", positions[1].label)
        assertEquals("2026-09-28T15:00:00Z", positions[1].effectiveAt)

        val single = buildEntryConflictPositions(conflicts.take(1), "2026-09-28T10:00:00-03:00", zone)
        assertEquals(listOf("Antes da Conferência", "Depois da Conferência"), single.map { it.label })
        assertTrue(buildEntryConflictPositions(emptyList(), "2026-09-28T10:00:00-03:00", zone).isEmpty())
    }
}

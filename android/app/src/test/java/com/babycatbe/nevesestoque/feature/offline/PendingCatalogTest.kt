package com.babycatbe.nevesestoque.feature.offline

import com.babycatbe.nevesestoque.feature.conferences.CategoryConferenceWriteInput
import com.babycatbe.nevesestoque.feature.conferences.CategoryConferenceWriteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCatalogTest {
    private val catalog = listOf(
        PendingCatalogProduct("p1", "DaVaca 200g", "UN"),
        PendingCatalogProduct("p2", "Requeijão", "UN"),
        PendingCatalogProduct("p11", "Delicia 250g", "UN"),
    )

    private fun oldPending(): PendingOperation = buildPendingCategoryConference(
        input = CategoryConferenceWriteInput(
            categoryId = "cat1",
            effectiveAt = "2026-10-25T18:00:00-03:00",
            physicalResponsible = "Responsável",
            deviceId = "device1",
            idempotencyKey = "idem-123",
            observation = "Contagem física",
            items = listOf(
                CategoryConferenceWriteItem("p1", 20.0),
                CategoryConferenceWriteItem("p2", 0.0),
            ),
        ),
        categoryLabel = "MANTEIGAS, MARGARINAS E REQUEIJOES",
        productLabel = { if (it == "p1") "DaVaca 200g" to "UN" else "Requeijão" to "UN" },
        metadata = PendingMetadata("auth", "app", "device1", "Operador"),
        localId = "local123",
        now = "2026-10-25T18:01:00-03:00",
    )

    @Test fun catalogDifferenceFindsNewItemEvenWhenOrderChanges() {
        val items = oldPending().categoryConference!!.items
        val diff = comparePendingCategoryCatalog(items, catalog.reversed())
        assertEquals(2, diff.receivedCount)
        assertEquals(3, diff.expectedCount)
        assertEquals(listOf("Delicia 250g"), diff.missing.map { it.name })
        assertFalse(diff.matches)
        assertTrue(diff.userMessage().contains("Delicia 250g"))
    }

    @Test fun missingQuantityDoesNotAssumeZeroOrModifyPending() {
        val pending = oldPending()
        val result = applyPendingEdit(
            pending,
            initialEditDraft(pending),
            activeCatalog = catalog,
        )
        assertNull(result.operation)
        assertTrue(result.errors.any { it.contains("Delicia") })
        assertEquals(2, pending.categoryConference!!.items.size)
    }

    @Test fun explicitlyEnteredZeroCompletesSamePendingWithoutChangingOriginalItems() {
        val pending = oldPending()
        val draft = initialEditDraft(pending).let {
            it.copy(quantities = it.quantities + ("p11" to "0"))
        }
        val result = applyPendingEdit(
            pending, draft,
            now = "2026-10-26T19:00:00-03:00",
            activeCatalog = catalog,
        )
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val updated = result.operation!!
        assertEquals(pending.localId, updated.localId)
        assertEquals(pending.idempotencyKey, updated.idempotencyKey)
        assertEquals(pending.createdAt, updated.createdAt)
        assertEquals(\n            java.time.OffsetDateTime.parse(pending.categoryConference!!.effectiveAt).toInstant(),\n            java.time.OffsetDateTime.parse(updated.categoryConference!!.effectiveAt).toInstant(),\n        )
        assertEquals(pending.categoryConference!!.physicalResponsible, updated.categoryConference!!.physicalResponsible)
        assertEquals(pending.categoryConference!!.observation, updated.categoryConference!!.observation)
        assertEquals(listOf(20.0, 0.0, 0.0), updated.categoryConference!!.items.map { it.quantity })
        assertEquals(listOf("p1", "p2", "p11"), updated.categoryConference!!.items.map { it.productId })
        assertTrue(comparePendingCategoryCatalog(updated.categoryConference!!.items, catalog).matches)
    }

    @Test fun invalidNewQuantityIsRejectedAndPreviousCountsStayUntouched() {
        val pending = oldPending()
        val result = applyPendingEdit(
            pending,
            initialEditDraft(pending).copy(quantities = initialEditDraft(pending).quantities + ("p11" to "-2")),
            activeCatalog = catalog,
        )
        assertNull(result.operation)
        assertTrue(result.errors.isNotEmpty())
        assertEquals(20.0, pending.categoryConference!!.items[0].quantity, 0.0)
    }

    @Test fun deletedMovedOrDuplicateItemBlocksAutomaticRepair() {
        val pending = oldPending()
        val moved = catalog.filterNot { it.id == "p1" }
        val movedDiff = comparePendingCategoryCatalog(pending.categoryConference!!.items, moved)
        assertFalse(movedDiff.matches)
        assertEquals(listOf("p1"), movedDiff.inactiveOrMoved.map { it.productId })
        val movedResult = applyPendingEdit(pending, initialEditDraft(pending), activeCatalog = moved)
        assertNull(movedResult.operation)
        assertTrue(movedResult.errors.any { it.contains("DaVaca") })

        val duplicated = pending.categoryConference!!.items + pending.categoryConference!!.items[0]
        val repeated = comparePendingCategoryCatalog(duplicated, catalog)
        assertEquals(listOf("p1"), repeated.repeatedIds)
        assertFalse(repeated.matches)
    }

    @Test fun fullCatalogMatchesWithAllZeroCountsAndDifferentOrder() {
        val items = listOf(
            PendingConferenceItem("p11", "Delicia 250g", "UN", 0.0),
            PendingConferenceItem("p2", "Requeijão", "UN", 0.0),
            PendingConferenceItem("p1", "DaVaca 200g", "UN", 0.0),
        )
        assertTrue(comparePendingCategoryCatalog(items, catalog).matches)
    }
}

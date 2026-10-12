package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.feature.offline.PendingCategoryConferencePayload
import com.babycatbe.nevesestoque.feature.offline.PendingConferenceItem
import com.babycatbe.nevesestoque.feature.offline.PendingKind
import com.babycatbe.nevesestoque.feature.offline.PendingOperation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConferenceBackgroundSavesTest {
    private val input = CategoryConferenceWriteInput(
        categoryId = "c1",
        effectiveAt = "2026-10-12T10:00:00-03:00",
        physicalResponsible = "Responsável",
        deviceId = "device1",
        idempotencyKey = "key1",
        observation = "Observação",
        items = listOf(CategoryConferenceWriteItem("p1", 2.0)),
    )

    private val pending = PendingOperation(
        localId = "local1",
        kind = PendingKind.CategoryConference,
        createdAt = "2026-10-12T10:00:00-03:00",
        updatedAt = "2026-10-12T10:00:00-03:00",
        deviceId = "device1",
        actorLabel = "Responsável",
        idempotencyKey = "key1",
        categoryConference = PendingCategoryConferencePayload(
            categoryId = "c1",
            categoryLabel = "Categoria",
            effectiveAt = input.effectiveAt,
            physicalResponsible = "Responsável",
            observation = "Observação",
            items = listOf(PendingConferenceItem("p1", "Produto", "UN", 2.0)),
        ),
    )

    @Test
    fun matchingCopyAllowsBackgroundChecks() {
        assertTrue(conferencePendingMatches(input, "local1", pending))
    }

    @Test
    fun missingOrModifiedCopiesCannotBeSentWithStaleValues() {
        assertFalse(conferencePendingMatches(input, "local1", null))
        assertFalse(conferencePendingMatches(input, "different", pending))
        assertFalse(conferencePendingMatches(input, "local1", pending.copy(idempotencyKey = "other")))
        assertFalse(conferencePendingMatches(input, "local1", pending.copy(
            categoryConference = pending.categoryConference!!.copy(
                items = listOf(PendingConferenceItem("p1", "Produto", "UN", 3.0))
            )
        )))
    }
}

package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.feature.offline.PendingKind
import com.babycatbe.nevesestoque.feature.offline.PendingMetadata
import com.babycatbe.nevesestoque.feature.offline.buildPendingCategoryConferenceCorrection
import com.babycatbe.nevesestoque.feature.offline.toCategoryConferenceUpdateInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConferenceCorrectionPendingTest {
    private val update = CategoryConferenceUpdateInput(
        conferenceId = "conference-1",
        effectiveAt = "2026-10-12T14:00:00-03:00",
        physicalResponsible = "Responsável",
        deviceId = "device-1",
        observation = "Correção",
        items = listOf(CategoryConferenceWriteItem("product-1", 2.0)),
    )

    private fun pending() = buildPendingCategoryConferenceCorrection(
        input = update, categoryId = "category-1", categoryLabel = "Frios",
        productLabel = { "Produto" to "UN" },
        metadata = PendingMetadata(null, "user-1", "device-1", "Responsável"),
        localId = "local-1",
        now = "2026-10-12T14:01:00-03:00",
    )

    @Test fun correctionIsNotANewCategoryConference() {
        val operation = pending()
        assertEquals(PendingKind.CategoryConferenceCorrection, operation.kind)
        assertEquals("conference-1", operation.categoryConference?.conferenceId)
        assertEquals("category-1", operation.categoryConference?.categoryId)
        assertEquals(update, operation.toCategoryConferenceUpdateInput("device-1"))
        assertTrue(conferenceCorrectionPendingMatches(update, "local-1", operation))
    }

    @Test fun changedLocalCorrectionBlocksStaleBackgroundUpdate() {
        val operation = pending()
        assertFalse(conferenceCorrectionPendingMatches(update, "local-1", null))
        assertFalse(conferenceCorrectionPendingMatches(update, "other", operation))
        assertFalse(conferenceCorrectionPendingMatches(
            update, "local-1",
            operation.copy(categoryConference = operation.categoryConference!!.copy(
                items = operation.categoryConference!!.items.map { it.copy(quantity = 3.0) }
            )),
        ))
        assertFalse(conferenceCorrectionPendingMatches(
            update, "local-1",
            operation.copy(kind = PendingKind.CategoryConference),
        ))
    }
}

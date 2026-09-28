package com.babycatbe.nevesestoque.feature.conferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ConferenceModuleTest {
    private val products = listOf(
        ConferenceProduct("p1", "Farinha", "SC", 1),
        ConferenceProduct("p2", "Açúcar", "SC", 2),
    )

    @Test
    fun categoryDraftRequiresEveryQuantityAndAcceptsZero() {
        val result = validateCategoryConferenceDraft(
            products = products,
            date = "2026-09-28",
            responsible = " Elias ",
            observation = "",
            quantities = mapOf("p1" to "0", "p2" to ""),
            effectiveAtBuilder = { "2026-09-28T10:00:00-03:00" },
        )

        assertTrue(result.errors.hasErrors)
        assertNull(result.errors.quantities["p1"])
        assertNotNull(result.errors.quantities["p2"])
    }

    @Test
    fun categoryDraftNormalizesResponsibleAndCommaDecimals() {
        val result = validateCategoryConferenceDraft(
            products = products,
            date = "2026-09-28",
            responsible = "  Elias   Junior ",
            observation = " ok ",
            quantities = mapOf("p1" to "2,5", "p2" to "0"),
            effectiveAtBuilder = { "2026-09-28T10:00:00-03:00" },
        )

        assertFalse(result.errors.hasErrors)
        assertEquals("Elias Junior", result.responsible)
        assertEquals("ok", result.observation)
        assertEquals(2.5, result.items[0].quantity, 0.0)
        assertEquals(0.0, result.items[1].quantity, 0.0)
    }

    @Test
    fun conferenceDateRejectsFutureDate() {
        val today = LocalDate.of(2026, 9, 28)
        assertNull(categoryConferenceDateError("2026-09-28", today))
        assertEquals("A data não pode ser futura.", categoryConferenceDateError("2026-09-29", today))
    }

    @Test
    fun effectiveAtKeepsChosenLocalDate() {
        val zone = ZoneId.of("America/Bahia")
        val effective = buildCategoryConferenceEffectiveAt(
            dateValue = "2026-09-27",
            referenceInstant = Instant.parse("2026-09-28T14:30:45Z"),
            zoneId = zone,
        )
        assertEquals("2026-09-27", conferenceLocalDate(effective, zone))
    }

    @Test
    fun updateMatchRequiresExactItems() {
        val details = ConferenceDetails(
            id = "c",
            categoryId = "cat",
            categoryName = "Secos",
            effectiveAt = "2026-09-28T10:00:00-03:00",
            createdAt = "2026-09-28T13:00:00Z",
            physicalResponsible = "Elias",
            observation = "ok",
            items = listOf(
                ConferenceDetailItem("i1", "p1", "Farinha", "SC", 2.0, 1),
                ConferenceDetailItem("i2", "p2", "Açúcar", "SC", 3.0, 2),
            ),
        )
        val input = CategoryConferenceUpdateInput(
            conferenceId = "c",
            effectiveAt = "2026-09-28T13:00:00Z",
            physicalResponsible = "Elias",
            deviceId = "d",
            observation = "ok",
            items = listOf(
                CategoryConferenceWriteItem("p1", 2.0),
                CategoryConferenceWriteItem("p2", 3.0),
            ),
        )

        assertTrue(categoryConferenceUpdateMatches(details, input))
        assertFalse(
            categoryConferenceUpdateMatches(
                details,
                input.copy(items = input.items.reversed()),
            )
        )
    }

    @Test
    fun printHtmlContainsA4Structure() {
        val html = buildConferencePrintHtml(
            ConferencePrintData(
                categories = listOf(
                    ConferencePrintCategory(
                        id = "cat",
                        name = "Secos",
                        illustrationSource = "library",
                        illustrationKey = "panificacao",
                        products = products,
                    )
                ),
                pendingProductCount = 0,
            )
        )

        assertTrue(html.contains("A4 portrait"))
        assertTrue(html.contains("Secos"))
        assertTrue(html.contains("Farinha"))
        assertTrue(html.contains("PANIFICADORA", ignoreCase = true).not())
        assertTrue(html.contains("data:image/webp;base64,"))
    }
}

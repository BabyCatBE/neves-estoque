package com.babycatbe.nevesestoque.feature.conferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductConferenceTest {
    @Test
    fun quantityAcceptsCommaAndZero() {
        assertEquals(12.5, parseConferenceQuantity("12,5", "Nova quantidade").first!!, 0.0001)
        assertEquals(0.0, parseConferenceQuantity("0", "Nova quantidade").first!!, 0.0001)
        assertNull(parseConferenceQuantity("0", "Nova quantidade").second)
    }

    @Test
    fun quantityRejectsBlankAndInvalidValues() {
        assertEquals(
            "Nova quantidade é obrigatória.",
            parseConferenceQuantity("", "Nova quantidade").second,
        )
        assertEquals(
            "Nova quantidade inválida.",
            parseConferenceQuantity("-1", "Nova quantidade").second,
        )
        assertEquals(
            "Nova quantidade inválida.",
            parseConferenceQuantity("1,2,3", "Nova quantidade").second,
        )
    }

    @Test
    fun draftNormalizesResponsibleAndObservation() {
        val result = validateProductConferenceDraft(
            responsible = "  Elias   Junior  ",
            quantity = "10",
            observation = "  contagem conferida  ",
        )

        assertTrue(!result.errors.hasErrors)
        assertEquals("Elias Junior", result.responsible)
        assertEquals(10.0, result.quantity!!, 0.0001)
        assertEquals("contagem conferida", result.observation)
    }

    @Test
    fun draftRequiresResponsibleAndLimitsLength() {
        assertEquals(
            "Informe o responsável pela contagem física.",
            validateProductConferenceDraft("", "1", "").errors.responsible,
        )
        assertEquals(
            "O responsável pode ter no máximo 160 caracteres.",
            validateProductConferenceDraft("x".repeat(161), "1", "").errors.responsible,
        )
    }

    @Test
    fun consumptionWithinFiftyPercentDoesNotWarn() {
        val warning = evaluateConferenceConsumption(
            productId = "p1",
            expectedDailyAverage = 10.0,
            intervalDays = 10.0,
            previousQuantity = 150.0,
            entriesQuantity = 0.0,
            candidateQuantity = 60.0,
        )
        assertNull(warning)
    }

    @Test
    fun consumptionAtFiftyPercentAboveWarns() {
        val warning = evaluateConferenceConsumption(
            productId = "p1",
            expectedDailyAverage = 10.0,
            intervalDays = 10.0,
            previousQuantity = 200.0,
            entriesQuantity = 0.0,
            candidateQuantity = 50.0,
        )
        assertEquals(ConferenceConsumptionWarningKind.Above, warning?.kind)
        assertEquals(50.0, warning?.differencePercent ?: 0.0, 0.0001)
    }

    @Test
    fun unexpectedStockIncreaseIsInconsistent() {
        val warning = evaluateConferenceConsumption(
            productId = "p1",
            expectedDailyAverage = 2.0,
            intervalDays = 5.0,
            previousQuantity = 10.0,
            entriesQuantity = 0.0,
            candidateQuantity = 20.0,
        )
        assertEquals(ConferenceConsumptionWarningKind.Inconsistent, warning?.kind)
        assertTrue((warning?.actualConsumption ?: 0.0) < 0)
    }
}

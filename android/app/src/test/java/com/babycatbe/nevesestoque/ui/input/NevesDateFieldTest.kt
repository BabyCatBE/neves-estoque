package com.babycatbe.nevesestoque.ui.input

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class NevesDateFieldTest {
    @Test
    fun isoDateIsDisplayedInBrazilianOrder() {
        assertEquals("29/09/2026", formatNevesDateForDisplay("2026-09-29"))
    }

    @Test
    fun invalidDateIsNotSilentlyRewritten() {
        assertEquals("data-invalida", formatNevesDateForDisplay("data-invalida"))
    }

    @Test
    fun pickerMillisRoundTripKeepsIsoDate() {
        val millis = LocalDate.of(2026, 9, 29)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        assertEquals("2026-09-29", nevesIsoDateFromPickerMillis(millis))
    }
}

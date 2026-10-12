package com.babycatbe.nevesestoque.feature.conferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConferenceFormFocusTest {

    @Test
    fun quantitiesAdvanceInScreenOrderAndLastClosesKeyboard() {
        val ids = listOf("a", "b", "c")
        assertEquals("b", nextConferenceProductId(ids, "a"))
        assertEquals("c", nextConferenceProductId(ids, "b"))
        // Último Produto: sem próximo, o fluxo segue para Observação.
        assertNull(nextConferenceProductId(ids, "c"))
        assertNull(nextConferenceProductId(ids, "x"))
    }

    @Test
    fun responsibleValidationOnAdvanceMatchesSaveRules() {
        assertEquals("Informe o responsável pela contagem física.", conferenceResponsibleError("   "))
        assertEquals(
            "O responsável pode ter no máximo 160 caracteres.",
            conferenceResponsibleError("a".repeat(161)),
        )
        assertNull(conferenceResponsibleError("  Maria   Neves "))
        assertEquals("Maria Neves", cleanConferenceResponsible("  Maria   Neves "))
    }
}

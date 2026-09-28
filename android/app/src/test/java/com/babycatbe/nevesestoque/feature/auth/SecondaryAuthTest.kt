package com.babycatbe.nevesestoque.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryAuthTest {
    @Test
    fun normalizesUsernameLikeWeb() {
        assertEquals("elias.jr", normalizeSecondaryUsername("  ÉLIAS.JR  "))
    }

    @Test
    fun validatesApprovedUsernameFormat() {
        assertTrue(isValidSecondaryUsername("teste_01"))
        assertFalse(isValidSecondaryUsername("ab"))
        assertFalse(isValidSecondaryUsername("nome com espaço"))
    }

    @Test
    fun mapsUsernameToInternalAuthEmail() {
        assertEquals("teste@usuarios.neves.invalid", secondaryAuthEmail(" Teste "))
    }
}

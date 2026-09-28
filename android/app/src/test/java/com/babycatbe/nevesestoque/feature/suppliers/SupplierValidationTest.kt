package com.babycatbe.nevesestoque.feature.suppliers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierValidationTest {
    @Test
    fun phoneFormattingMatchesWebRules() {
        assertEquals("(75", formatSupplierPhoneInput("75"))
        assertEquals("(75) 9", formatSupplierPhoneInput("759"))
        assertEquals("(75) 9 9999", formatSupplierPhoneInput("7599999"))
        assertEquals("(75) 9 9999-0000", formatSupplierPhoneInput("75999990000"))
        assertEquals("(75) 9 9999-0000", formatSupplierPhoneInput("759999900001234"))
    }

    @Test
    fun completePhoneRequiresExactlyElevenDigits() {
        assertTrue(isSupplierPhoneComplete("(75) 9 9999-0000"))
        assertFalse(isSupplierPhoneComplete("7599999000"))
        assertFalse(isSupplierPhoneComplete("759999900000"))
    }

    @Test
    fun optionalIntegerAcceptsEmptyAndValidLimits() {
        assertNull(parseOptionalSupplierInteger("", "Frequência", 1, 3650).value)
        assertEquals(14, parseOptionalSupplierInteger("14", "Frequência", 1, 3650).value)
        assertNotNull(parseOptionalSupplierInteger("0", "Frequência", 1, 3650).error)
        assertNotNull(parseOptionalSupplierInteger("1,5", "Frequência", 1, 3650).error)
    }

    @Test
    fun fullSupplierRequiresCompanyAndPhone() {
        val result = validateSupplierDraft(
            name = "João",
            company = "",
            phone = "",
            observation = "",
            purchaseFrequencyDays = "",
            preferredOrderWeekday = "",
            averageDeliveryDays = "",
            safetyMarginDays = "",
        )
        assertNull(result.input)
        assertNotNull(result.errors.company)
        assertNotNull(result.errors.phone)
    }

    @Test
    fun validDraftNormalizesPhoneAndOptionalObservation() {
        val result = validateSupplierDraft(
            name = "  João  ",
            company = "  Empresa X ",
            phone = "(75) 9 9999-0000",
            observation = "   ",
            purchaseFrequencyDays = "7",
            preferredOrderWeekday = "2",
            averageDeliveryDays = "0",
            safetyMarginDays = "3",
        )
        assertNotNull(result.input)
        assertEquals("João", result.input?.name)
        assertEquals("Empresa X", result.input?.company)
        assertEquals("75999990000", result.input?.phone)
        assertNull(result.input?.observation)
        assertEquals(0, result.input?.averageDeliveryDays)
    }

    @Test
    fun searchIgnoresAccentsCaseAndPhoneFormatting() {
        val supplier = SupplierDetails(
            id = "1",
            name = "João",
            company = "Açúcar Bahia",
            phone = "75999990000",
            observation = null,
            purchaseFrequencyDays = null,
            preferredOrderWeekday = null,
            averageDeliveryDays = null,
            safetyMarginDays = null,
            isPending = false,
        )
        assertTrue(matchesSupplierSearch(supplier, "JOAO"))
        assertTrue(matchesSupplierSearch(supplier, "acucar"))
        assertTrue(matchesSupplierSearch(supplier, "9999-0000"))
        assertFalse(matchesSupplierSearch(supplier, "farinha"))
    }

    @Test
    fun pendingStatusMatchesIncompleteQuickRegistration() {
        val complete = SupplierRow(
            id = "1",
            name = "João",
            company = "Empresa",
            phone = "75999990000",
        ).toDetails()
        val pending = SupplierRow(
            id = "2",
            name = "Maria",
            company = null,
            phone = null,
        ).toDetails()

        assertFalse(complete.isPending)
        assertTrue(pending.isPending)
    }

    @Test
    fun updateReconciliationRequiresExactPersistedState() {
        val supplier = SupplierDetails(
            id = "1",
            name = "João",
            company = "Empresa X",
            phone = "75999990000",
            observation = "Entrega cedo",
            purchaseFrequencyDays = 7,
            preferredOrderWeekday = 2,
            averageDeliveryDays = 1,
            safetyMarginDays = 2,
            isPending = false,
        )
        val input = SupplierMutationInput(
            name = "João",
            company = "Empresa X",
            phone = "75999990000",
            observation = "Entrega cedo",
            purchaseFrequencyDays = 7,
            preferredOrderWeekday = 2,
            averageDeliveryDays = 1,
            safetyMarginDays = 2,
        )

        assertTrue(supplierMatchesInput(supplier, input))
        assertFalse(supplierMatchesInput(supplier, input.copy(safetyMarginDays = 3)))
    }

    @Test
    fun weekdayLabelsMatchOperationalConvention() {
        assertEquals("Segunda-feira", supplierWeekdayLabel(1))
        assertEquals("Domingo", supplierWeekdayLabel(7))
        assertEquals("Não informado", supplierWeekdayLabel(null))
    }
}

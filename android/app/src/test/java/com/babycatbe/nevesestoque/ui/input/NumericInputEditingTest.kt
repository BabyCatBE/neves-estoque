package com.babycatbe.nevesestoque.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumericInputEditingTest {

    @Test
    fun digitsAppendWithoutLeadingZero() {
        assertEquals("5", NumericInputEditing.appendDigit("", '5'))
        assertEquals("7", NumericInputEditing.appendDigit("0", '7'))
        assertEquals("0", NumericInputEditing.appendDigit("0", '0'))
        assertEquals("12", NumericInputEditing.appendDigit("1", '2'))
        assertEquals("0,05", NumericInputEditing.appendDigit("0,0", '5'))
    }

    @Test
    fun decimalSeparatorIsCommaAndOnlyOnce() {
        assertEquals("0,", NumericInputEditing.appendDecimalSeparator(""))
        assertEquals("3,", NumericInputEditing.appendDecimalSeparator("3"))
        assertEquals("3,5", NumericInputEditing.appendDecimalSeparator("3,5"))
        // Valor antigo digitado com ponto também bloqueia uma segunda separação.
        assertEquals("3.5", NumericInputEditing.appendDecimalSeparator("3.5"))
    }

    @Test
    fun respectsMaximumLength() {
        val full = "1".repeat(NumericInputEditing.MAX_LENGTH)
        assertEquals(full, NumericInputEditing.appendDigit(full, '9'))
        assertEquals(full, NumericInputEditing.appendDecimalSeparator(full))
    }

    @Test
    fun backspaceRemovesLastCharacter() {
        assertEquals("1,", NumericInputEditing.backspace("1,5"))
        assertEquals("", NumericInputEditing.backspace("7"))
        assertEquals("", NumericInputEditing.backspace(""))
    }

    @Test
    fun stepAddsAndSubtractsOneNeverBelowZero() {
        assertEquals("1", NumericInputEditing.step("", 1))
        assertEquals("0", NumericInputEditing.step("", -1))
        assertEquals("0", NumericInputEditing.step("0", -1))
        assertEquals("11", NumericInputEditing.step("10", 1))
        assertEquals("3,5", NumericInputEditing.step("2,5", 1))
        assertEquals("1,25", NumericInputEditing.step("2.25", -1))
        assertEquals("0", NumericInputEditing.step("0,4", -1))
        assertEquals("4", NumericInputEditing.step("3,", 1))
    }

    @Test
    fun stepKeepsInvalidTextUntouched() {
        assertNull(NumericInputEditing.step("abc", 1))
        assertNull(NumericInputEditing.step("1,2,3", -1))
    }

    @Test
    fun formatUsesCommaWithoutGrouping() {
        assertEquals("1234,5", NumericInputEditing.format(java.math.BigDecimal("1234.50")))
        assertEquals("100", NumericInputEditing.format(java.math.BigDecimal("1E+2")))
        assertEquals("0", NumericInputEditing.format(java.math.BigDecimal("0.000")))
    }
}

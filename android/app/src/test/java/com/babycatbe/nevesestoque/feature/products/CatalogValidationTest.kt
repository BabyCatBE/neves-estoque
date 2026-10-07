package com.babycatbe.nevesestoque.feature.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidationTest {
    @Test
    fun productNameIsTrimmedAndInternalSpacesAreCollapsed() {
        assertEquals("Farinha de trigo", normalizeProductName("  Farinha   de\ttrigo  "))
    }

    @Test
    fun emptyOptionalNumbersRemainNullAndZeroIsPreserved() {
        assertNull(parseOptionalNonNegativeDecimal("   ", "Preço inicial").value)
        assertEquals(0.0, parseOptionalNonNegativeDecimal("0", "Preço inicial").value!!, 0.0)
    }

    @Test
    fun decimalAcceptsCommaWithoutTurningInvalidInputIntoZero() {
        assertEquals(12.5, parseOptionalNonNegativeDecimal("12,5", "Preço inicial").value!!, 0.0)
        assertNull(parseOptionalNonNegativeDecimal("abc", "Preço inicial").value)
        assertEquals("Preço inicial inválido.", parseOptionalNonNegativeDecimal("abc", "Preço inicial").error)
    }

    @Test
    fun negativeInitialValuesAreRejected() {
        assertEquals(
            "Estoque inicial não pode ser negativo.",
            parseOptionalNonNegativeDecimal("-1", "Estoque inicial").error,
        )
    }

    @Test
    fun productDraftRequiresCategoryAndAllowedUnit() {
        val result = validateProductDraft("Produto", "", "OUTRA", "", "")
        assertTrue(result.errors.hasErrors)
        assertNull(result.input)
        assertEquals("Escolha a Categoria.", result.errors.category)
        assertEquals("Escolha a Unidade.", result.errors.unit)
    }

    @Test
    fun validProductDraftKeepsOptionalNulls() {
        val result = validateProductDraft(" Produto ", "category-id", "UN", "", "")
        assertFalse(result.errors.hasErrors)
        assertEquals("Produto", result.input?.name)
        assertNull(result.input?.initialStockQuantity)
        assertNull(result.input?.initialPrice)
    }

    @Test
    fun namesRespectCurrentBackendLimits() {
        assertEquals(
            "O nome do Produto pode ter no máximo 200 caracteres.",
            validateProductDraft("a".repeat(201), "category-id", "UN", "", "").errors.name,
        )
        assertEquals(
            "O nome da Categoria pode ter no máximo 120 caracteres.",
            validateCategoryName("a".repeat(121)),
        )
    }

    @Test
    fun duplicateAndUnknownBackendErrorsAreFriendly() {
        assertEquals(
            "Já existe um Produto ativo com esse nome.",
            productErrorMessage(IllegalStateException("duplicate key 23505")),
        )
        assertEquals(
            "Já existe uma Categoria ativa com esse nome.",
            categoryErrorMessage(IllegalStateException("duplicate key 23505")),
        )
        assertEquals(
            "Não foi possível salvar o Produto. Tente novamente.",
            productErrorMessage(IllegalStateException("PGRST999 schema internals")),
        )
    }

    private fun priceItem(unitPrice: Double?) = ProductPriceHistoryItem(
        id = "item-$unitPrice",
        entryId = "entry",
        effectiveAt = "2026-10-01T10:00:00-03:00",
        supplierName = "Fornecedor",
        quantity = 1.0,
        unitPrice = unitPrice,
        position = 1,
    )

    @Test
    fun initialPriceStaysEditableWithoutRealPricedEntry() {
        assertTrue(canEditInitialPrice(emptyList()))
        assertTrue(canEditInitialPrice(listOf(priceItem(null), priceItem(0.0))))
    }

    @Test
    fun initialPriceLocksAfterEntryWithRealPrice() {
        assertFalse(canEditInitialPrice(listOf(priceItem(0.0), priceItem(9.9))))
    }

    @Test
    fun decimalInputFormattingPreservesEmptyAndZero() {
        assertEquals("", formatDecimalInput(null))
        assertEquals("0", formatDecimalInput(0.0))
        assertEquals("10", formatDecimalInput(10.0))
        assertEquals("24,9", formatDecimalInput(24.9))
    }
}

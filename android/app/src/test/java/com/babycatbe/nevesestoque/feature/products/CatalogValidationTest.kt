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
}

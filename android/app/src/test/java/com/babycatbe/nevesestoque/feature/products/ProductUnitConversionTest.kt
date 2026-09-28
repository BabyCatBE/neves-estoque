package com.babycatbe.nevesestoque.feature.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductUnitConversionTest {
    @Test
    fun positiveQuantityAcceptsCommaDecimal() {
        val result = parsePositiveConversionQuantity("12,5", "Quantidade")
        assertEquals(12.5, result.value ?: 0.0, 0.0000001)
        assertNull(result.error)
    }

    @Test
    fun positiveQuantityRejectsZeroAndInvalidText() {
        assertEquals(
            "Quantidade deve ser maior que zero.",
            parsePositiveConversionQuantity("0", "Quantidade").error,
        )
        assertEquals(
            "Quantidade inválida.",
            parsePositiveConversionQuantity("abc", "Quantidade").error,
        )
    }

    @Test
    fun factorMatchesTwelveUnitsToOneBox() {
        assertEquals(
            1.0 / 12.0,
            calculateUnitConversionFactor(12.0, 1.0),
            0.0000000001,
        )
    }

    @Test
    fun quantitiesUseNewOverOldRatio() {
        val factor = calculateUnitConversionFactor(1.0, 100.0)
        assertEquals(250.0, convertQuantityForUnit(2.5, factor) ?: 0.0, 0.0000001)
    }

    @Test
    fun unitPricesUseInverseRatio() {
        val factor = calculateUnitConversionFactor(12.0, 1.0)
        assertEquals(120.0, convertPriceForUnit(10.0, factor) ?: 0.0, 0.0000001)
    }

    @Test
    fun quantityTimesPriceRemainsFinanciallyEquivalent() {
        val factor = calculateUnitConversionFactor(1.0, 10.0)
        val oldQuantity = 7.6
        val oldPrice = 10.0
        val newQuantity = convertQuantityForUnit(oldQuantity, factor) ?: 0.0
        val newPrice = convertPriceForUnit(oldPrice, factor) ?: 0.0

        assertEquals(oldQuantity * oldPrice, newQuantity * newPrice, 0.0000001)
        assertTrue(newQuantity > oldQuantity)
        assertTrue(newPrice < oldPrice)
    }

    @Test
    fun backendErrorsAreMappedWithoutLeakingInternals() {
        assertEquals(
            "Dispositivo não autorizado.",
            productUnitConversionErrorMessage(
                IllegalStateException("Dispositivo não autorizado.")
            ),
        )
        assertEquals(
            "Não foi possível alterar a unidade. Tente novamente.",
            productUnitConversionErrorMessage(
                IllegalStateException("internal database detail")
            ),
        )
    }
}

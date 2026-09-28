package com.babycatbe.nevesestoque.feature.entries

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EntryLogicTest {
    @Test
    fun quantityAcceptsCommaAndRejectsZero() {
        assertEquals(2.5, parseEntryPositiveDecimal("2,5", "Quantidade").value!!, 0.0)
        assertNotNull(parseEntryPositiveDecimal("0", "Quantidade").error)
    }

    @Test
    fun priceDistinguishesMissingFromBonus() {
        assertNull(parseEntryOptionalPrice("").value)
        assertEquals(0.0, parseEntryOptionalPrice("0").value!!, 0.0)
        assertNotNull(parseEntryOptionalPrice("-1").error)
    }

    @Test
    fun futureDateIsRejected() {
        val today = LocalDate.of(2026, 9, 28)
        assertNull(entryDateError("2026-09-28", today))
        assertEquals("A data não pode ser futura.", entryDateError("2026-09-29", today))
    }

    @Test
    fun effectiveAtKeepsChosenLocalDate() {
        val zone = ZoneId.of("America/Bahia")
        val instant = Instant.parse("2026-09-28T14:30:45Z")
        val effective = buildEntryEffectiveAt("2026-09-27", instant, zone)
        assertEquals("2026-09-27", entryLocalDate(effective, zone))
    }

    @Test
    fun quickSupplierNormalizesSpaces() {
        assertNull(validateQuickSupplierName("  João   Silva "))
        assertEquals("João Silva", normalizeQuickSupplierName("  João   Silva "))
    }

    @Test
    fun quickProductAllowsPendingCategory() {
        val result = validateQuickProduct(
            clientId = "abc",
            name = " Farinha ",
            unit = "kg",
            categoryId = null,
        )
        assertNull(result.error)
        assertEquals("Farinha", result.product?.name)
        assertEquals("KG", result.product?.unit)
        assertNull(result.product?.categoryId)
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        val supplier = EntrySupplierOption(
            id = "1",
            name = "João",
            company = "Açúcar Bahia",
            phone = "75999990000",
            isPending = false,
        )
        assertTrue(entrySupplierMatches(supplier, "ACUCAR"))
        assertTrue(entrySupplierMatches(supplier, "joao"))
        assertFalse(entrySupplierMatches(supplier, "farinha"))
    }

    @Test
    fun updateReconciliationRequiresExactOrderedState() {
        val details = EntryDetails(
            id = "e",
            effectiveAt = "2026-09-28T10:00:00-03:00",
            supplierId = "s",
            supplierName = "Fornecedor",
            supplierCompany = null,
            observation = "ok",
            totalKnown = 20.0,
            hasMissingPrice = false,
            items = listOf(
                EntryDetailItem("i1", "p1", "P1", "KG", 2.0, 10.0, 1)
            ),
        )
        val input = EntryUpdateInput(
            entryId = "e",
            supplierId = "s",
            effectiveAt = "2026-09-28T13:00:00Z",
            observation = "ok",
            items = listOf(EntryUpdateItem("p1", 2.0, 10.0)),
        )
        assertTrue(entryMatchesUpdate(details, input))
        assertFalse(entryMatchesUpdate(details, input.copy(
            items = listOf(EntryUpdateItem("p1", 3.0, 10.0))
        )))
    }
}

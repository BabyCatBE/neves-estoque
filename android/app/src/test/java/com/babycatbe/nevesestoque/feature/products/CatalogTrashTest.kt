package com.babycatbe.nevesestoque.feature.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class CatalogTrashTest {
    private val now = OffsetDateTime.parse("2026-09-28T10:00:00Z").toInstant().toEpochMilli()

    @Test
    fun restorableTrashKeepsOnlyOpenWindowAndNonPermanentItems() {
        val products = listOf(
            ProductTrashRow("p1", "Farinha", "SC", "2026-09-28T09:00:00Z", "2026-10-05T09:00:00Z"),
            ProductTrashRow("expired", "Expirado", "UN", "2026-09-20T09:00:00Z", "2026-09-27T09:00:00Z"),
            ProductTrashRow(
                "permanent", "Definitivo", "UN", "2026-09-28T08:00:00Z",
                "2026-10-05T08:00:00Z", "2026-09-28T08:30:00Z",
            ),
        )
        val categories = listOf(
            CategoryTrashRow("c1", "Panificação", "2026-09-28T09:30:00Z", "2026-10-05T09:30:00Z")
        )

        val items = buildRestorableCatalogTrash(products, categories, now)

        assertEquals(listOf("c1", "p1"), items.map { it.id })
        assertTrue(items.none { it.id == "expired" || it.id == "permanent" })
    }

    @Test
    fun categoryDeletionRequiresEmptyCategory() {
        assertTrue(canSoftDeleteCategory(0))
        assertFalse(canSoftDeleteCategory(1))
    }

    @Test
    fun productDeleteConfirmationWarnsAboutPositiveStock() {
        val warning = productDeleteConfirmation("Farinha", 12.0, "SC")
        assertTrue(warning.contains("12 SC"))
        assertTrue(warning.contains("cálculos ativos"))
        assertTrue(warning.contains("7 dias"))
    }

    @Test
    fun productDeleteConfirmationSkipsStockWarningWhenZero() {
        val warning = productDeleteConfirmation("Farinha", 0.0, "SC")
        assertFalse(warning.contains("cálculos ativos"))
        assertTrue(warning.contains("7 dias"))
    }

    @Test
    fun restoreErrorsAreFriendly() {
        assertEquals(
            "O prazo de 7 dias para restaurar este item expirou.",
            catalogTrashErrorMessage(IllegalStateException("Prazo de restauração expirado."))
        )
        assertEquals(
            "A Categoria só pode ser excluída quando estiver vazia.",
            catalogTrashErrorMessage(
                IllegalStateException("Categoria possui produtos ativos e não pode ser excluída.")
            )
        )
    }
}

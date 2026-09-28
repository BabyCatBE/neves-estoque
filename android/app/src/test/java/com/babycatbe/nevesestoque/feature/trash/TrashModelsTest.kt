package com.babycatbe.nevesestoque.feature.trash

import com.babycatbe.nevesestoque.feature.products.normalizeProductSearch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId

class TrashModelsTest {
    private val now = OffsetDateTime.parse("2026-09-28T10:00:00Z").toInstant().toEpochMilli()
    private val zone = ZoneId.of("America/Sao_Paulo")

    private val rows = TrashSourceRows(
        products = listOf(
            TrashProductRow("p1", "Farinha", "SC", "2026-09-28T09:00:00Z", "2026-10-05T09:00:00Z"),
            TrashProductRow("p-expired", "Expirado", "UN", "2026-09-20T09:00:00Z", "2026-09-27T09:00:00Z"),
            TrashProductRow(
                "p-permanent", "Definitivo", "UN", "2026-09-28T08:00:00Z",
                "2026-10-05T08:00:00Z", "2026-09-28T08:30:00Z",
            ),
            TrashProductRow("p-active", "Ativo", "UN", null, null),
        ),
        categories = listOf(
            TrashCategoryRow("c1", "Panificação", "2026-09-28T09:30:00Z", "2026-10-05T09:30:00Z"),
        ),
        suppliers = listOf(
            TrashSupplierRow("s1", "Moinho", company = null, phone = "(84) 9 9999-0000",
                deletedAt = "2026-09-27T12:00:00Z", restoreUntil = "2026-10-04T12:00:00Z"),
            TrashSupplierRow("s2", "Sem contato", deletedAt = "2026-09-26T12:00:00Z",
                restoreUntil = "2026-10-03T12:00:00Z"),
        ),
        entries = listOf(
            TrashEntryRow("e1", "s-hist", "2026-09-25T13:00:00Z", "2026-09-28T07:00:00Z", "2026-10-05T07:00:00Z"),
            TrashEntryRow("e2", "s-missing", "2026-09-25T13:00:00Z", "2026-09-28T06:00:00Z", "2026-10-05T06:00:00Z"),
        ),
        conferences = listOf(
            TrashConferenceRow("k1", "category", categoryId = "c-hist", effectiveAt = "2026-09-26T12:00:00Z",
                physicalResponsible = "Eychila", deletedAt = "2026-09-28T05:00:00Z", restoreUntil = "2026-10-05T05:00:00Z"),
            TrashConferenceRow("k2", "product", scopeProductId = "p-hist", effectiveAt = "2026-09-26T12:00:00Z",
                physicalResponsible = " ", deletedAt = "2026-09-28T04:00:00Z", restoreUntil = "2026-10-05T04:00:00Z"),
            TrashConferenceRow("k-permanent", "category", categoryId = "c-hist", effectiveAt = "2026-09-26T12:00:00Z",
                deletedAt = "2026-09-28T03:00:00Z", restoreUntil = "2026-10-05T03:00:00Z",
                permanentlyDeletedAt = "2026-09-28T03:30:00Z"),
        ),
        supplierNames = mapOf("s-hist" to "Distribuidora Histórica"),
        categoryNames = mapOf("c-hist" to "Laticínios"),
        productNames = mapOf("p-hist" to "Manteiga"),
    )

    @Test
    fun restorableTrashIncludesAllFiveTypesInsideWindowOnly() {
        val items = buildRestorableTrash(rows, now, zone)

        assertEquals(
            listOf("c1", "p1", "e1", "e2", "k1", "k2", "s1", "s2"),
            items.map { it.id },
        )
        assertTrue(items.none { it.id in setOf("p-expired", "p-permanent", "p-active", "k-permanent") })
        assertEquals(TrashItemType.entries.toSet(), items.map { it.type }.toSet())
    }

    @Test
    fun restorableTrashResolvesHistoricalNamesAndDetails() {
        val byId = buildRestorableTrash(rows, now, zone).associateBy { it.id }

        assertEquals("Distribuidora Histórica", byId.getValue("e1").name)
        assertEquals("Entrada de 25/09/2026", byId.getValue("e1").detail)
        assertEquals("Fornecedor não disponível", byId.getValue("e2").name)
        assertEquals("Laticínios", byId.getValue("k1").name)
        assertEquals("Conferência de 26/09/2026 · Responsável: Eychila", byId.getValue("k1").detail)
        assertEquals("Manteiga", byId.getValue("k2").name)
        assertEquals("Conferência de 26/09/2026", byId.getValue("k2").detail)
        assertEquals("(84) 9 9999-0000", byId.getValue("s1").detail)
        assertEquals("Cadastro pendente", byId.getValue("s2").detail)
        assertEquals("SC", byId.getValue("p1").detail)
        assertNull(byId.getValue("c1").detail)
    }

    @Test
    fun filterCombinesTypeAndAccentInsensitiveSearch() {
        val items = buildRestorableTrash(rows, now, zone)

        assertEquals(
            listOf("e1", "e2"),
            filterTrashItems(items, TrashItemType.Entry, "", ::normalizeProductSearch).map { it.id },
        )
        assertEquals(
            listOf("c1"),
            filterTrashItems(items, null, "panificacao", ::normalizeProductSearch).map { it.id },
        )
        assertEquals(
            listOf("k1", "k2"),
            filterTrashItems(items, null, "CONFERENCIA", ::normalizeProductSearch).map { it.id },
        )
        assertTrue(filterTrashItems(items, TrashItemType.Supplier, "farinha", ::normalizeProductSearch).isEmpty())
    }

    @Test
    fun typedConfirmationMatchesWebRule() {
        assertTrue(isTrashConfirmationValid(" excluir ", PERMANENT_DELETE_PHRASE))
        assertTrue(isTrashConfirmationValid("ESVAZIAR", EMPTY_TRASH_PHRASE))
        assertFalse(isTrashConfirmationValid("EXCLUI", PERMANENT_DELETE_PHRASE))
        assertFalse(isTrashConfirmationValid("", EMPTY_TRASH_PHRASE))
    }

    @Test
    fun filterParsingFallsBackToAll() {
        assertEquals(TrashItemType.Entry, TrashItemType.fromFilter("entry"))
        assertEquals(TrashItemType.Conference, TrashItemType.fromFilter("conference"))
        assertNull(TrashItemType.fromFilter(null))
        assertNull(TrashItemType.fromFilter("qualquer"))
    }

    @Test
    fun emptyTrashResultParsingAndMessages() {
        val result = parseEmptyTrashResult(
            buildJsonObject {
                put("products", JsonPrimitive(1))
                put("suppliers", JsonPrimitive(1))
                put("entries", JsonPrimitive(1))
                put("total", JsonPrimitive(3))
            }
        )

        assertEquals(EmptyTrashResult(1, 0, 1, 1, 0, 3), result)
        assertEquals(EmptyTrashResult(0, 0, 0, 0, 0, 0), parseEmptyTrashResult(null))
        assertEquals("A Lixeira já estava vazia.", emptyTrashSuccessMessage(EmptyTrashResult(0, 0, 0, 0, 0, 0)))
        assertEquals(
            "Lixeira esvaziada: 1 item ficou irrecuperável no aplicativo.",
            emptyTrashSuccessMessage(EmptyTrashResult(1, 0, 0, 0, 0, 1)),
        )
        assertEquals(
            "Lixeira esvaziada: 3 itens ficaram irrecuperáveis no aplicativo.",
            emptyTrashSuccessMessage(result),
        )
    }

    @Test
    fun messagesUseCorrectGenderAndKnownBackendErrors() {
        val entry = TrashItem("e1", TrashItemType.Entry, "Moinho", null, "2026-09-28T07:00:00Z", "2026-10-05T07:00:00Z")
        val supplier = entry.copy(type = TrashItemType.Supplier)

        assertEquals("Entrada “Moinho” restaurada com sucesso.", trashRestoreSuccessMessage(entry))
        assertEquals("Fornecedor “Moinho” restaurado com sucesso.", trashRestoreSuccessMessage(supplier))
        assertEquals(
            "Fornecedor “Moinho” excluído definitivamente da área restaurável.",
            trashPermanentDeleteSuccessMessage(supplier),
        )
        assertEquals(
            "O prazo de 7 dias para restaurar este item expirou.",
            trashErrorMessage(IllegalStateException("Prazo de restauração expirado")),
        )
        assertEquals(
            "Este item não está mais disponível para restauração.",
            trashErrorMessage(IllegalStateException("Conferência não encontrada na lixeira.")),
        )
        assertEquals(
            "Este dispositivo não está autorizado para esta ação.",
            trashErrorMessage(IllegalStateException("Dispositivo não autorizado.")),
        )
    }
}

package com.babycatbe.nevesestoque.feature.reports

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.data.supabase.fetchAllByKeyset
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
internal data class ReportProductRow(
    val id: String,
    val name: String,
    val unit: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("initial_stock_quantity") val initialStockQuantity: Double? = null,
    @SerialName("initial_stock_at") val initialStockAt: String? = null,
    @SerialName("initial_price") val initialPrice: Double? = null,
    @SerialName("initial_price_at") val initialPriceAt: String? = null,
)

@Serializable
internal data class ReportHeaderRow(
    val id: String,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class ReportEntryItemRow(
    val id: String,
    @SerialName("entry_id") val entryId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double? = null,
    val position: Int,
)

@Serializable
internal data class ReportConferenceItemRow(
    val id: String,
    @SerialName("conference_id") val conferenceId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
)

@Serializable
internal data class ReportProductAuditRow(
    val id: Long,
    @SerialName("entity_id") val entityId: String? = null,
    val action: String,
    @SerialName("created_at") val createdAt: String,
)

/**
 * Lê os mesmos fatos que a Web usa em src/features/reports/api/reports.ts, com paginação,
 * e delega todo o cálculo para ReportsCalculation. Somente leitura: nada é gravado.
 */
class ReportsRepository {
    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    /**
     * Leitura completa por cursor de `id` (paridade com a Web): a página curta devolvida
     * pelo teto `max-rows` do servidor não encerra mais a leitura; só a página vazia.
     */
    private suspend inline fun <reified T : Any, K : Comparable<K>> readAll(
        table: String,
        columns: List<String>,
        noinline keyOf: (T) -> K,
        onlyActive: Boolean = false,
        entityType: String? = null,
    ): List<T> {
        val client = client()
        return fetchAllByKeyset(keyOf) { afterKey, pageLimit ->
            client.from(table)
                .select(Columns.list(*columns.toTypedArray())) {
                    attachRegisteredDevice()
                    if (onlyActive || entityType != null || afterKey != null) {
                        filter {
                            if (onlyActive) exact("deleted_at", null)
                            if (entityType != null) eq("entity_type", entityType)
                            if (afterKey != null) gt("id", afterKey)
                        }
                    }
                    order("id", Order.ASCENDING)
                    limit(pageLimit)
                }
                .decodeList<T>()
        }
    }

    suspend fun loadFacts(): MonthlyStockReportFacts {
        val products = readAll<ReportProductRow, String>(
            "products",
            listOf(
                "id", "name", "unit", "created_at", "deleted_at", "initial_stock_quantity",
                "initial_stock_at", "initial_price", "initial_price_at",
            ),
            keyOf = ReportProductRow::id,
        )
        val entries = readAll<ReportHeaderRow, String>(
            "entries", listOf("id", "effective_at", "created_at"), ReportHeaderRow::id, onlyActive = true,
        )
        val entryItems = readAll<ReportEntryItemRow, String>(
            "entry_items",
            listOf("id", "entry_id", "product_id", "quantity", "unit_price", "position"),
            ReportEntryItemRow::id,
        )
        val conferences = readAll<ReportHeaderRow, String>(
            "conferences", listOf("id", "effective_at", "created_at"), ReportHeaderRow::id, onlyActive = true,
        )
        val conferenceItems = readAll<ReportConferenceItemRow, String>(
            "conference_items", listOf("id", "conference_id", "product_id", "quantity"),
            ReportConferenceItemRow::id,
        )
        val audits = readAll<ReportProductAuditRow, Long>(
            "audit_log", listOf("id", "entity_id", "action", "created_at"),
            ReportProductAuditRow::id, entityType = "products",
        )

        return assembleReportFacts(products, entries, entryItems, conferences, conferenceItems, audits)
    }

    /** Série mensal oficial: do primeiro mês com fatos até o mês atual (provisório). */
    suspend fun loadHistory(now: Instant = Instant.now()): MonthlyStockValueSeries {
        val available = filterFactsAvailableAt(loadFacts(), now)
        return calculateMonthlyStockValueSeries(reportCurrentDateKey(now), available)
    }
}

/** Monta os fatos do relatório a partir das linhas lidas (sem rede; testável). */
internal fun assembleReportFacts(
    products: List<ReportProductRow>,
    entries: List<ReportHeaderRow>,
    entryItems: List<ReportEntryItemRow>,
    conferences: List<ReportHeaderRow>,
    conferenceItems: List<ReportConferenceItemRow>,
    audits: List<ReportProductAuditRow>,
): MonthlyStockReportFacts {
    val entryById = entries.associateBy { it.id }
    val conferenceById = conferences.associateBy { it.id }

    return MonthlyStockReportFacts(
        products = products.map {
            HistoricalReportProduct(
                id = it.id,
                name = it.name,
                unit = it.unit,
                createdAt = it.createdAt,
                deletedAt = it.deletedAt,
                initialStockQuantity = it.initialStockQuantity,
                initialStockAt = it.initialStockAt,
                initialPrice = it.initialPrice,
                initialPriceAt = it.initialPriceAt,
            )
        },
        entries = entryItems.mapNotNull { item ->
            val entry = entryById[item.entryId] ?: return@mapNotNull null
            HistoricalEntryFact(
                id = item.id,
                productId = item.productId,
                effectiveAt = entry.effectiveAt,
                createdAt = entry.createdAt,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                position = item.position,
            )
        },
        conferences = conferenceItems.mapNotNull { item ->
            val conference = conferenceById[item.conferenceId] ?: return@mapNotNull null
            HistoricalConferenceFact(
                id = conference.id,
                productId = item.productId,
                effectiveAt = conference.effectiveAt,
                createdAt = conference.createdAt,
                quantity = item.quantity,
            )
        },
        merges = audits.mapNotNull { audit ->
            val productId = audit.entityId ?: return@mapNotNull null
            if (audit.action != "PRODUCT_MERGE") return@mapNotNull null
            HistoricalProductMergeFact(audit.id, productId, audit.createdAt)
        },
        lifecycle = audits.mapNotNull { audit ->
            val productId = audit.entityId ?: return@mapNotNull null
            val action = when (audit.action) {
                "SOFT_DELETE" -> LifecycleAction.SoftDelete
                "RESTORE" -> LifecycleAction.Restore
                else -> return@mapNotNull null
            }
            HistoricalProductLifecycleFact(audit.id, productId, action, audit.createdAt)
        },
    )
}

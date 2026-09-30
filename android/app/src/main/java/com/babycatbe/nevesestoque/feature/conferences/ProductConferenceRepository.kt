package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.data.supabase.fetchByIdChunks
import com.babycatbe.nevesestoque.feature.products.ProductConferenceItemRow
import com.babycatbe.nevesestoque.feature.products.ProductConferenceRow
import com.babycatbe.nevesestoque.feature.products.ProductDetails
import com.babycatbe.nevesestoque.feature.products.ProductEntryItemRow
import com.babycatbe.nevesestoque.feature.products.ProductEntryRow
import com.babycatbe.nevesestoque.feature.products.ProductsRepository
import com.babycatbe.nevesestoque.feature.products.UsageConferencePoint
import com.babycatbe.nevesestoque.feature.products.UsageEntryPoint
import com.babycatbe.nevesestoque.feature.products.UsageStatus
import com.babycatbe.nevesestoque.feature.products.calculateProductUsageInsights
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

data class ProductConferenceWriteInput(
    val productId: String,
    val effectiveAt: String,
    val physicalResponsible: String,
    val deviceId: String,
    val idempotencyKey: String,
    val quantity: Double,
    val observation: String?,
)

class ProductConferenceRepository {
    private val productsRepository = ProductsRepository()

    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    suspend fun loadProduct(productId: String): ProductDetails =
        try {
            productsRepository.loadProductDetails(productId).first
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (!com.babycatbe.nevesestoque.data.offline.isNetworkFailure(error)) throw error
            // Sem internet: usa a cópia local do catálogo (dados mínimos para a Conferência unitária).
            val product = productsRepository.loadCatalog().products.firstOrNull { it.id == productId }
                ?: throw error
            ProductDetails(
                id = product.id,
                name = product.name,
                categoryId = product.categoryId,
                unit = product.unit,
                createdAt = product.createdAt,
                initialStockQuantity = null,
                initialStockAt = null,
                initialPrice = null,
                initialPriceAt = null,
                currentQuantity = product.currentQuantity,
                currentPrice = product.currentPrice,
                currentValue = null,
                stockRequiresConference = product.stockRequiresConference,
                priceHistory = emptyList(),
                usageInsights = com.babycatbe.nevesestoque.feature.products.calculateProductUsageInsights(
                    conferences = emptyList(),
                    entries = emptyList(),
                    currentQuantity = product.currentQuantity,
                ),
            )
        }

    suspend fun createProductConference(input: ProductConferenceWriteInput) {
        client().postgrest.rpc(
            function = "create_product_conference",
            parameters = buildJsonObject {
                put("p_product_id", input.productId)
                put("p_effective_at", input.effectiveAt)
                put("p_physical_responsible", input.physicalResponsible)
                put("p_device_id", input.deviceId)
                put("p_idempotency_key", input.idempotencyKey)
                put("p_quantity", input.quantity)
                input.observation?.let { put("p_observation", it) }
            },
        ) {
            attachRegisteredDevice()
        }
    }

    suspend fun reviewConsumption(
        productId: String,
        candidateQuantity: Double,
        effectiveAt: String,
        excludeConferenceId: String? = null,
    ): ConferenceConsumptionWarning? {
        val client = client()
        val candidateTime = epoch(effectiveAt) ?: return null

        val conferenceItems = client.from("conference_items")
            .select(Columns.list("conference_id", "product_id", "quantity")) {
                attachRegisteredDevice()
                filter { eq("product_id", productId) }
            }
            .decodeList<ProductConferenceItemRow>()

        val conferenceIds = conferenceItems.map { it.conferenceId }.toSet()
        // Cabeçalhos buscados pelos ids dos itens deste Produto (não o histórico inteiro).
        val conferences = fetchByIdChunks(conferenceIds) { ids ->
            client.from("conferences")
                .select(Columns.list("id", "effective_at", "created_at", "deleted_at")) {
                    attachRegisteredDevice()
                    filter {
                        isIn("id", ids)
                        exact("deleted_at", null)
                        lt("effective_at", effectiveAt)
                    }
                }
                .decodeList<ProductConferenceRow>()
        }.filter { it.id in conferenceIds && it.id != excludeConferenceId }

        val conferenceById = conferences.associateBy { it.id }
        val conferencePoints = conferenceItems.mapNotNull { item ->
            val conference = conferenceById[item.conferenceId] ?: return@mapNotNull null
            UsageConferencePoint(
                effectiveAt = conference.effectiveAt,
                createdAt = conference.createdAt,
                quantity = item.quantity,
            )
        }.sortedWith(
            compareBy<UsageConferencePoint> { epoch(it.effectiveAt) ?: Long.MIN_VALUE }
                .thenBy { epoch(it.createdAt) ?: Long.MIN_VALUE }
        )

        val entryItems = client.from("entry_items")
            .select(
                Columns.list("id", "entry_id", "product_id", "quantity", "unit_price", "position")
            ) {
                attachRegisteredDevice()
                filter { eq("product_id", productId) }
            }
            .decodeList<ProductEntryItemRow>()

        val entryIds = entryItems.map { it.entryId }.toSet()
        val entries = fetchByIdChunks(entryIds) { ids ->
            client.from("entries")
                .select(
                    Columns.list("id", "effective_at", "created_at", "supplier_id", "deleted_at")
                ) {
                    attachRegisteredDevice()
                    filter {
                        isIn("id", ids)
                        exact("deleted_at", null)
                        lte("effective_at", effectiveAt)
                    }
                }
                .decodeList<ProductEntryRow>()
        }.filter { it.id in entryIds }

        val entryById = entries.associateBy { it.id }
        val entryPoints = entryItems.mapNotNull { item ->
            val entry = entryById[item.entryId] ?: return@mapNotNull null
            UsageEntryPoint(
                effectiveAt = entry.effectiveAt,
                quantity = item.quantity,
            )
        }

        val usage = calculateProductUsageInsights(
            conferences = conferencePoints,
            entries = entryPoints,
            currentQuantity = null,
            referenceTimeMillis = candidateTime,
        )
        val dailyAverage = usage.dailyAverage
        if (usage.status != UsageStatus.Ready || dailyAverage == null) return null

        val previous = conferencePoints.lastOrNull() ?: return null
        val previousTime = epoch(previous.effectiveAt) ?: return null
        if (candidateTime <= previousTime) return null

        val intervalDays = (candidateTime - previousTime) / DAY_MS
        val entriesQuantity = entryPoints
            .filter { point ->
                val time = epoch(point.effectiveAt)
                time != null && time > previousTime && time <= candidateTime
            }
            .sumOf { it.quantity }

        return evaluateConferenceConsumption(
            productId = productId,
            expectedDailyAverage = dailyAverage,
            intervalDays = intervalDays,
            previousQuantity = previous.quantity,
            entriesQuantity = entriesQuantity,
            candidateQuantity = candidateQuantity,
        )
    }

    private fun epoch(value: String): Long? =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

    private companion object {
        const val DAY_MS = 24.0 * 60 * 60 * 1000
    }
}

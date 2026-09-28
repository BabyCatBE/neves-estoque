package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.feature.products.ProductsRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

class ConferenceModuleRepository {
    private val productConferenceRepository = ProductConferenceRepository()
    private val productsRepository = ProductsRepository()

    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    suspend fun loadCategorySummaries(): List<ConferenceCategorySummary> {
        val client = client()
        val categories = loadActiveCategories()
        val products = loadActiveProducts()
        val conferences = client.from("conferences")
            .select(
                Columns.list(
                    "id", "effective_at", "created_at", "physical_responsible",
                    "observation", "scope_type", "category_id", "scope_product_id",
                    "idempotency_key", "deleted_at",
                )
            ) {
                attachRegisteredDevice()
                filter {
                    eq("scope_type", "category")
                    exact("deleted_at", null)
                }
            }
            .decodeList<ConferenceRow>()

        val today = java.time.LocalDate.now().toString()
        return categories.map { category ->
            val categoryConferences = conferences
                .filter { it.categoryId == category.id }
                .sortedWith(conferenceHistoryComparator)
            val latest = categoryConferences.firstOrNull()
            ConferenceCategorySummary(
                id = category.id,
                name = category.name,
                sortOrder = category.sortOrder,
                productCount = products.count { it.categoryId == category.id },
                lastConferenceAt = latest?.effectiveAt,
                conferredToday = latest?.effectiveAt?.let(::conferenceLocalDate) == today,
            )
        }
    }

    suspend fun loadCategorySetup(categoryId: String): CategoryConferenceSetup {
        val category = loadActiveCategories().firstOrNull { it.id == categoryId }
            ?: error("Categoria não encontrada.")
        val products = loadActiveProducts()
            .filter { it.categoryId == categoryId }
            .sortedWith(
                compareBy<ConferenceProductRow> { it.sortOrder ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() }
            )
            .map { ConferenceProduct(it.id, it.name, it.unit, it.sortOrder) }

        return CategoryConferenceSetup(
            categoryId = category.id,
            categoryName = category.name,
            products = products,
        )
    }

    suspend fun loadCategoryHistory(categoryId: String): List<ConferenceHistoryItem> {
        val rows = client().from("conferences")
            .select(
                Columns.list(
                    "id", "effective_at", "created_at", "physical_responsible",
                    "observation", "scope_type", "category_id", "scope_product_id",
                    "idempotency_key", "deleted_at",
                )
            ) {
                attachRegisteredDevice()
                filter {
                    eq("scope_type", "category")
                    eq("category_id", categoryId)
                    exact("deleted_at", null)
                }
            }
            .decodeList<ConferenceRow>()

        return rows.mapNotNull { row ->
            val resolvedCategoryId = row.categoryId ?: return@mapNotNull null
            ConferenceHistoryItem(
                id = row.id,
                categoryId = resolvedCategoryId,
                effectiveAt = row.effectiveAt,
                createdAt = row.createdAt,
                physicalResponsible = row.physicalResponsible,
                observation = row.observation,
            )
        }.sortedWith(
            compareByDescending<ConferenceHistoryItem> { conferenceEpoch(it.effectiveAt) }
                .thenByDescending { conferenceEpoch(it.createdAt) }
        )
    }

    suspend fun listSameDayCategoryConferences(
        categoryId: String,
        dateValue: String,
        excludeConferenceId: String? = null,
    ): List<ConferenceHistoryItem> =
        loadCategoryHistory(categoryId).filter {
            it.id != excludeConferenceId && conferenceLocalDate(it.effectiveAt) == dateValue
        }

    suspend fun loadConferenceDetails(conferenceId: String): ConferenceDetails {
        val client = client()
        val conference = client.from("conferences")
            .select(
                Columns.list(
                    "id", "effective_at", "created_at", "physical_responsible",
                    "observation", "scope_type", "category_id", "scope_product_id",
                    "idempotency_key", "deleted_at",
                )
            ) {
                attachRegisteredDevice()
                filter {
                    eq("id", conferenceId)
                    exact("deleted_at", null)
                }
            }
            .decodeList<ConferenceRow>()
            .firstOrNull()
            ?: error("Conferência de categoria não encontrada.")

        if (conference.scopeType != "category" || conference.categoryId == null) {
            error("Conferência de categoria não encontrada.")
        }

        val categories = client.from("categories")
            .select(Columns.list("id", "name", "sort_order", "illustration_source", "illustration_key", "deleted_at")) {
                attachRegisteredDevice()
            }
            .decodeList<ConferenceCategoryRow>()
            .associateBy { it.id }

        val items = client.from("conference_items")
            .select(Columns.list("id", "conference_id", "product_id", "quantity", "position")) {
                attachRegisteredDevice()
                filter { eq("conference_id", conferenceId) }
            }
            .decodeList<ConferenceItemRow>()
            .sortedBy { it.position }

        val products = client.from("products")
            .select(Columns.list("id", "name", "category_id", "unit", "sort_order", "deleted_at")) {
                attachRegisteredDevice()
            }
            .decodeList<ConferenceProductRow>()
            .associateBy { it.id }

        return ConferenceDetails(
            id = conference.id,
            categoryId = conference.categoryId,
            categoryName = categories[conference.categoryId]?.name ?: "Categoria não disponível",
            effectiveAt = conference.effectiveAt,
            createdAt = conference.createdAt,
            physicalResponsible = conference.physicalResponsible,
            observation = conference.observation,
            items = items.map { item ->
                val product = products[item.productId]
                ConferenceDetailItem(
                    id = item.id,
                    productId = item.productId,
                    productName = product?.name ?: "Produto não disponível",
                    unit = product?.unit ?: "—",
                    quantity = item.quantity,
                    position = item.position,
                )
            },
        )
    }

    suspend fun loadPrintData(): ConferencePrintData {
        val categories = loadActiveCategories()
        val products = loadActiveProducts()
        val visualCategories = runCatching { productsRepository.loadCategories() }
            .getOrDefault(emptyList())
            .associateBy { it.id }

        return ConferencePrintData(
            categories = categories.map { category ->
                val visual = visualCategories[category.id]
                ConferencePrintCategory(
                    id = category.id,
                    name = category.name,
                    illustrationSource = visual?.illustrationSource ?: category.illustrationSource,
                    illustrationKey = visual?.illustrationKey ?: category.illustrationKey,
                    illustrationPositionX = visual?.illustrationPositionX ?: 50,
                    illustrationPositionY = visual?.illustrationPositionY ?: 50,
                    illustrationBytes = visual?.illustrationBytes,
                    products = products
                        .filter { it.categoryId == category.id }
                        .sortedWith(
                            compareBy<ConferenceProductRow> { it.sortOrder ?: Int.MAX_VALUE }
                                .thenBy { it.name.lowercase() }
                        )
                        .map { ConferenceProduct(it.id, it.name, it.unit, it.sortOrder) },
                )
            },
            pendingProductCount = products.count { it.categoryId == null },
        )
    }

    suspend fun reviewConsumption(
        effectiveAt: String,
        items: List<CategoryConferenceWriteItem>,
        excludeConferenceId: String? = null,
    ): List<ConferenceConsumptionWarning> {
        val warnings = mutableListOf<ConferenceConsumptionWarning>()
        for (item in items) {
            productConferenceRepository.reviewConsumption(
                productId = item.productId,
                candidateQuantity = item.quantity,
                effectiveAt = effectiveAt,
                excludeConferenceId = excludeConferenceId,
            )?.let(warnings::add)
        }
        return warnings
    }

    suspend fun createCategoryConference(input: CategoryConferenceWriteInput): String {
        return client().postgrest.rpc(
            function = "create_category_conference",
            parameters = buildJsonObject {
                put("p_category_id", input.categoryId)
                put("p_effective_at", input.effectiveAt)
                put("p_physical_responsible", input.physicalResponsible)
                put("p_device_id", input.deviceId)
                put("p_idempotency_key", input.idempotencyKey)
                put("p_items", conferenceItemsJson(input.items))
                put("p_observation", input.observation?.let(::JsonPrimitive) ?: JsonNull)
            },
        ) { attachRegisteredDevice() }.decodeAs()
    }

    suspend fun findConferenceIdByIdempotencyKey(idempotencyKey: String): String? =
        client().from("conferences")
            .select(Columns.list("id", "idempotency_key", "deleted_at")) {
                attachRegisteredDevice()
                filter { eq("idempotency_key", idempotencyKey) }
            }
            .decodeList<ConferenceLookupRow>()
            .firstOrNull { it.deletedAt == null }
            ?.id

    suspend fun updateCategoryConference(input: CategoryConferenceUpdateInput) {
        client().postgrest.rpc(
            function = "update_category_conference",
            parameters = buildJsonObject {
                put("p_conference_id", input.conferenceId)
                put("p_effective_at", input.effectiveAt)
                put("p_physical_responsible", input.physicalResponsible)
                put("p_device_id", input.deviceId)
                put("p_items", conferenceItemsJson(input.items))
                put("p_observation", input.observation?.let(::JsonPrimitive) ?: JsonNull)
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun isUpdateApplied(input: CategoryConferenceUpdateInput): Boolean =
        categoryConferenceUpdateMatches(loadConferenceDetails(input.conferenceId), input)

    suspend fun softDeleteConference(conferenceId: String, deviceId: String) {
        client().postgrest.rpc(
            function = "soft_delete_conference",
            parameters = buildJsonObject {
                put("p_conference_id", conferenceId)
                put("p_device_id", deviceId)
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun isConferenceActive(conferenceId: String): Boolean {
        val row = client().from("conferences")
            .select(Columns.list("id", "idempotency_key", "deleted_at")) {
                attachRegisteredDevice()
                filter { eq("id", conferenceId) }
            }
            .decodeList<ConferenceLookupRow>()
            .firstOrNull()
            ?: return false
        return row.deletedAt == null
    }

    private suspend fun loadActiveCategories(): List<ConferenceCategoryRow> =
        client().from("categories")
            .select(Columns.list("id", "name", "sort_order", "illustration_source", "illustration_key", "deleted_at")) {
                attachRegisteredDevice()
            }
            .decodeList<ConferenceCategoryRow>()
            .filter { it.deletedAt == null }
            .sortedWith(
                compareBy<ConferenceCategoryRow> { it.sortOrder ?: Int.MAX_VALUE }
                    .thenBy { it.name.lowercase() }
            )

    private suspend fun loadActiveProducts(): List<ConferenceProductRow> =
        client().from("products")
            .select(Columns.list("id", "name", "category_id", "unit", "sort_order", "deleted_at")) {
                attachRegisteredDevice()
            }
            .decodeList<ConferenceProductRow>()
            .filter { it.deletedAt == null }

    private fun conferenceItemsJson(items: List<CategoryConferenceWriteItem>) = buildJsonArray {
        items.forEach { item ->
            add(buildJsonObject {
                put("product_id", item.productId)
                put("quantity", item.quantity)
            })
        }
    }

    private fun conferenceEpoch(value: String): Long =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(Long.MIN_VALUE)

    private val conferenceHistoryComparator =
        compareByDescending<ConferenceRow> { conferenceEpoch(it.effectiveAt) }
            .thenByDescending { conferenceEpoch(it.createdAt) }
}

package com.babycatbe.nevesestoque.feature.entries

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import com.babycatbe.nevesestoque.data.offline.offlineCachedList
import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

class EntriesRepository {
    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    suspend fun loadFormOptions(): EntryFormOptions = coroutineScope {
        val client = client()
        val suppliersAsync = async {
            offlineCachedList<EntrySupplierRow>("entry-options-suppliers") {
                client.from("suppliers")
                .select(Columns.list("id", "name", "company", "phone", "deleted_at")) {
                    attachRegisteredDevice()
                }
                .decodeList<EntrySupplierRow>()
            }
                .filter { it.deletedAt == null }
                .map {
                    EntrySupplierOption(
                        id = it.id,
                        name = it.name,
                        company = it.company,
                        phone = it.phone,
                        isPending = it.company.isNullOrBlank() ||
                            it.phone.orEmpty().filter(Char::isDigit).length != 11,
                    )
                }
                .sortedBy { normalizeEntrySearchText(it.name) }
        }

        val productsAsync = async {
            offlineCachedList<EntryProductRow>("entry-options-products") {
                client.from("products")
                .select(
                    Columns.list(
                        "id", "name", "category_id", "unit", "sort_order",
                        "created_at", "deleted_at",
                    )
                ) { attachRegisteredDevice() }
                .decodeList<EntryProductRow>()
            }
                .filter { it.deletedAt == null }
                .map {
                    EntryProductOption(
                        id = it.id,
                        name = it.name,
                        categoryId = it.categoryId,
                        unit = it.unit,
                        sortOrder = it.sortOrder,
                        createdAt = it.createdAt,
                    )
                }
                .sortedBy { normalizeEntrySearchText(it.name) }
        }

        val categoriesAsync = async {
            offlineCachedList<EntryCategoryRow>("entry-options-categories") {
                client.from("categories")
                .select(Columns.list("id", "name", "sort_order", "deleted_at")) {
                    attachRegisteredDevice()
                }
                .decodeList<EntryCategoryRow>()
            }
                .filter { it.deletedAt == null }
                .map { EntryCategoryOption(it.id, it.name, it.sortOrder) }
                .sortedWith(
                    compareBy<EntryCategoryOption> { it.sortOrder ?: Int.MAX_VALUE }
                        .thenBy { normalizeEntrySearchText(it.name) }
                )
        }

        val suppliers = suppliersAsync.await()
        val products = productsAsync.await()
        val categories = categoriesAsync.await()

        EntryFormOptions(suppliers, products, categories)
    }

    suspend fun loadHistory(): List<EntryHistoryItem> {
        val client = client()
        val entries = client.from("entries")
            .select(
                Columns.list(
                    "id", "supplier_id", "effective_at", "observation",
                    "idempotency_key", "deleted_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<EntryRow>()
            .filter { it.deletedAt == null }

        if (entries.isEmpty()) return emptyList()

        val entryIds = entries.map { it.id }.toSet()
        val suppliers = client.from("suppliers")
            .select(Columns.list("id", "name", "company", "phone", "deleted_at")) {
                attachRegisteredDevice()
            }
            .decodeList<EntrySupplierRow>()
            .associateBy { it.id }

        val items = client.from("entry_items")
            .select(
                Columns.list(
                    "id", "entry_id", "product_id", "quantity", "unit_price", "position"
                )
            ) { attachRegisteredDevice() }
            .decodeList<EntryItemRow>()
            .filter { it.entryId in entryIds }

        val products = client.from("products")
            .select(
                Columns.list(
                    "id", "name", "category_id", "unit", "sort_order",
                    "created_at", "deleted_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<EntryProductRow>()
            .associateBy { it.id }

        return entries.map { entry ->
            val entryItems = items.filter { it.entryId == entry.id }.sortedBy { it.position }
            EntryHistoryItem(
                id = entry.id,
                effectiveAt = entry.effectiveAt,
                supplierName = suppliers[entry.supplierId]?.name ?: "Fornecedor não disponível",
                observation = entry.observation,
                totalKnown = entryItems.sumOf { item ->
                    item.unitPrice?.let { item.quantity * it } ?: 0.0
                },
                hasMissingPrice = entryItems.any { it.unitPrice == null },
                productNames = entryItems.map {
                    products[it.productId]?.name ?: "Produto não disponível"
                },
            )
        }.sortedByDescending {
            runCatching { OffsetDateTime.parse(it.effectiveAt).toInstant() }.getOrNull()
        }
    }

    suspend fun loadEntryDetails(entryId: String): EntryDetails {
        val client = client()
        val entry = client.from("entries")
            .select(
                Columns.list(
                    "id", "supplier_id", "effective_at", "observation",
                    "idempotency_key", "deleted_at",
                )
            ) {
                attachRegisteredDevice()
                filter {
                    eq("id", entryId)
                    exact("deleted_at", null)
                }
            }
            .decodeList<EntryRow>()
            .firstOrNull()
            ?: error("Entrada não encontrada.")

        val supplier = client.from("suppliers")
            .select(Columns.list("id", "name", "company", "phone", "deleted_at")) {
                attachRegisteredDevice()
                filter { eq("id", entry.supplierId) }
            }
            .decodeList<EntrySupplierRow>()
            .firstOrNull()

        val items = client.from("entry_items")
            .select(
                Columns.list(
                    "id", "entry_id", "product_id", "quantity", "unit_price", "position"
                )
            ) {
                attachRegisteredDevice()
                filter { eq("entry_id", entryId) }
            }
            .decodeList<EntryItemRow>()
            .sortedBy { it.position }

        val products = client.from("products")
            .select(
                Columns.list(
                    "id", "name", "category_id", "unit", "sort_order",
                    "created_at", "deleted_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<EntryProductRow>()
            .associateBy { it.id }

        val detailItems = items.map { item ->
            val product = products[item.productId]
            EntryDetailItem(
                id = item.id,
                productId = item.productId,
                productName = product?.name ?: "Produto não disponível",
                unit = product?.unit ?: "—",
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                position = item.position,
            )
        }

        return EntryDetails(
            id = entry.id,
            effectiveAt = entry.effectiveAt,
            supplierId = entry.supplierId,
            supplierName = supplier?.name ?: "Fornecedor não disponível",
            supplierCompany = supplier?.company,
            observation = entry.observation,
            totalKnown = detailItems.sumOf { item ->
                item.unitPrice?.let { item.quantity * it } ?: 0.0
            },
            hasMissingPrice = detailItems.any { it.unitPrice == null },
            items = detailItems,
        )
    }

    suspend fun loadEditData(entryId: String): EntryEditData =
        EntryEditData(
            entry = loadEntryDetails(entryId),
            options = loadFormOptions(),
        )

    suspend fun createEntry(input: EntryCreateInput): String {
        val deviceId = DeviceIdentityStore.registeredDeviceId()
            ?: error("Dispositivo não autorizado.")
        val common = buildJsonObject {
            put("p_effective_at", input.effectiveAt)
            put("p_device_id", deviceId)
            put("p_idempotency_key", input.idempotencyKey)
            put("p_items", createItemsJson(input.items))
            put("p_observation", input.observation?.let(::JsonPrimitive) ?: JsonNull)
        }

        val parameters = buildJsonObject {
            common.forEach { (key, value) -> put(key, value) }
            if (input.newSupplier != null) {
                put("p_supplier_name", input.newSupplier.name)
            } else {
                put("p_supplier_id", input.supplierId ?: error("Selecione um Fornecedor."))
            }
        }

        return client().postgrest.rpc(
            function = if (input.newSupplier != null) {
                "create_entry_with_draft_supplier"
            } else {
                "create_entry"
            },
            parameters = parameters,
        ) { attachRegisteredDevice() }.decodeAs()
    }

    suspend fun findEntryIdByIdempotencyKey(idempotencyKey: String): String? =
        client().from("entries")
            .select(Columns.list("id", "idempotency_key", "deleted_at")) {
                attachRegisteredDevice()
                filter { eq("idempotency_key", idempotencyKey) }
            }
            .decodeList<EntryLookupRow>()
            .firstOrNull { it.deletedAt == null }
            ?.id

    suspend fun updateEntry(input: EntryUpdateInput) {
        val deviceId = DeviceIdentityStore.registeredDeviceId()
            ?: error("Dispositivo não autorizado.")
        client().postgrest.rpc(
            function = "update_entry",
            parameters = buildJsonObject {
                put("p_entry_id", input.entryId)
                put("p_supplier_id", input.supplierId)
                put("p_effective_at", input.effectiveAt)
                put("p_device_id", deviceId)
                put("p_items", updateItemsJson(input.items))
                put("p_observation", input.observation?.let(::JsonPrimitive) ?: JsonNull)
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun isUpdateApplied(input: EntryUpdateInput): Boolean =
        entryMatchesUpdate(loadEntryDetails(input.entryId), input)

    suspend fun softDeleteEntry(entryId: String) {
        val deviceId = DeviceIdentityStore.registeredDeviceId()
            ?: error("Dispositivo não autorizado.")
        client().postgrest.rpc(
            function = "soft_delete_entry",
            parameters = buildJsonObject {
                put("p_entry_id", entryId)
                put("p_device_id", deviceId)
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun isEntryActive(entryId: String): Boolean {
        val row = client().from("entries")
            .select(Columns.list("id", "idempotency_key", "deleted_at")) {
                attachRegisteredDevice()
                filter { eq("id", entryId) }
            }
            .decodeList<EntryLookupRow>()
            .firstOrNull()
            ?: return false

        return row.deletedAt == null
    }

    suspend fun completePendingProduct(
        productId: String,
        name: String,
        categoryId: String,
    ): EntryProductOption {
        client().postgrest.rpc(
            function = "update_product_details",
            parameters = buildJsonObject {
                put("p_product_id", productId)
                put("p_name", name)
                put("p_category_id", categoryId)
            },
        ) { attachRegisteredDevice() }

        return loadFormOptions().products.firstOrNull { it.id == productId }
            ?: error("Produto não encontrado.")
    }

    private fun createItemsJson(items: List<EntryCreateItem>) = buildJsonArray {
        items.forEach { item ->
            add(buildJsonObject {
                if (item.newProduct != null) {
                    put("new_product", buildJsonObject {
                        put("client_id", item.newProduct.clientId)
                        put("name", item.newProduct.name)
                        put("unit", item.newProduct.unit)
                        item.newProduct.categoryId?.let { put("category_id", it) }
                    })
                } else {
                    put("product_id", item.productId ?: error("Produto é obrigatório."))
                }
                put("quantity", item.quantity)
                item.unitPrice?.let { put("unit_price", it) }
            })
        }
    }

    private fun updateItemsJson(items: List<EntryUpdateItem>) = buildJsonArray {
        items.forEach { item ->
            add(buildJsonObject {
                put("product_id", item.productId)
                put("quantity", item.quantity)
                item.unitPrice?.let { put("unit_price", it) }
            })
        }
    }
}

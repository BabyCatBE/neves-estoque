package com.babycatbe.nevesestoque.feature.trash

import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Lixeira Universal Android. Reutiliza exatamente os contratos oficiais já usados pela Web:
 * restore_product, restore_supplier, restore_entry, restore_conference,
 * permanently_delete_trash_item e empty_trash. Categoria restaura pelo mesmo update
 * protegido por RLS usado na Web. Nenhuma autorização é decidida no aplicativo.
 */
class TrashRepository {
    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    private fun requireDeviceId(): String =
        DeviceIdentityStore.registeredDeviceId() ?: error("Dispositivo não autorizado.")

    suspend fun loadRestorableTrash(): List<TrashItem> {
        val client = client()

        val products = client.from("products")
            .select(Columns.list("id", "name", "unit", "deleted_at", "restore_until", "permanently_deleted_at")) {
                attachRegisteredDevice()
                filter { exact("permanently_deleted_at", null) }
            }
            .decodeList<TrashProductRow>()
            .filter { it.deletedAt != null }

        val categories = client.from("categories")
            .select(Columns.list("id", "name", "deleted_at", "restore_until", "permanently_deleted_at")) {
                attachRegisteredDevice()
                filter { exact("permanently_deleted_at", null) }
            }
            .decodeList<TrashCategoryRow>()
            .filter { it.deletedAt != null }

        val suppliers = client.from("suppliers")
            .select(
                Columns.list(
                    "id", "name", "company", "phone", "deleted_at", "restore_until", "permanently_deleted_at",
                )
            ) {
                attachRegisteredDevice()
                filter { exact("permanently_deleted_at", null) }
            }
            .decodeList<TrashSupplierRow>()
            .filter { it.deletedAt != null }

        val entries = client.from("entries")
            .select(
                Columns.list(
                    "id", "supplier_id", "effective_at", "deleted_at", "restore_until", "permanently_deleted_at",
                )
            ) {
                attachRegisteredDevice()
                filter { exact("permanently_deleted_at", null) }
            }
            .decodeList<TrashEntryRow>()
            .filter { it.deletedAt != null }

        val conferences = client.from("conferences")
            .select(
                Columns.list(
                    "id", "scope_type", "category_id", "scope_product_id", "effective_at",
                    "physical_responsible", "deleted_at", "restore_until", "permanently_deleted_at",
                )
            ) {
                attachRegisteredDevice()
                filter { exact("permanently_deleted_at", null) }
            }
            .decodeList<TrashConferenceRow>()
            .filter { it.deletedAt != null }

        val supplierNames = loadNames("suppliers", entries.map { it.supplierId })
        val categoryNames = loadNames("categories", conferences.mapNotNull { it.categoryId })
        val productNames = loadNames("products", conferences.mapNotNull { it.scopeProductId })

        return buildRestorableTrash(
            TrashSourceRows(
                products = products,
                categories = categories,
                suppliers = suppliers,
                entries = entries,
                conferences = conferences,
                supplierNames = supplierNames,
                categoryNames = categoryNames,
                productNames = productNames,
            )
        )
    }

    /** Nomes históricos: inclui cadastros excluídos, para não perder a identificação do item. */
    private suspend fun loadNames(table: String, ids: List<String>): Map<String, String> {
        val wanted = ids.toSet()
        if (wanted.isEmpty()) return emptyMap()
        return client().from(table)
            .select(Columns.list("id", "name")) { attachRegisteredDevice() }
            .decodeList<TrashNameRow>()
            .filter { it.id in wanted }
            .associate { it.id to it.name }
    }

    suspend fun restore(item: TrashItem) {
        val client = client()
        when (item.type) {
            TrashItemType.Product -> client.postgrest.rpc(
                function = "restore_product",
                parameters = buildJsonObject { put("p_product_id", item.id) },
            ) { attachRegisteredDevice() }

            TrashItemType.Category -> client.from("categories").update(
                buildJsonObject { put("deleted_at", JsonNull) }
            ) {
                attachRegisteredDevice()
                filter {
                    eq("id", item.id)
                    exact("permanently_deleted_at", null)
                }
            }

            TrashItemType.Supplier -> client.postgrest.rpc(
                function = "restore_supplier",
                parameters = buildJsonObject { put("p_supplier_id", item.id) },
            ) { attachRegisteredDevice() }

            TrashItemType.Entry -> {
                val deviceId = requireDeviceId()
                client.postgrest.rpc(
                    function = "restore_entry",
                    parameters = buildJsonObject {
                        put("p_entry_id", item.id)
                        put("p_device_id", deviceId)
                    },
                ) { attachRegisteredDevice() }
            }

            TrashItemType.Conference -> {
                val deviceId = requireDeviceId()
                client.postgrest.rpc(
                    function = "restore_conference",
                    parameters = buildJsonObject {
                        put("p_conference_id", item.id)
                        put("p_device_id", deviceId)
                    },
                ) { attachRegisteredDevice() }
            }
        }
    }

    suspend fun permanentlyDelete(item: TrashItem) {
        val deviceId = requireDeviceId()
        client().postgrest.rpc(
            function = "permanently_delete_trash_item",
            parameters = buildJsonObject {
                put("p_item_type", item.type.rpcValue)
                put("p_item_id", item.id)
                put("p_device_id", deviceId)
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun emptyTrash(): EmptyTrashResult {
        val deviceId = requireDeviceId()
        val result = client().postgrest.rpc(
            function = "empty_trash",
            parameters = buildJsonObject { put("p_device_id", deviceId) },
        ) { attachRegisteredDevice() }.decodeAs<JsonObject>()
        return parseEmptyTrashResult(result)
    }
}

internal fun parseEmptyTrashResult(result: JsonObject?): EmptyTrashResult {
    fun count(key: String): Int = result?.get(key)
        ?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
        ?: 0
    return EmptyTrashResult(
        products = count("products"),
        categories = count("categories"),
        suppliers = count("suppliers"),
        entries = count("entries"),
        conferences = count("conferences"),
        total = count("total"),
    )
}

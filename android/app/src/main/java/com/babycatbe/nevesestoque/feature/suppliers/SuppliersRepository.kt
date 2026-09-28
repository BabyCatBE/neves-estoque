package com.babycatbe.nevesestoque.feature.suppliers

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SuppliersRepository {
    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    suspend fun loadActiveSuppliers(): List<SupplierDetails> =
        client().from("suppliers")
            .select(
                Columns.list(
                    "id",
                    "name",
                    "company",
                    "phone",
                    "observation",
                    "purchase_frequency_days",
                    "preferred_order_weekday",
                    "average_delivery_days",
                    "safety_margin_days",
                    "deleted_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<SupplierRow>()
            .filter { it.deletedAt == null }
            .map { it.toDetails() }
            .sortedBy { normalizeSupplierSearchText(it.name) }

    suspend fun loadSupplier(supplierId: String): SupplierDetails =
        client().from("suppliers")
            .select(
                Columns.list(
                    "id",
                    "name",
                    "company",
                    "phone",
                    "observation",
                    "purchase_frequency_days",
                    "preferred_order_weekday",
                    "average_delivery_days",
                    "safety_margin_days",
                    "deleted_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<SupplierRow>()
            .firstOrNull { it.id == supplierId && it.deletedAt == null }
            ?.toDetails()
            ?: error("Fornecedor não encontrado.")

    suspend fun createSupplier(input: SupplierMutationInput): String =
        client().postgrest.rpc(
            function = "create_supplier",
            parameters = supplierParameters(input),
        ) { attachRegisteredDevice() }.decodeAs()

    suspend fun updateSupplier(supplierId: String, input: SupplierMutationInput) {
        val params = supplierParameters(input).toMutableMap()
        params["p_supplier_id"] = JsonPrimitive(supplierId)
        client().postgrest.rpc(
            function = "update_supplier",
            parameters = buildJsonObject {
                params.forEach { (key, value) -> put(key, value) }
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun softDeleteSupplier(supplierId: String) {
        client().postgrest.rpc(
            function = "soft_delete_supplier",
            parameters = buildJsonObject { put("p_supplier_id", supplierId) },
        ) { attachRegisteredDevice() }
    }

    suspend fun isSupplierActive(supplierId: String): Boolean =
        loadActiveSuppliers().any { it.id == supplierId }

    private fun supplierParameters(input: SupplierMutationInput) = buildJsonObject {
        put("p_name", input.name)
        put("p_company", input.company)
        put("p_phone", input.phone)
        put("p_observation", input.observation?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "p_purchase_frequency_days",
            input.purchaseFrequencyDays?.let(::JsonPrimitive) ?: JsonNull,
        )
        put(
            "p_preferred_order_weekday",
            input.preferredOrderWeekday?.let(::JsonPrimitive) ?: JsonNull,
        )
        put(
            "p_average_delivery_days",
            input.averageDeliveryDays?.let(::JsonPrimitive) ?: JsonNull,
        )
        put(
            "p_safety_margin_days",
            input.safetyMarginDays?.let(::JsonPrimitive) ?: JsonNull,
        )
    }
}

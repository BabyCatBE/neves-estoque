package com.babycatbe.nevesestoque.feature.entries

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntryRow(
    val id: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("effective_at") val effectiveAt: String,
    val observation: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class EntryLookupRow(
    val id: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class EntryItemRow(
    val id: String,
    @SerialName("entry_id") val entryId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double? = null,
    val position: Int,
)

@Serializable
data class EntrySupplierRow(
    val id: String,
    val name: String,
    val company: String? = null,
    val phone: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class EntryProductRow(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    val unit: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class EntryCategoryRow(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

data class EntrySupplierOption(
    val id: String,
    val name: String,
    val company: String?,
    val phone: String?,
    val isPending: Boolean,
)

data class EntryProductOption(
    val id: String,
    val name: String,
    val categoryId: String?,
    val unit: String,
    val sortOrder: Int?,
    val createdAt: String,
)

data class EntryCategoryOption(
    val id: String,
    val name: String,
    val sortOrder: Int?,
)

data class EntryFormOptions(
    val suppliers: List<EntrySupplierOption>,
    val products: List<EntryProductOption>,
    val categories: List<EntryCategoryOption>,
)

data class EntryDraftSupplier(val name: String)

data class EntryDraftProduct(
    val clientId: String,
    val name: String,
    val unit: String,
    val categoryId: String?,
)

data class EntryCreateItem(
    val productId: String? = null,
    val newProduct: EntryDraftProduct? = null,
    val quantity: Double,
    val unitPrice: Double?,
)

data class EntryCreateInput(
    val supplierId: String?,
    val newSupplier: EntryDraftSupplier?,
    val effectiveAt: String,
    val idempotencyKey: String,
    val observation: String?,
    val items: List<EntryCreateItem>,
)

data class EntryUpdateItem(
    val productId: String,
    val quantity: Double,
    val unitPrice: Double?,
)

data class EntryUpdateInput(
    val entryId: String,
    val supplierId: String,
    val effectiveAt: String,
    val observation: String?,
    val items: List<EntryUpdateItem>,
)

data class EntryHistoryItem(
    val id: String,
    val effectiveAt: String,
    val supplierName: String,
    val observation: String?,
    val totalKnown: Double,
    val hasMissingPrice: Boolean,
    val productNames: List<String>,
)

data class EntryDetailItem(
    val id: String,
    val productId: String,
    val productName: String,
    val unit: String,
    val quantity: Double,
    val unitPrice: Double?,
    val position: Int,
)

data class EntryDetails(
    val id: String,
    val effectiveAt: String,
    val supplierId: String,
    val supplierName: String,
    val supplierCompany: String?,
    val observation: String?,
    val totalKnown: Double,
    val hasMissingPrice: Boolean,
    val items: List<EntryDetailItem>,
)

data class EntryEditData(
    val entry: EntryDetails,
    val options: EntryFormOptions,
)

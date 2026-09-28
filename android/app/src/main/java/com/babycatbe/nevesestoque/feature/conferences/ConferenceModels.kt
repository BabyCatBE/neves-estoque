package com.babycatbe.nevesestoque.feature.conferences

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConferenceCategoryRow(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("illustration_source") val illustrationSource: String? = null,
    @SerialName("illustration_key") val illustrationKey: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ConferenceProductRow(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    val unit: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ConferenceRow(
    val id: String,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("physical_responsible") val physicalResponsible: String,
    val observation: String? = null,
    @SerialName("scope_type") val scopeType: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("scope_product_id") val scopeProductId: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ConferenceLookupRow(
    val id: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ConferenceItemRow(
    val id: String,
    @SerialName("conference_id") val conferenceId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
    val position: Int,
)

data class ConferenceProduct(
    val id: String,
    val name: String,
    val unit: String,
    val sortOrder: Int?,
)

data class ConferenceCategorySummary(
    val id: String,
    val name: String,
    val sortOrder: Int?,
    val productCount: Int,
    val lastConferenceAt: String?,
    val conferredToday: Boolean,
)

data class CategoryConferenceSetup(
    val categoryId: String,
    val categoryName: String,
    val products: List<ConferenceProduct>,
)

data class ConferenceHistoryItem(
    val id: String,
    val categoryId: String,
    val effectiveAt: String,
    val createdAt: String,
    val physicalResponsible: String,
    val observation: String?,
)

data class ConferenceDetailItem(
    val id: String,
    val productId: String,
    val productName: String,
    val unit: String,
    val quantity: Double,
    val position: Int,
)

data class ConferenceDetails(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val effectiveAt: String,
    val createdAt: String,
    val physicalResponsible: String,
    val observation: String?,
    val items: List<ConferenceDetailItem>,
)

data class CategoryConferenceWriteItem(
    val productId: String,
    val quantity: Double,
)

data class CategoryConferenceWriteInput(
    val categoryId: String,
    val effectiveAt: String,
    val physicalResponsible: String,
    val deviceId: String,
    val idempotencyKey: String,
    val observation: String?,
    val items: List<CategoryConferenceWriteItem>,
)

data class CategoryConferenceUpdateInput(
    val conferenceId: String,
    val effectiveAt: String,
    val physicalResponsible: String,
    val deviceId: String,
    val observation: String?,
    val items: List<CategoryConferenceWriteItem>,
)

data class ConferencePrintCategory(
    val id: String,
    val name: String,
    val illustrationSource: String?,
    val illustrationKey: String?,
    val products: List<ConferenceProduct>,
)

data class ConferencePrintData(
    val categories: List<ConferencePrintCategory>,
    val pendingProductCount: Int,
)

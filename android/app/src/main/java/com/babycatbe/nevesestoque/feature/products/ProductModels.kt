package com.babycatbe.nevesestoque.feature.products

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductCategoryRow(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("illustration_source") val illustrationSource: String? = null,
    @SerialName("illustration_key") val illustrationKey: String? = null,
    @SerialName("illustration_position_x") val illustrationPositionX: Int = 50,
    @SerialName("illustration_position_y") val illustrationPositionY: Int = 50,
)

@Serializable
data class ProductRow(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String? = null,
    val unit: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("initial_stock_quantity") val initialStockQuantity: Double? = null,
    @SerialName("initial_stock_at") val initialStockAt: String? = null,
    @SerialName("initial_price") val initialPrice: Double? = null,
    @SerialName("initial_price_at") val initialPriceAt: String? = null,
)

@Serializable
data class ProductStockRow(
    @SerialName("product_id") val productId: String,
    @SerialName("current_quantity") val currentQuantity: Double? = null,
    @SerialName("current_price") val currentPrice: Double? = null,
    @SerialName("current_value") val currentValue: Double? = null,
    @SerialName("stock_requires_conference") val stockRequiresConference: Boolean = false,
)

@Serializable
data class ProductEntryItemRow(
    val id: String,
    @SerialName("entry_id") val entryId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
    @SerialName("unit_price") val unitPrice: Double? = null,
    val position: Int,
)

@Serializable
data class ProductEntryRow(
    val id: String,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ProductSupplierRow(
    val id: String,
    val name: String,
)

@Serializable
data class ProductConferenceItemRow(
    @SerialName("conference_id") val conferenceId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
    /** Chave do cursor de paginação; leituras que não selecionam `id` continuam válidas. */
    val id: String = "",
)

@Serializable
data class ProductConferenceRow(
    val id: String,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

data class ProductListItem(
    val id: String,
    val name: String,
    val categoryId: String?,
    val unit: String,
    val sortOrder: Int?,
    val createdAt: String,
    val currentQuantity: Double?,
    val currentPrice: Double?,
    val stockRequiresConference: Boolean,
)

data class ProductPriceHistoryItem(
    val id: String,
    val entryId: String,
    val effectiveAt: String,
    val supplierName: String,
    val quantity: Double,
    val unitPrice: Double?,
    val position: Int,
)

enum class UsageStatus { Ready, Insufficient }

enum class UsageReason {
    NeedsTwoConferences,
    InvalidInterval,
    NegativeConsumption,
    NeedsMoreHistory,
}

data class ProductUsageInsights(
    val status: UsageStatus,
    val reason: UsageReason?,
    val conferencesUsed: Int,
    val validIntervals: Int,
    val ignoredNegativeIntervals: Int,
    val historyDays: Double?,
    val intervalStart: String?,
    val intervalEnd: String?,
    val entriesDuringInterval: Double?,
    val estimatedConsumption: Double?,
    val dailyAverage: Double?,
    val weeklyAverage: Double?,
    val coverageDays: Double?,
)

data class ProductDetails(
    val id: String,
    val name: String,
    val categoryId: String?,
    val unit: String,
    val createdAt: String,
    val initialStockQuantity: Double?,
    val initialStockAt: String?,
    val initialPrice: Double?,
    val initialPriceAt: String?,
    val currentQuantity: Double?,
    val currentPrice: Double?,
    val currentValue: Double?,
    val stockRequiresConference: Boolean,
    val priceHistory: List<ProductPriceHistoryItem>,
    val usageInsights: ProductUsageInsights,
)

data class ProductCatalogData(
    val categories: List<ProductCategoryRow>,
    val products: List<ProductListItem>,
)

data class InitialPriceUpdate(val value: Double?)

data class ProductMutationInput(
    val name: String,
    val categoryId: String,
    val unit: String,
    val initialStockQuantity: Double?,
    val initialPrice: Double?,
)

@Serializable
data class CreateCategoryPayload(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("illustration_source") val illustrationSource: String? = null,
    @SerialName("illustration_key") val illustrationKey: String? = null,
    @SerialName("illustration_position_x") val illustrationPositionX: Int = 50,
    @SerialName("illustration_position_y") val illustrationPositionY: Int = 50,
)

@Serializable
data class CategoryNamePayload(val name: String)

@Serializable
data class ProductTrashRow(
    val id: String,
    val name: String,
    val unit: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("restore_until") val restoreUntil: String? = null,
    @SerialName("permanently_deleted_at") val permanentlyDeletedAt: String? = null,
)

@Serializable
data class CategoryTrashRow(
    val id: String,
    val name: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("restore_until") val restoreUntil: String? = null,
    @SerialName("permanently_deleted_at") val permanentlyDeletedAt: String? = null,
)

data class CategoryListItem(
    val id: String,
    val name: String,
    val sortOrder: Int?,
    val productCount: Int,
    val illustrationSource: String?,
    val illustrationKey: String?,
    val illustrationPositionX: Int = 50,
    val illustrationPositionY: Int = 50,
    val illustrationBytes: ByteArray? = null,
)

data class CategoryMutationInput(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val illustrationSource: String?,
    val illustrationKey: String?,
    val illustrationPositionX: Int,
    val illustrationPositionY: Int,
)

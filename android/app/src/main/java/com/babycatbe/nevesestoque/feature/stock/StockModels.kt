package com.babycatbe.nevesestoque.feature.stock

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StockCategoryRow(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class StockSupplierRow(
    val id: String,
    val name: String,
)

@Serializable
data class CurrentStockRow(
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("current_supplier_id") val currentSupplierId: String? = null,
    val unit: String,
    @SerialName("current_quantity") val currentQuantity: Double? = null,
    @SerialName("current_price") val currentPrice: Double? = null,
    @SerialName("current_value") val currentValue: Double? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("stock_requires_conference") val stockRequiresConference: Boolean = false,
)

data class CurrentStockData(
    val categories: List<StockCategoryRow>,
    val suppliers: List<StockSupplierRow>,
    val items: List<CurrentStockRow>,
)

enum class StockViewMode {
    Category,
    Supplier,
    Alphabetical,
}

data class StockGroup(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val items: List<CurrentStockRow>,
    val accent: StockGroupAccent = StockGroupAccent.Primary,
)

enum class StockGroupAccent {
    Primary,
    Warning,
    Neutral,
}

data class StockValueSummary(
    val totalKnown: Double,
    val hasMissingPrice: Boolean,
)

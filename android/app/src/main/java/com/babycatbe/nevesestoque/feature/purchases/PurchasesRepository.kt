package com.babycatbe.nevesestoque.feature.purchases

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.feature.products.UsageConferencePoint
import com.babycatbe.nevesestoque.feature.products.UsageEntryPoint
import com.babycatbe.nevesestoque.feature.products.UsageStatus
import com.babycatbe.nevesestoque.feature.products.calculateProductUsageInsights
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime

@Serializable
private data class PurchaseProductRow(
    val id: String,
    val name: String,
    val unit: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

@Serializable
private data class PurchaseStockRow(
    @SerialName("product_id") val productId: String,
    @SerialName("current_quantity") val currentQuantity: Double? = null,
    @SerialName("current_supplier_id") val currentSupplierId: String? = null,
)

@Serializable
data class PurchaseSupplierRow(
    val id: String,
    val name: String,
    val company: String? = null,
    val phone: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("purchase_frequency_days") val purchaseFrequencyDays: Int? = null,
    @SerialName("preferred_order_weekday") val preferredOrderWeekday: Int? = null,
    @SerialName("average_delivery_days") val averageDeliveryDays: Int? = null,
    @SerialName("safety_margin_days") val safetyMarginDays: Int? = null,
) {
    val configurationReady: Boolean
        get() = purchaseFrequencyDays != null && averageDeliveryDays != null && safetyMarginDays != null
}

@Serializable
data class PurchaseCategoryRow(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

@Serializable
private data class PurchaseConferenceRow(
    val id: String,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class PurchaseConferenceItemRow(
    @SerialName("conference_id") val conferenceId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
)

@Serializable
private data class PurchaseEntryRow(
    val id: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("effective_at") val effectiveAt: String,
)

@Serializable
private data class PurchaseEntryItemRow(
    @SerialName("entry_id") val entryId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
)

data class PurchaseData(
    val products: List<PurchaseProduct>,
    val suppliers: List<PurchaseSupplierRow>,
    val categories: List<PurchaseCategoryRow>,
)

/**
 * Mesmas leituras da Web (src/features/purchases/api/purchases.ts). Somente leitura.
 * O cálculo usa o consumo ponderado já portado em ProductUsage e a projeção de PurchasesLogic.
 */
class PurchasesRepository {
    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    suspend fun loadPurchaseData(nowMillis: Long = System.currentTimeMillis()): PurchaseData {
        val client = client()

        val products = client.from("products")
            .select(Columns.list("id", "name", "unit", "category_id", "sort_order")) {
                attachRegisteredDevice()
                filter { exact("deleted_at", null) }
            }
            .decodeList<PurchaseProductRow>()

        val stock = client.from("stock_current")
            .select(Columns.list("product_id", "current_quantity", "current_supplier_id")) { attachRegisteredDevice() }
            .decodeList<PurchaseStockRow>()
            .associateBy { it.productId }

        val suppliers = client.from("suppliers")
            .select(
                Columns.list(
                    "id", "name", "company", "phone", "deleted_at", "purchase_frequency_days",
                    "preferred_order_weekday", "average_delivery_days", "safety_margin_days",
                )
            ) { attachRegisteredDevice() }
            .decodeList<PurchaseSupplierRow>()
        val supplierById = suppliers.associateBy { it.id }

        val categories = client.from("categories")
            .select(Columns.list("id", "name", "sort_order")) {
                attachRegisteredDevice()
                filter { exact("deleted_at", null) }
            }
            .decodeList<PurchaseCategoryRow>()
            .sortedWith(compareBy<PurchaseCategoryRow> { it.sortOrder ?: Int.MAX_VALUE }.thenBy { it.name })

        fun reached(value: String): Boolean =
            runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() <= nowMillis }.getOrDefault(false)

        val conferenceById = client.from("conferences")
            .select(Columns.list("id", "effective_at", "created_at")) {
                attachRegisteredDevice()
                filter { exact("deleted_at", null) }
            }
            .decodeList<PurchaseConferenceRow>()
            .filter { reached(it.effectiveAt) }
            .associateBy { it.id }

        val conferenceItems = client.from("conference_items")
            .select(Columns.list("conference_id", "product_id", "quantity")) { attachRegisteredDevice() }
            .decodeList<PurchaseConferenceItemRow>()

        val entryById = client.from("entries")
            .select(Columns.list("id", "supplier_id", "effective_at")) {
                attachRegisteredDevice()
                filter { exact("deleted_at", null) }
            }
            .decodeList<PurchaseEntryRow>()
            .filter { reached(it.effectiveAt) }
            .associateBy { it.id }

        val entryItems = client.from("entry_items")
            .select(Columns.list("entry_id", "product_id", "quantity")) { attachRegisteredDevice() }
            .decodeList<PurchaseEntryItemRow>()

        val conferencesByProduct = conferenceItems.mapNotNull { item ->
            val conference = conferenceById[item.conferenceId] ?: return@mapNotNull null
            item.productId to UsageConferencePoint(conference.effectiveAt, conference.createdAt, item.quantity)
        }.groupBy({ it.first }, { it.second })

        val entriesByProduct = mutableMapOf<String, MutableList<UsageEntryPoint>>()
        val historicalSuppliers = mutableMapOf<String, MutableSet<String>>()
        entryItems.forEach { item ->
            val entry = entryById[item.entryId] ?: return@forEach
            entriesByProduct.getOrPut(item.productId) { mutableListOf() } += UsageEntryPoint(entry.effectiveAt, item.quantity)
            historicalSuppliers.getOrPut(item.productId) { mutableSetOf() } += entry.supplierId
        }

        val weekday = todayWeekday(LocalDate.now().dayOfWeek)

        val purchaseProducts = products.map { product ->
            val stockRow = stock[product.id]
            val currentQuantity = stockRow?.currentQuantity
            val currentSupplierId = stockRow?.currentSupplierId
            val supplier = currentSupplierId?.let(supplierById::get)
            val usage = calculateProductUsageInsights(
                conferences = conferencesByProduct[product.id].orEmpty(),
                entries = entriesByProduct[product.id].orEmpty(),
                currentQuantity = currentQuantity,
                referenceTimeMillis = nowMillis,
            )
            val projection = calculatePurchaseProjection(
                PurchaseProjectionInput(
                    usageReady = usage.status == UsageStatus.Ready,
                    dailyAverage = usage.dailyAverage,
                    currentQuantity = currentQuantity,
                    unit = product.unit,
                    hasSupplier = currentSupplierId != null,
                    supplierActive = supplier != null && supplier.deletedAt == null,
                    purchaseFrequencyDays = supplier?.purchaseFrequencyDays,
                    preferredOrderWeekday = supplier?.preferredOrderWeekday,
                    deliveryDays = supplier?.averageDeliveryDays,
                    safetyMarginDays = supplier?.safetyMarginDays,
                    todayWeekday = weekday,
                )
            )
            PurchaseProduct(
                productId = product.id,
                productName = product.name,
                unit = product.unit,
                categoryId = product.categoryId,
                sortOrder = product.sortOrder,
                currentQuantity = currentQuantity,
                currentSupplierId = currentSupplierId,
                historicalSupplierIds = historicalSuppliers[product.id].orEmpty(),
                usageInsights = usage,
                projection = projection,
            )
        }

        return PurchaseData(
            products = purchaseProducts,
            suppliers = suppliers.filter { it.deletedAt == null }
                .sortedWith { a, b -> comparePurchaseNames(a.name, b.name) },
            categories = categories,
        )
    }
}

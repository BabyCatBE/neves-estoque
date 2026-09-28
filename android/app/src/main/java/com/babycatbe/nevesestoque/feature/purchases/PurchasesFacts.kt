package com.babycatbe.nevesestoque.feature.purchases

import com.babycatbe.nevesestoque.feature.products.UsageConferencePoint
import com.babycatbe.nevesestoque.feature.products.UsageEntryPoint
import com.babycatbe.nevesestoque.feature.products.UsageStatus
import com.babycatbe.nevesestoque.feature.products.calculateProductUsageInsights
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
internal data class PurchaseProductRow(
    val id: String,
    val name: String,
    val unit: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

@Serializable
internal data class PurchaseStockRow(
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
internal data class PurchaseConferenceRow(
    val id: String,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class PurchaseConferenceItemRow(
    val id: String,
    @SerialName("conference_id") val conferenceId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
)

@Serializable
internal data class PurchaseEntryRow(
    val id: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("effective_at") val effectiveAt: String,
)

@Serializable
internal data class PurchaseEntryItemRow(
    val id: String,
    @SerialName("entry_id") val entryId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
)

/** Fatos lidos do Supabase para Compras. Os quatro históricos devem vir COMPLETOS. */
internal data class PurchaseFacts(
    val products: List<PurchaseProductRow>,
    val stock: List<PurchaseStockRow>,
    val suppliers: List<PurchaseSupplierRow>,
    val conferences: List<PurchaseConferenceRow>,
    val conferenceItems: List<PurchaseConferenceItemRow>,
    val entries: List<PurchaseEntryRow>,
    val entryItems: List<PurchaseEntryItemRow>,
)

/**
 * Cálculo puro de Compras a partir dos fatos (sem acesso à rede). Mesma regra de antes
 * (e da Web): conferências e Entradas só contam quando a data efetiva já chegou; o
 * consumo usa ProductUsage e a projeção usa PurchasesLogic.
 */
internal fun assemblePurchaseProducts(
    facts: PurchaseFacts,
    nowMillis: Long,
    weekday: Int,
): List<PurchaseProduct> {
    fun reached(value: String): Boolean =
        runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() <= nowMillis }.getOrDefault(false)

    val stock = facts.stock.associateBy { it.productId }
    val supplierById = facts.suppliers.associateBy { it.id }
    val conferenceById = facts.conferences.filter { reached(it.effectiveAt) }.associateBy { it.id }
    val entryById = facts.entries.filter { reached(it.effectiveAt) }.associateBy { it.id }

    val conferencesByProduct = facts.conferenceItems.mapNotNull { item ->
        val conference = conferenceById[item.conferenceId] ?: return@mapNotNull null
        item.productId to UsageConferencePoint(conference.effectiveAt, conference.createdAt, item.quantity)
    }.groupBy({ it.first }, { it.second })

    val entriesByProduct = mutableMapOf<String, MutableList<UsageEntryPoint>>()
    val historicalSuppliers = mutableMapOf<String, MutableSet<String>>()
    facts.entryItems.forEach { item ->
        val entry = entryById[item.entryId] ?: return@forEach
        entriesByProduct.getOrPut(item.productId) { mutableListOf() } += UsageEntryPoint(entry.effectiveAt, item.quantity)
        historicalSuppliers.getOrPut(item.productId) { mutableSetOf() } += entry.supplierId
    }

    return facts.products.map { product ->
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
}

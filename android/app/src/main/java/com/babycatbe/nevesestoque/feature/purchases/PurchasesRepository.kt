package com.babycatbe.nevesestoque.feature.purchases

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.data.supabase.fetchAllByIdKeyset
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class PurchaseCategoryRow(
    val id: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

data class PurchaseData(
    val products: List<PurchaseProduct>,
    val suppliers: List<PurchaseSupplierRow>,
    val categories: List<PurchaseCategoryRow>,
)

/**
 * Mesmas leituras da Web (src/features/purchases/api/purchases.ts). Somente leitura.
 * Os quatro históricos (conferences, conference_items, entries, entry_items) são lidos por
 * completo com cursor por id (data/supabase/KeysetPagination.kt); o cálculo fica em
 * assemblePurchaseProducts (PurchasesFacts.kt).
 */
class PurchasesRepository {
    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    suspend fun loadPurchaseData(nowMillis: Long = System.currentTimeMillis()): PurchaseData = coroutineScope {
        val client = client()

        val productsAsync = async {
            client.from("products")
                .select(Columns.list("id", "name", "unit", "category_id", "sort_order")) {
                    attachRegisteredDevice()
                    filter { exact("deleted_at", null) }
                }
                .decodeList<PurchaseProductRow>()
        }

        val stockAsync = async {
            client.from("stock_current")
                .select(Columns.list("product_id", "current_quantity", "current_supplier_id")) { attachRegisteredDevice() }
                .decodeList<PurchaseStockRow>()
        }

        val suppliersAsync = async {
            client.from("suppliers")
                .select(
                    Columns.list(
                        "id", "name", "company", "phone", "deleted_at", "purchase_frequency_days",
                        "preferred_order_weekday", "average_delivery_days", "safety_margin_days",
                    )
                ) { attachRegisteredDevice() }
                .decodeList<PurchaseSupplierRow>()
        }

        val categoriesAsync = async {
            client.from("categories")
                .select(Columns.list("id", "name", "sort_order")) {
                    attachRegisteredDevice()
                    filter { exact("deleted_at", null) }
                }
                .decodeList<PurchaseCategoryRow>()
                .sortedWith(compareBy<PurchaseCategoryRow> { it.sortOrder ?: Int.MAX_VALUE }.thenBy { it.name })
        }

        val products = productsAsync.await()
        val stock = stockAsync.await()
        val suppliers = suppliersAsync.await()
        val categories = categoriesAsync.await()

        val facts = coroutineScope {
            val conferences = async {
                fetchAllByIdKeyset(idOf = PurchaseConferenceRow::id) { afterId, pageLimit ->
                    client.from("conferences")
                        .select(Columns.list("id", "effective_at", "created_at")) {
                            attachRegisteredDevice()
                            filter {
                                exact("deleted_at", null)
                                if (afterId != null) gt("id", afterId)
                            }
                            order("id", Order.ASCENDING)
                            limit(pageLimit)
                        }
                        .decodeList<PurchaseConferenceRow>()
                }
            }
            val conferenceItems = async {
                fetchAllByIdKeyset(idOf = PurchaseConferenceItemRow::id) { afterId, pageLimit ->
                    client.from("conference_items")
                        .select(Columns.list("id", "conference_id", "product_id", "quantity")) {
                            attachRegisteredDevice()
                            if (afterId != null) filter { gt("id", afterId) }
                            order("id", Order.ASCENDING)
                            limit(pageLimit)
                        }
                        .decodeList<PurchaseConferenceItemRow>()
                }
            }
            val entries = async {
                fetchAllByIdKeyset(idOf = PurchaseEntryRow::id) { afterId, pageLimit ->
                    client.from("entries")
                        .select(Columns.list("id", "supplier_id", "effective_at")) {
                            attachRegisteredDevice()
                            filter {
                                exact("deleted_at", null)
                                if (afterId != null) gt("id", afterId)
                            }
                            order("id", Order.ASCENDING)
                            limit(pageLimit)
                        }
                        .decodeList<PurchaseEntryRow>()
                }
            }
            val entryItems = async {
                fetchAllByIdKeyset(idOf = PurchaseEntryItemRow::id) { afterId, pageLimit ->
                    client.from("entry_items")
                        .select(Columns.list("id", "entry_id", "product_id", "quantity")) {
                            attachRegisteredDevice()
                            if (afterId != null) filter { gt("id", afterId) }
                            order("id", Order.ASCENDING)
                            limit(pageLimit)
                        }
                        .decodeList<PurchaseEntryItemRow>()
                }
            }
            PurchaseFacts(
                products = products,
                stock = stock,
                suppliers = suppliers,
                conferences = conferences.await(),
                conferenceItems = conferenceItems.await(),
                entries = entries.await(),
                entryItems = entryItems.await(),
            )
        }

        // Projeções de todos os Produtos: cálculo fora da thread da interface.
        val purchaseProducts = withContext(Dispatchers.Default) {
            assemblePurchaseProducts(
                facts = facts,
                nowMillis = nowMillis,
                weekday = todayWeekday(LocalDate.now().dayOfWeek),
            )
        }

        PurchaseData(
            products = purchaseProducts,
            suppliers = suppliers.filter { it.deletedAt == null }
                .sortedWith { a, b -> comparePurchaseNames(a.name, b.name) },
            categories = categories,
        )
    }
}

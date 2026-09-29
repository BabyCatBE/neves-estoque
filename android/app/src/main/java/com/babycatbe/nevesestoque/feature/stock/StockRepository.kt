package com.babycatbe.nevesestoque.feature.stock

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import com.babycatbe.nevesestoque.data.offline.offlineCachedList
import com.babycatbe.nevesestoque.data.offline.readCachedList
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class StockRepository {
    suspend fun loadCurrentStock(): CurrentStockData = coroutineScope {
        val client = SupabaseProvider.client
            ?: error("Supabase não está configurado nesta build.")

        val categoriesAsync = async {
            offlineCachedList<StockCategoryRow>(CATEGORIES_KEY) {
                client.from("categories")
                .select(Columns.list("id", "name", "sort_order", "deleted_at")) {
                    attachRegisteredDevice()
                }
                .decodeList<StockCategoryRow>()
            }
        }

        val suppliersAsync = async {
            offlineCachedList<StockSupplierRow>(SUPPLIERS_KEY) {
                client.from("suppliers")
                .select(Columns.list("id", "name")) {
                    attachRegisteredDevice()
                }
                .decodeList<StockSupplierRow>()
            }
        }

        val itemsAsync = async {
            offlineCachedList<CurrentStockRow>(ITEMS_KEY) {
                client.from("stock_current")
                .select(
                    Columns.list(
                        "product_id",
                        "product_name",
                        "category_id",
                        "current_supplier_id",
                        "unit",
                        "current_quantity",
                        "current_price",
                        "current_value",
                        "sort_order",
                        "stock_requires_conference",
                    )
                ) {
                    attachRegisteredDevice()
                }
                .decodeList<CurrentStockRow>()
            }
        }

        assembleCurrentStock(
            categories = categoriesAsync.await(),
            suppliers = suppliersAsync.await(),
            items = itemsAsync.await(),
        )
    }

    /**
     * Estoque Atual montado somente da cópia local deste aparelho (sem rede), para exibir na hora
     * enquanto a leitura oficial atualiza em segundo plano. Null quando alguma parte não existe.
     */
    suspend fun loadCurrentStockSnapshot(): CurrentStockData? {
        val categories = readCachedList<StockCategoryRow>(CATEGORIES_KEY) ?: return null
        val suppliers = readCachedList<StockSupplierRow>(SUPPLIERS_KEY) ?: return null
        val items = readCachedList<CurrentStockRow>(ITEMS_KEY) ?: return null
        return assembleCurrentStock(categories, suppliers, items)
    }

    private companion object {
        const val CATEGORIES_KEY = "stock-categories"
        const val SUPPLIERS_KEY = "stock-suppliers"
        const val ITEMS_KEY = "stock-stock_current"
    }
}

internal fun assembleCurrentStock(
    categories: List<StockCategoryRow>,
    suppliers: List<StockSupplierRow>,
    items: List<CurrentStockRow>,
): CurrentStockData = CurrentStockData(
    categories = categories.filter { it.deletedAt == null },
    suppliers = suppliers,
    items = items.filter { it.productId.isNotBlank() && it.productName.isNotBlank() && it.unit.isNotBlank() },
)

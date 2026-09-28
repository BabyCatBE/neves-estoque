package com.babycatbe.nevesestoque.feature.stock

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class StockRepository {
    suspend fun loadCurrentStock(): CurrentStockData {
        val client = SupabaseProvider.client
            ?: error("Supabase não está configurado nesta build.")

        val categories = client.from("categories")
            .select(Columns.list("id", "name", "sort_order", "deleted_at")) {
                attachRegisteredDevice()
            }
            .decodeList<StockCategoryRow>()
            .filter { it.deletedAt == null }

        val suppliers = client.from("suppliers")
            .select(Columns.list("id", "name")) {
                attachRegisteredDevice()
            }
            .decodeList<StockSupplierRow>()

        val items = client.from("stock_current")
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
            .filter { it.productId.isNotBlank() && it.productName.isNotBlank() && it.unit.isNotBlank() }

        return CurrentStockData(
            categories = categories,
            suppliers = suppliers,
            items = items,
        )
    }
}

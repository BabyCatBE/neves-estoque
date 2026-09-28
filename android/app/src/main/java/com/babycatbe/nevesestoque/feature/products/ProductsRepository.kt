package com.babycatbe.nevesestoque.feature.products

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class ProductsRepository {
    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    suspend fun loadCatalog(): ProductCatalogData {
        val client = client()

        val categories = client.from("categories")
            .select(
                Columns.list(
                    "id", "name", "sort_order", "deleted_at",
                    "illustration_source", "illustration_key",
                )
            ) { attachRegisteredDevice() }
            .decodeList<ProductCategoryRow>()
            .filter { it.deletedAt == null }

        val productRows = client.from("products")
            .select(
                Columns.list(
                    "id", "name", "category_id", "unit", "sort_order",
                    "created_at", "deleted_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<ProductRow>()
            .filter { it.deletedAt == null }

        val stocks = client.from("stock_current")
            .select(
                Columns.list(
                    "product_id", "current_quantity", "current_price",
                    "current_value", "stock_requires_conference",
                )
            ) { attachRegisteredDevice() }
            .decodeList<ProductStockRow>()
            .associateBy { it.productId }

        return ProductCatalogData(
            categories = categories,
            products = productRows.map { row ->
                val stock = stocks[row.id]
                ProductListItem(
                    id = row.id,
                    name = row.name,
                    categoryId = row.categoryId,
                    unit = row.unit,
                    sortOrder = row.sortOrder,
                    createdAt = row.createdAt,
                    currentQuantity = stock?.currentQuantity,
                    currentPrice = stock?.currentPrice,
                    stockRequiresConference = stock?.stockRequiresConference ?: false,
                )
            },
        )
    }

    suspend fun loadCategories(): List<CategoryListItem> {
        val catalog = loadCatalog()
        val counts = catalog.products
            .mapNotNull { it.categoryId }
            .groupingBy { it }
            .eachCount()

        return catalog.categories.map { category ->
            CategoryListItem(
                id = category.id,
                name = category.name,
                sortOrder = category.sortOrder,
                productCount = counts[category.id] ?: 0,
                illustrationSource = category.illustrationSource,
                illustrationKey = category.illustrationKey,
            )
        }
    }

    suspend fun loadProductDetails(productId: String): Pair<ProductDetails, List<ProductCategoryRow>> {
        val client = client()
        val catalog = loadCatalog()

        val product = client.from("products")
            .select(
                Columns.list(
                    "id", "name", "category_id", "unit", "sort_order", "created_at",
                    "deleted_at", "initial_stock_quantity", "initial_stock_at",
                    "initial_price", "initial_price_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<ProductRow>()
            .firstOrNull { it.id == productId && it.deletedAt == null }
            ?: error("Produto não encontrado.")

        val stock = client.from("stock_current")
            .select(
                Columns.list(
                    "product_id", "current_quantity", "current_price",
                    "current_value", "stock_requires_conference",
                )
            ) { attachRegisteredDevice() }
            .decodeList<ProductStockRow>()
            .firstOrNull { it.productId == productId }

        val entryItems = client.from("entry_items")
            .select(
                Columns.list("id", "entry_id", "product_id", "quantity", "unit_price", "position")
            ) { attachRegisteredDevice() }
            .decodeList<ProductEntryItemRow>()
            .filter { it.productId == productId }

        val entryIds = entryItems.map { it.entryId }.toSet()
        val entries = client.from("entries")
            .select(
                Columns.list("id", "effective_at", "created_at", "supplier_id", "deleted_at")
            ) { attachRegisteredDevice() }
            .decodeList<ProductEntryRow>()
            .filter { it.id in entryIds && it.deletedAt == null }

        val supplierById = client.from("suppliers")
            .select(Columns.list("id", "name")) { attachRegisteredDevice() }
            .decodeList<ProductSupplierRow>()
            .associate { it.id to it.name }

        val entryById = entries.associateBy { it.id }
        val orderedEntries = entries
            .sortedWith(compareByDescending<ProductEntryRow> { epoch(it.effectiveAt) }
                .thenByDescending { epoch(it.createdAt) })
            .mapIndexed { index, entry -> entry.id to index }
            .toMap()

        val priceHistory = entryItems
            .filter { entryById.containsKey(it.entryId) }
            .sortedWith(
                compareBy<ProductEntryItemRow> { orderedEntries[it.entryId] ?: Int.MAX_VALUE }
                    .thenByDescending { it.position }
            )
            .map { item ->
                val entry = entryById.getValue(item.entryId)
                ProductPriceHistoryItem(
                    id = item.id,
                    entryId = item.entryId,
                    effectiveAt = entry.effectiveAt,
                    supplierName = supplierById[entry.supplierId] ?: "Fornecedor não disponível",
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    position = item.position,
                )
            }

        val conferenceItems = client.from("conference_items")
            .select(Columns.list("conference_id", "product_id", "quantity")) {
                attachRegisteredDevice()
            }
            .decodeList<ProductConferenceItemRow>()
            .filter { it.productId == productId }

        val conferenceIds = conferenceItems.map { it.conferenceId }.toSet()
        val conferenceById = client.from("conferences")
            .select(Columns.list("id", "effective_at", "created_at", "deleted_at")) {
                attachRegisteredDevice()
            }
            .decodeList<ProductConferenceRow>()
            .filter { it.id in conferenceIds && it.deletedAt == null }
            .associateBy { it.id }

        val usage = calculateProductUsageInsights(
            conferences = conferenceItems.mapNotNull { item ->
                val conference = conferenceById[item.conferenceId] ?: return@mapNotNull null
                UsageConferencePoint(
                    effectiveAt = conference.effectiveAt,
                    createdAt = conference.createdAt,
                    quantity = item.quantity,
                )
            },
            entries = entryItems.mapNotNull { item ->
                val entry = entryById[item.entryId] ?: return@mapNotNull null
                UsageEntryPoint(effectiveAt = entry.effectiveAt, quantity = item.quantity)
            },
            currentQuantity = stock?.currentQuantity,
        )

        return ProductDetails(
            id = product.id,
            name = product.name,
            categoryId = product.categoryId,
            unit = product.unit,
            createdAt = product.createdAt,
            initialStockQuantity = product.initialStockQuantity,
            initialStockAt = product.initialStockAt,
            initialPrice = product.initialPrice,
            initialPriceAt = product.initialPriceAt,
            currentQuantity = stock?.currentQuantity,
            currentPrice = stock?.currentPrice,
            currentValue = stock?.currentValue,
            stockRequiresConference = stock?.stockRequiresConference ?: false,
            priceHistory = priceHistory,
            usageInsights = usage,
        ) to catalog.categories
    }

    private fun epoch(value: String): Long =
        runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0L)
}

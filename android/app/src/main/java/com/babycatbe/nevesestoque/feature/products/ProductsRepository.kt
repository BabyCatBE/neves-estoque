package com.babycatbe.nevesestoque.feature.products

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import com.babycatbe.nevesestoque.data.offline.OfflineStore
import com.babycatbe.nevesestoque.data.offline.offlineCachedList
import com.babycatbe.nevesestoque.data.offline.readCachedList
import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.data.supabase.fetchAllByIdKeyset
import com.babycatbe.nevesestoque.data.supabase.fetchByIdChunks
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.util.UUID

class ProductsRepository {
    private val categoryIllustrationsBucket = "category-illustrations"

    private fun client() = SupabaseProvider.client
        ?: error("Supabase não está configurado nesta build.")

    suspend fun loadCatalog(): ProductCatalogData = coroutineScope {
        val client = client()

        val categoriesAsync = async {
            offlineCachedList<ProductCategoryRow>("catalog-categories") {
                client.from("categories")
                .select(
                    Columns.list(
                        "id", "name", "sort_order", "deleted_at",
                        "illustration_source", "illustration_key",
                        "illustration_position_x", "illustration_position_y",
                    )
                ) { attachRegisteredDevice() }
                .decodeList<ProductCategoryRow>()
            }
        }

        val productRowsAsync = async {
            offlineCachedList<ProductRow>("catalog-products") {
                client.from("products")
                .select(
                    Columns.list(
                        "id", "name", "category_id", "unit", "sort_order",
                        "created_at", "deleted_at",
                    )
                ) { attachRegisteredDevice() }
                .decodeList<ProductRow>()
            }
        }

        val stocksAsync = async {
            offlineCachedList<ProductStockRow>("catalog-stock_current") {
                client.from("stock_current")
                .select(
                    Columns.list(
                        "product_id", "current_quantity", "current_price",
                        "current_value", "stock_requires_conference",
                    )
                ) { attachRegisteredDevice() }
                .decodeList<ProductStockRow>()
            }
        }

        assembleProductCatalog(
            categories = categoriesAsync.await(),
            productRows = productRowsAsync.await(),
            stocks = stocksAsync.await(),
        )
    }

    /**
     * Catálogo montado somente da cópia local deste aparelho (sem rede), para a lista de Produtos
     * abrir na hora enquanto a leitura oficial atualiza em segundo plano. Null quando falta alguma parte.
     */
    suspend fun loadCatalogSnapshot(): ProductCatalogData? {
        val categories = readCachedList<ProductCategoryRow>("catalog-categories") ?: return null
        val productRows = readCachedList<ProductRow>("catalog-products") ?: return null
        val stocks = readCachedList<ProductStockRow>("catalog-stock_current") ?: return null
        return assembleProductCatalog(categories, productRows, stocks)
    }

    suspend fun loadCategories(): List<CategoryListItem> {
        val catalog = loadCatalog()
        val counts = catalog.products
            .mapNotNull { it.categoryId }
            .groupingBy { it }
            .eachCount()

        val client = client()
        return catalog.categories.map { category ->
            val illustrationBytes =
                if (category.illustrationSource == "upload" && category.illustrationKey != null) {
                    val key = category.illustrationKey
                    try {
                        client.storage[categoryIllustrationsBucket]
                            .downloadAuthenticated(key)
                            .also { bytes -> runCatching { OfflineStore.writeCacheBytes("illustration:$key", bytes) } }
                    } catch (_: Throwable) {
                        // Sem internet (ou falha no Storage): usa a cópia local da ilustração, se existir.
                        OfflineStore.readCacheBytes("illustration:$key")
                    }
                } else {
                    null
                }

            CategoryListItem(
                id = category.id,
                name = category.name,
                sortOrder = category.sortOrder,
                productCount = counts[category.id] ?: 0,
                illustrationSource = category.illustrationSource,
                illustrationKey = category.illustrationKey,
                illustrationPositionX = category.illustrationPositionX,
                illustrationPositionY = category.illustrationPositionY,
                illustrationBytes = illustrationBytes,
            )
        }
    }

    suspend fun createProduct(input: ProductMutationInput): String {
        val parameters = buildJsonObject {
            put("p_name", input.name)
            put("p_category_id", input.categoryId)
            put("p_unit", input.unit)
            input.initialStockQuantity?.let { put("p_initial_stock_quantity", it) }
            input.initialPrice?.let { put("p_initial_price", it) }
        }

        return client().postgrest.rpc(
            function = "create_product",
            parameters = parameters,
        ) { attachRegisteredDevice() }.decodeAs()
    }

    suspend fun updateProduct(productId: String, name: String, categoryId: String) {
        client().postgrest.rpc(
            function = "update_product_details",
            parameters = buildJsonObject {
                put("p_product_id", productId)
                put("p_name", name)
                put("p_category_id", categoryId)
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun convertProductUnit(input: ProductUnitConversionDraft) {
        val deviceId = DeviceIdentityStore.registeredDeviceId()
            ?: error("Dispositivo não autorizado.")

        client().postgrest.rpc(
            function = "convert_product_unit",
            parameters = buildJsonObject {
                put("p_product_id", input.productId)
                put("p_new_unit", input.newUnit)
                put("p_old_quantity", input.oldQuantity)
                put("p_new_quantity", input.newQuantity)
                put("p_device_id", deviceId)
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun mergeProducts(input: ProductMergeDraft): ProductMergeResult {
        val deviceId = DeviceIdentityStore.registeredDeviceId()
            ?: error("Dispositivo não autorizado.")

        val response = client().postgrest.rpc(
            function = "merge_products",
            parameters = buildJsonObject {
                put("p_product_a_id", input.productAId)
                put("p_product_b_id", input.productBId)
                put("p_final_name", input.finalName)
                put("p_final_category_id", input.finalCategoryId)
                put("p_final_unit", input.finalUnit)
                put(
                    "p_survivor_equivalent_quantity",
                    input.survivorEquivalentQuantity?.let(::JsonPrimitive) ?: JsonNull,
                )
                put(
                    "p_absorbed_equivalent_quantity",
                    input.absorbedEquivalentQuantity?.let(::JsonPrimitive) ?: JsonNull,
                )
                put("p_initial_price_source", input.initialPriceSource.wireValue)
                put("p_device_id", deviceId)
            },
        ) { attachRegisteredDevice() }.decodeAs<ProductMergeRpcResponse>()

        if (response.survivorProductId.isBlank() || response.absorbedProductId.isBlank()) {
            error("A mescla foi concluída, mas o resultado retornado é inválido.")
        }

        return response.toResult()
    }

    suspend fun reconcileProductMerge(
        input: ProductMergeDraft,
        pair: ProductMergePair,
    ): ProductMergeResult? {
        val catalog = loadCatalog()
        if (catalog.products.any { it.id == pair.absorbed.id }) return null

        val survivorList = catalog.products.firstOrNull { it.id == pair.survivor.id } ?: return null
        val survivorDetails = runCatching {
            loadProductDetails(pair.survivor.id).first
        }.getOrNull() ?: return null

        if (!isMergeAppliedSnapshot(input, pair, catalog.products, survivorDetails)) return null
        if (survivorList.id != survivorDetails.id) return null

        return ProductMergeResult(
            survivorProductId = pair.survivor.id,
            absorbedProductId = pair.absorbed.id,
            entryItemsCount = null,
            conferenceItemsCount = null,
            overlapConferenceCount = null,
            reconciledAfterAmbiguousFailure = true,
        )
    }

    suspend fun createCategory(name: String, sortOrder: Int): String {
        val categoryId = UUID.randomUUID().toString()
        createCategory(
            CategoryMutationInput(
                id = categoryId,
                name = name,
                sortOrder = sortOrder,
                illustrationSource = null,
                illustrationKey = null,
                illustrationPositionX = 50,
                illustrationPositionY = 50,
            )
        )
        return categoryId
    }

    suspend fun createCategory(input: CategoryMutationInput) {
        client().from("categories").insert(
            CreateCategoryPayload(
                id = input.id,
                name = input.name,
                sortOrder = input.sortOrder,
                illustrationSource = input.illustrationSource,
                illustrationKey = input.illustrationKey,
                illustrationPositionX = input.illustrationPositionX,
                illustrationPositionY = input.illustrationPositionY,
            )
        ) { attachRegisteredDevice() }
    }

    suspend fun updateCategory(categoryId: String, name: String) {
        val current = loadCategories().firstOrNull { it.id == categoryId }
            ?: error("Categoria não encontrada.")
        updateCategory(
            CategoryMutationInput(
                id = categoryId,
                name = name,
                sortOrder = current.sortOrder ?: 0,
                illustrationSource = current.illustrationSource,
                illustrationKey = current.illustrationKey,
                illustrationPositionX = current.illustrationPositionX,
                illustrationPositionY = current.illustrationPositionY,
            )
        )
    }

    suspend fun updateCategory(input: CategoryMutationInput) {
        client().from("categories").update(
            buildJsonObject {
                put("name", input.name)
                put(
                    "illustration_source",
                    input.illustrationSource?.let(::JsonPrimitive) ?: JsonNull,
                )
                put(
                    "illustration_key",
                    input.illustrationKey?.let(::JsonPrimitive) ?: JsonNull,
                )
                put("illustration_position_x", input.illustrationPositionX)
                put("illustration_position_y", input.illustrationPositionY)
            }
        ) {
            attachRegisteredDevice()
            filter {
                eq("id", input.id)
                exact("deleted_at", null)
            }
        }
    }

    suspend fun uploadCategoryIllustration(
        categoryId: String,
        bytes: ByteArray,
        mimeType: String,
    ): String {
        validateCategoryIllustrationFile(mimeType, bytes.size)?.let(::error)
        val extension = categoryIllustrationExtension(mimeType)
            ?: error("Formato de imagem não permitido.")
        val path = "$categoryId/${UUID.randomUUID()}.$extension"

        client().storage[categoryIllustrationsBucket].upload(
            path = path,
            data = bytes,
        ) {
            upsert = false
            contentType = ContentType.parse(mimeType)
        }
        return path
    }

    suspend fun removeCategoryIllustration(path: String) {
        client().storage[categoryIllustrationsBucket].delete(path)
    }

    suspend fun reorderCategories(categoryIds: List<String>) {
        client().postgrest.rpc(
            function = "reorder_categories",
            parameters = buildJsonObject {
                put(
                    "p_category_ids",
                    buildJsonArray {
                        categoryIds.forEach { add(JsonPrimitive(it)) }
                    },
                )
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun reorderProducts(orders: List<ProductOrderChange>) {
        client().postgrest.rpc(
            function = "reorder_products",
            parameters = buildJsonObject {
                put(
                    "p_orders",
                    buildJsonArray {
                        orders.forEach { order ->
                            add(
                                buildJsonObject {
                                    put("category_id", order.categoryId)
                                    put(
                                        "product_ids",
                                        buildJsonArray {
                                            order.productIds.forEach { add(JsonPrimitive(it)) }
                                        },
                                    )
                                }
                            )
                        }
                    },
                )
            },
        ) { attachRegisteredDevice() }
    }

    suspend fun softDeleteProduct(productId: String) {
        client().postgrest.rpc(
            function = "soft_delete_product",
            parameters = buildJsonObject { put("p_product_id", productId) },
        ) { attachRegisteredDevice() }
    }

    suspend fun restoreProduct(productId: String) {
        client().postgrest.rpc(
            function = "restore_product",
            parameters = buildJsonObject { put("p_product_id", productId) },
        ) { attachRegisteredDevice() }
    }

    suspend fun softDeleteCategory(categoryId: String) {
        client().from("categories").update(
            buildJsonObject { put("deleted_at", OffsetDateTime.now().toString()) }
        ) {
            attachRegisteredDevice()
            filter {
                eq("id", categoryId)
                exact("deleted_at", null)
            }
        }
    }

    suspend fun restoreCategory(categoryId: String) {
        client().from("categories").update(
            buildJsonObject { put("deleted_at", JsonNull) }
        ) {
            attachRegisteredDevice()
            filter { eq("id", categoryId) }
        }
    }

    suspend fun loadCatalogTrash(): List<CatalogTrashItem> {
        val client = client()
        val products = client.from("products")
            .select(
                Columns.list(
                    "id", "name", "unit", "deleted_at", "restore_until",
                    "permanently_deleted_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<ProductTrashRow>()

        val categories = client.from("categories")
            .select(
                Columns.list(
                    "id", "name", "deleted_at", "restore_until",
                    "permanently_deleted_at",
                )
            ) { attachRegisteredDevice() }
            .decodeList<CategoryTrashRow>()

        return buildRestorableCatalogTrash(products, categories)
    }

    suspend fun restoreCatalogTrashItem(item: CatalogTrashItem) {
        when (item.type) {
            CatalogTrashType.Product -> restoreProduct(item.id)
            CatalogTrashType.Category -> restoreCategory(item.id)
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

        // Filtro no servidor + cursor por id: ler a tabela inteira e filtrar no aparelho truncaria
        // em silêncio o histórico quando a tabela passasse do teto de linhas do servidor.
        val entryItems = fetchAllByIdKeyset(ProductEntryItemRow::id) { afterId, pageLimit ->
            client.from("entry_items")
                .select(
                    Columns.list("id", "entry_id", "product_id", "quantity", "unit_price", "position")
                ) {
                    attachRegisteredDevice()
                    filter {
                        eq("product_id", productId)
                        if (afterId != null) gt("id", afterId)
                    }
                    order("id", Order.ASCENDING)
                    limit(pageLimit)
                }
                .decodeList<ProductEntryItemRow>()
        }

        val entryIds = entryItems.map { it.entryId }.toSet()
        val entries = fetchByIdChunks(entryIds) { ids ->
            client.from("entries")
                .select(
                    Columns.list("id", "effective_at", "created_at", "supplier_id", "deleted_at")
                ) {
                    attachRegisteredDevice()
                    filter { isIn("id", ids) }
                }
                .decodeList<ProductEntryRow>()
        }.filter { it.deletedAt == null }

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

        val conferenceItems = fetchAllByIdKeyset(ProductConferenceItemRow::id) { afterId, pageLimit ->
            client.from("conference_items")
                .select(Columns.list("id", "conference_id", "product_id", "quantity")) {
                    attachRegisteredDevice()
                    filter {
                        eq("product_id", productId)
                        if (afterId != null) gt("id", afterId)
                    }
                    order("id", Order.ASCENDING)
                    limit(pageLimit)
                }
                .decodeList<ProductConferenceItemRow>()
        }

        val conferenceIds = conferenceItems.map { it.conferenceId }.toSet()
        val conferenceById = fetchByIdChunks(conferenceIds) { ids ->
            client.from("conferences")
                .select(Columns.list("id", "effective_at", "created_at", "deleted_at")) {
                    attachRegisteredDevice()
                    filter { isIn("id", ids) }
                }
                .decodeList<ProductConferenceRow>()
        }
            .filter { it.deletedAt == null }
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

internal fun assembleProductCatalog(
    categories: List<ProductCategoryRow>,
    productRows: List<ProductRow>,
    stocks: List<ProductStockRow>,
): ProductCatalogData {
    val stockByProduct = stocks.associateBy { it.productId }
    return ProductCatalogData(
        categories = categories.filter { it.deletedAt == null },
        products = productRows.filter { it.deletedAt == null }.map { row ->
            val stock = stockByProduct[row.id]
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

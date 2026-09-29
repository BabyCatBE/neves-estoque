package com.babycatbe.nevesestoque.feature.stock

import com.babycatbe.nevesestoque.feature.products.ProductCategoryRow
import com.babycatbe.nevesestoque.feature.products.ProductRow
import com.babycatbe.nevesestoque.feature.products.ProductStockRow
import com.babycatbe.nevesestoque.feature.products.assembleProductCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/** A cópia local e a leitura oficial passam pela mesma montagem (mesmos filtros). */
class StockSnapshotAssemblyTest {

    @Test
    fun currentStockIgnoresDeletedCategoriesAndIncompleteRows() {
        val data = assembleCurrentStock(
            categories = listOf(
                StockCategoryRow("c1", "Pães"),
                StockCategoryRow("c2", "Antiga", deletedAt = "2026-01-01T00:00:00Z"),
            ),
            suppliers = listOf(StockSupplierRow("s1", "Fornecedor")),
            items = listOf(
                CurrentStockRow(productId = "p1", productName = "Farinha", unit = "KG"),
                CurrentStockRow(productId = "", productName = "Sem id", unit = "UN"),
                CurrentStockRow(productId = "p3", productName = "Sem unidade", unit = ""),
            ),
        )

        assertEquals(listOf("c1"), data.categories.map { it.id })
        assertEquals(listOf("p1"), data.items.map { it.productId })
        assertEquals(1, data.suppliers.size)
    }

    @Test
    fun catalogJoinsStockAndIgnoresDeletedRows() {
        val catalog = assembleProductCatalog(
            categories = listOf(
                ProductCategoryRow("c1", "Pães"),
                ProductCategoryRow("c2", "Antiga", deletedAt = "2026-01-01T00:00:00Z"),
            ),
            productRows = listOf(
                ProductRow(id = "p1", name = "Farinha", categoryId = "c1", unit = "KG", createdAt = "2026-01-01T00:00:00Z"),
                ProductRow(
                    id = "p2",
                    name = "Excluído",
                    categoryId = "c1",
                    unit = "UN",
                    createdAt = "2026-01-01T00:00:00Z",
                    deletedAt = "2026-02-01T00:00:00Z",
                ),
            ),
            stocks = listOf(ProductStockRow("p1", currentQuantity = 12.5, stockRequiresConference = true)),
        )

        assertEquals(listOf("c1"), catalog.categories.map { it.id })
        assertEquals(listOf("p1"), catalog.products.map { it.id })
        assertEquals(12.5, catalog.products.single().currentQuantity!!, 0.0)
        assertEquals(true, catalog.products.single().stockRequiresConference)
    }
}

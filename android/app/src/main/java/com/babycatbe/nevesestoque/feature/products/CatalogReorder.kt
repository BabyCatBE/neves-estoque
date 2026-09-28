package com.babycatbe.nevesestoque.feature.products

import java.text.Collator
import java.util.Locale

data class ProductOrderGroup(
    val categoryId: String,
    val categoryName: String,
    val items: List<ProductListItem>,
)

data class ProductOrderChange(
    val categoryId: String,
    val productIds: List<String>,
)

private val reorderLocale = Locale.forLanguageTag("pt-BR")

private fun reorderNameComparator(): Comparator<String> {
    val collator = Collator.getInstance(reorderLocale).apply { strength = Collator.PRIMARY }
    return Comparator { a, b -> collator.compare(a, b) }
}

fun orderedCategories(categories: List<CategoryListItem>): List<CategoryListItem> {
    val comparator = reorderNameComparator()
    return categories.sortedWith { a, b ->
        compareValues(a.sortOrder ?: Int.MAX_VALUE, b.sortOrder ?: Int.MAX_VALUE)
            .takeIf { it != 0 } ?: comparator.compare(a.name, b.name)
    }
}

fun moveCategoryOrder(
    categories: List<CategoryListItem>,
    categoryId: String,
    direction: Int,
): List<CategoryListItem> {
    if (direction !in listOf(-1, 1)) return categories

    val fromIndex = categories.indexOfFirst { it.id == categoryId }
    val targetIndex = fromIndex + direction
    if (fromIndex < 0 || targetIndex !in categories.indices) return categories

    val next = categories.toMutableList()
    val moved = next.removeAt(fromIndex)
    next.add(targetIndex, moved)
    return next
}

fun categoryOrderChanged(
    original: List<CategoryListItem>,
    draft: List<CategoryListItem>,
): Boolean = original.map { it.id } != draft.map { it.id }

fun buildProductOrderGroups(
    categories: List<ProductCategoryRow>,
    products: List<ProductListItem>,
): List<ProductOrderGroup> {
    val comparator = reorderNameComparator()
    val productsByCategory = products
        .filter { it.categoryId != null }
        .groupBy { it.categoryId!! }

    return categories
        .sortedWith { a, b ->
            compareValues(a.sortOrder ?: Int.MAX_VALUE, b.sortOrder ?: Int.MAX_VALUE)
                .takeIf { it != 0 } ?: comparator.compare(a.name, b.name)
        }
        .mapNotNull { category ->
            val items = productsByCategory[category.id]
                .orEmpty()
                .sortedWith { a, b ->
                    compareValues(a.sortOrder ?: Int.MAX_VALUE, b.sortOrder ?: Int.MAX_VALUE)
                        .takeIf { it != 0 } ?: comparator.compare(a.name, b.name)
                }
            if (items.isEmpty()) {
                null
            } else {
                ProductOrderGroup(
                    categoryId = category.id,
                    categoryName = category.name,
                    items = items,
                )
            }
        }
}

fun moveProductOrder(
    groups: List<ProductOrderGroup>,
    categoryId: String,
    productId: String,
    direction: Int,
): List<ProductOrderGroup> {
    if (direction !in listOf(-1, 1)) return groups

    val groupIndex = groups.indexOfFirst { it.categoryId == categoryId }
    if (groupIndex < 0) return groups

    val group = groups[groupIndex]
    val fromIndex = group.items.indexOfFirst { it.id == productId }
    val targetIndex = fromIndex + direction
    if (fromIndex < 0 || targetIndex !in group.items.indices) return groups

    val nextItems = group.items.toMutableList()
    val moved = nextItems.removeAt(fromIndex)
    nextItems.add(targetIndex, moved)

    return groups.toMutableList().also { nextGroups ->
        nextGroups[groupIndex] = group.copy(items = nextItems)
    }
}

fun changedProductOrders(
    original: List<ProductOrderGroup>,
    draft: List<ProductOrderGroup>,
): List<ProductOrderChange> {
    val originalByCategory = original.associateBy { it.categoryId }

    return draft.mapNotNull { group ->
        val originalIds = originalByCategory[group.categoryId]?.items?.map { it.id }.orEmpty()
        val nextIds = group.items.map { it.id }
        if (originalIds == nextIds) {
            null
        } else {
            ProductOrderChange(
                categoryId = group.categoryId,
                productIds = nextIds,
            )
        }
    }
}

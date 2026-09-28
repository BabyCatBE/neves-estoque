package com.babycatbe.nevesestoque.feature.products

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val marks = Regex("\\p{Mn}+")
private val spaces = Regex("\\s+")
private val ptBr = Locale.forLanguageTag("pt-BR")

fun normalizeProductSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(marks, "")
        .lowercase(ptBr)
        .trim()
        .replace(spaces, " ")

private fun productNameComparator(): Comparator<String> {
    val collator = Collator.getInstance(ptBr).apply { strength = Collator.PRIMARY }
    return Comparator { a, b -> collator.compare(a, b) }
}

fun filterAndSortProducts(
    products: List<ProductListItem>,
    search: String = "",
    categoryId: String? = null,
): List<ProductListItem> {
    val term = normalizeProductSearch(search)
    val comparator = productNameComparator()

    return products
        .filter { categoryId == null || it.categoryId == categoryId }
        .filter { term.isBlank() || normalizeProductSearch(it.name).contains(term) }
        .sortedWith { a, b -> comparator.compare(a.name, b.name) }
}

data class ProductGroup(
    val id: String,
    val name: String,
    val items: List<ProductListItem>,
)

fun buildProductGroups(
    categories: List<ProductCategoryRow>,
    products: List<ProductListItem>,
    search: String = "",
): List<ProductGroup> {
    val term = normalizeProductSearch(search)
    val visible = products.filter { term.isBlank() || normalizeProductSearch(it.name).contains(term) }
    val comparator = productNameComparator()
    val byCategory = visible.filter { it.categoryId != null }.groupBy { it.categoryId!! }

    val groups = categories
        .sortedWith { a, b ->
            compareValues(a.sortOrder ?: Int.MAX_VALUE, b.sortOrder ?: Int.MAX_VALUE)
                .takeIf { it != 0 } ?: comparator.compare(a.name, b.name)
        }
        .mapNotNull { category ->
            val items = (byCategory[category.id] ?: emptyList()).sortedWith { a, b ->
                compareValues(a.sortOrder ?: Int.MAX_VALUE, b.sortOrder ?: Int.MAX_VALUE)
                    .takeIf { it != 0 } ?: comparator.compare(a.name, b.name)
            }
            if (items.isEmpty()) null else ProductGroup(
                id = category.id,
                name = category.name,
                items = items,
            )
        }
        .toMutableList()

    val pending = visible.filter { it.categoryId == null }.sortedWith { a, b ->
        comparator.compare(a.name, b.name)
    }
    if (pending.isNotEmpty()) {
        groups.add(
            0,
            ProductGroup(
                id = "pending",
                name = "Cadastro pendente",
                items = pending,
            )
        )
    }

    return groups
}

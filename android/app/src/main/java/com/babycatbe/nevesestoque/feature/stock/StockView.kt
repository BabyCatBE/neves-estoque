package com.babycatbe.nevesestoque.feature.stock

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val combiningMarks = Regex("\\p{Mn}+")
private val spaces = Regex("\\s+")
private val ptBr = Locale.forLanguageTag("pt-BR")

fun normalizeStockSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase(ptBr)
        .trim()
        .replace(spaces, " ")

private fun CurrentStockRow.matches(search: String): Boolean {
    val term = normalizeStockSearch(search)
    return term.isBlank() || normalizeStockSearch(productName).contains(term)
}

private fun nameComparator(): Comparator<String> {
    val collator = Collator.getInstance(ptBr).apply { strength = Collator.PRIMARY }
    return Comparator { a, b -> collator.compare(a, b) }
}

private fun sortByName(items: List<CurrentStockRow>): List<CurrentStockRow> {
    val comparator = nameComparator()
    return items.sortedWith { a, b -> comparator.compare(a.productName, b.productName) }
}

fun buildAlphabeticalStockView(
    items: List<CurrentStockRow>,
    search: String = "",
): List<CurrentStockRow> = sortByName(items.filter { it.matches(search) })

fun buildCategoryStockGroups(
    categories: List<StockCategoryRow>,
    items: List<CurrentStockRow>,
    search: String = "",
): List<StockGroup> {
    val visible = items.filter { it.matches(search) }
    val byCategory = visible.filter { it.categoryId != null }.groupBy { it.categoryId!! }
    val pending = visible.filter { it.categoryId == null }
    val comparator = nameComparator()

    val categoryGroups = categories
        .sortedWith { a, b ->
            compareValues(a.sortOrder ?: Int.MAX_VALUE, b.sortOrder ?: Int.MAX_VALUE)
                .takeIf { it != 0 }
                ?: comparator.compare(a.name, b.name)
        }
        .mapNotNull { category ->
            val groupItems = (byCategory[category.id] ?: emptyList())
                .sortedWith { a, b ->
                    compareValues(a.sortOrder ?: Int.MAX_VALUE, b.sortOrder ?: Int.MAX_VALUE)
                        .takeIf { it != 0 }
                        ?: comparator.compare(a.productName, b.productName)
                }
            if (groupItems.isEmpty()) null else StockGroup(
                id = "category:${category.id}",
                name = category.name,
                items = groupItems,
            )
        }

    val pendingGroup = if (pending.isEmpty()) emptyList() else listOf(
        StockGroup(
            id = "category:pending",
            name = "Cadastro pendente",
            subtitle = "Falta definir categoria",
            items = sortByName(pending),
            accent = StockGroupAccent.Warning,
        )
    )

    return pendingGroup + categoryGroups
}

fun buildSupplierStockGroups(
    suppliers: List<StockSupplierRow>,
    items: List<CurrentStockRow>,
    search: String = "",
): List<StockGroup> {
    val visible = items.filter { it.matches(search) }
    val supplierById = suppliers.associateBy { it.id }
    val valid = visible.filter { it.currentSupplierId != null && supplierById.containsKey(it.currentSupplierId) }
        .groupBy { it.currentSupplierId!! }
    val unavailable = visible.filter {
        it.currentSupplierId != null && !supplierById.containsKey(it.currentSupplierId)
    }
    val withoutSupplier = visible.filter { it.currentSupplierId == null }
    val comparator = nameComparator()

    val groups = suppliers
        .sortedWith { a, b -> comparator.compare(a.name, b.name) }
        .mapNotNull { supplier ->
            val groupItems = sortByName(valid[supplier.id] ?: emptyList())
            if (groupItems.isEmpty()) null else StockGroup(
                id = "supplier:${supplier.id}",
                name = supplier.name,
                subtitle = "Fornecedor da Entrada mais recente",
                items = groupItems,
            )
        }
        .toMutableList()

    if (unavailable.isNotEmpty()) {
        groups += StockGroup(
            id = "supplier:unavailable",
            name = "Fornecedor indisponível",
            subtitle = "Há referência histórica, mas o cadastro não pôde ser carregado",
            items = sortByName(unavailable),
            accent = StockGroupAccent.Warning,
        )
    }

    if (withoutSupplier.isNotEmpty()) {
        groups += StockGroup(
            id = "supplier:none",
            name = "Sem fornecedor",
            subtitle = "Sem Entrada válida registrada",
            items = sortByName(withoutSupplier),
            accent = StockGroupAccent.Neutral,
        )
    }

    return groups
}

fun calculateStockValueSummary(items: List<CurrentStockRow>): StockValueSummary =
    StockValueSummary(
        totalKnown = items.sumOf { it.currentValue ?: 0.0 },
        hasMissingPrice = items.any {
            it.currentQuantity != null && it.currentQuantity > 0.0 && it.currentPrice == null
        },
    )

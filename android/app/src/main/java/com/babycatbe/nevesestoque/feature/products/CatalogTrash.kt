package com.babycatbe.nevesestoque.feature.products

import java.time.OffsetDateTime

enum class CatalogTrashType { Product, Category }

data class CatalogTrashItem(
    val id: String,
    val type: CatalogTrashType,
    val name: String,
    val detail: String?,
    val deletedAt: String,
    val restoreUntil: String,
)

fun buildRestorableCatalogTrash(
    products: List<ProductTrashRow>,
    categories: List<CategoryTrashRow>,
    nowMillis: Long = System.currentTimeMillis(),
): List<CatalogTrashItem> {
    val productItems = products.mapNotNull { row ->
        val deletedAt = row.deletedAt ?: return@mapNotNull null
        val restoreUntil = row.restoreUntil ?: return@mapNotNull null
        if (row.permanentlyDeletedAt != null || epoch(restoreUntil) <= nowMillis) return@mapNotNull null
        CatalogTrashItem(
            id = row.id,
            type = CatalogTrashType.Product,
            name = row.name,
            detail = row.unit,
            deletedAt = deletedAt,
            restoreUntil = restoreUntil,
        )
    }

    val categoryItems = categories.mapNotNull { row ->
        val deletedAt = row.deletedAt ?: return@mapNotNull null
        val restoreUntil = row.restoreUntil ?: return@mapNotNull null
        if (row.permanentlyDeletedAt != null || epoch(restoreUntil) <= nowMillis) return@mapNotNull null
        CatalogTrashItem(
            id = row.id,
            type = CatalogTrashType.Category,
            name = row.name,
            detail = null,
            deletedAt = deletedAt,
            restoreUntil = restoreUntil,
        )
    }

    return (productItems + categoryItems).sortedByDescending { epoch(it.deletedAt) }
}

fun canSoftDeleteCategory(productCount: Int): Boolean = productCount == 0

fun productDeleteConfirmation(name: String, currentQuantity: Double?, unit: String): String {
    val stockWarning = if (currentQuantity != null && currentQuantity > 0.0) {
        " Este Produto ainda possui ${formatTrashQuantity(currentQuantity)} $unit em estoque. " +
            "Ao excluir, ele sairá imediatamente do catálogo e dos cálculos ativos."
    } else {
        ""
    }
    return "Excluir o Produto “$name”?$stockWarning Ele ficará na Lixeira por 7 dias e poderá ser restaurado nesse período."
}

fun catalogTrashErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        message.contains("Prazo de restauração expirado", ignoreCase = true) ->
            "O prazo de 7 dias para restaurar este item expirou."
        message.contains("Item excluído definitivamente", ignoreCase = true) ->
            "Este item foi excluído definitivamente e não pode ser restaurado."
        message.contains("Categoria possui produtos ativos", ignoreCase = true) ->
            "A Categoria só pode ser excluída quando estiver vazia."
        message.contains("Produto não encontrado na lixeira", ignoreCase = true) ->
            "Este Produto não está mais disponível para restauração."
        message.contains("Produto não encontrado ou já excluído", ignoreCase = true) ->
            "Este Produto não está mais disponível para exclusão."
        message.contains("Acesso não autorizado", ignoreCase = true) ->
            "Seu usuário não está autorizado para esta ação."
        message.contains("duplicate", ignoreCase = true) ||
            message.contains("unique", ignoreCase = true) ->
            "Já existe um cadastro ativo com dados conflitantes. Revise os cadastros antes de restaurar."
        else -> "Não foi possível concluir a ação na Lixeira. Tente novamente."
    }
}

private fun epoch(value: String): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(Long.MIN_VALUE)

private fun formatTrashQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

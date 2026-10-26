package com.babycatbe.nevesestoque.feature.offline

import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.data.supabase.fetchAllByIdKeyset
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

/**
 * Estado ATUAL do catálogo, consultado diretamente no servidor.
 * Não usar o cache offline: ele pode ter 10 produtos quando a categoria já exige 11.
 */
@Serializable
data class PendingCatalogProduct(
    val id: String,
    val name: String,
    val unit: String,
)

data class PendingCatalogDifference(
    val expectedCount: Int,
    val receivedCount: Int,
    val missing: List<PendingCatalogProduct>,
    val inactiveOrMoved: List<PendingConferenceItem>,
    val repeatedIds: List<String>,
) {
    val matches: Boolean get() =
        expectedCount > 0 && missing.isEmpty() && inactiveOrMoved.isEmpty() &&
            repeatedIds.isEmpty() && receivedCount == expectedCount

    fun userMessage(): String = buildString {
        append("A pendência tem $receivedCount de $expectedCount produtos ativos nesta Categoria.")
        if (missing.isNotEmpty()) {
            append(" Falta")
            append(if (missing.size == 1) ": " else "m: ")
            append(missing.joinToString { it.name })
            append(". Abra Editar pendência para informar a quantidade física que faltou; zero deve ser digitado, nunca presumido.")
        }
        if (inactiveOrMoved.isNotEmpty()) {
            append(" Produto(s) removido(s) ou movido(s) da categoria: ")
            append(inactiveOrMoved.joinToString { it.productName })
            append(". A pendência foi preservada; revise o cadastro antes de continuar.")
        }
        if (repeatedIds.isNotEmpty()) {
            append(" Existem produtos repetidos no arquivo local. A pendência foi preservada para revisão.")
        }
        if (expectedCount == 0) append(" A categoria não tem produtos ativos; confirme o cadastro.")
        append(" Nada foi enviado.")
    }
}

/** Compara IDs exatos, não apenas quantidade: protege contra substituição, duplicata e movimentação. */
fun comparePendingCategoryCatalog(
    items: List<PendingConferenceItem>,
    activeProducts: List<PendingCatalogProduct>,
): PendingCatalogDifference {
    val activeIds = activeProducts.map { it.id }.toSet()
    val savedIds = items.map { it.productId }.toSet()
    return PendingCatalogDifference(
        expectedCount = activeProducts.size,
        receivedCount = items.size,
        missing = activeProducts.filter { it.id !in savedIds },
        inactiveOrMoved = items.filter { it.productId !in activeIds },
        repeatedIds = items.groupBy { it.productId }.filterValues { it.size > 1 }.keys.toList(),
    )
}

class PendingCatalogRepository {
    suspend fun currentProducts(categoryId: String): List<PendingCatalogProduct> {
        val client = SupabaseProvider.client ?: error("Supabase não está configurado nesta build.")
        // Leitura DIRETA online; nenhuma queda para cópia local em uma confirmação de envio.
        return fetchAllByIdKeyset(PendingCatalogProduct::id) { afterId, pageLimit ->
            client.from("products")
                .select(Columns.list("id", "name", "unit")) {
                    attachRegisteredDevice()
                    filter {
                        eq("category_id", categoryId)
                        exact("deleted_at", null)
                        if (afterId != null) gt("id", afterId)
                    }
                    order("id", Order.ASCENDING)
                    limit(pageLimit)
                }
                .decodeList<PendingCatalogProduct>()
        }
    }

    suspend fun compare(operation: PendingOperation): PendingCatalogDifference {
        val payload = requireNotNull(operation.categoryConference) { "Pendência sem dados de Conferência." }
        return comparePendingCategoryCatalog(payload.items, currentProducts(payload.categoryId))
    }
}

package com.babycatbe.nevesestoque.feature.trash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tipos aceitos pela Lixeira Universal. O [rpcValue] é o mesmo contrato usado pela Web e pelo banco. */
enum class TrashItemType(val rpcValue: String, val label: String, val filterLabel: String) {
    Product("product", "Produto", "Produtos"),
    Category("category", "Categoria", "Categorias"),
    Supplier("supplier", "Fornecedor", "Fornecedores"),
    Entry("entry", "Entrada", "Entradas"),
    Conference("conference", "Conferência", "Conferências");

    companion object {
        fun fromFilter(value: String?): TrashItemType? = entries.firstOrNull { it.rpcValue == value }
    }
}

data class TrashItem(
    val id: String,
    val type: TrashItemType,
    val name: String,
    val detail: String?,
    val deletedAt: String,
    val restoreUntil: String,
)

data class EmptyTrashResult(
    val products: Int,
    val categories: Int,
    val suppliers: Int,
    val entries: Int,
    val conferences: Int,
    val total: Int,
)

@Serializable
data class TrashProductRow(
    val id: String,
    val name: String,
    val unit: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("restore_until") val restoreUntil: String? = null,
    @SerialName("permanently_deleted_at") val permanentlyDeletedAt: String? = null,
)

@Serializable
data class TrashCategoryRow(
    val id: String,
    val name: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("restore_until") val restoreUntil: String? = null,
    @SerialName("permanently_deleted_at") val permanentlyDeletedAt: String? = null,
)

@Serializable
data class TrashSupplierRow(
    val id: String,
    val name: String,
    val company: String? = null,
    val phone: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("restore_until") val restoreUntil: String? = null,
    @SerialName("permanently_deleted_at") val permanentlyDeletedAt: String? = null,
)

@Serializable
data class TrashEntryRow(
    val id: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("restore_until") val restoreUntil: String? = null,
    @SerialName("permanently_deleted_at") val permanentlyDeletedAt: String? = null,
)

@Serializable
data class TrashConferenceRow(
    val id: String,
    @SerialName("scope_type") val scopeType: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("scope_product_id") val scopeProductId: String? = null,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("physical_responsible") val physicalResponsible: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("restore_until") val restoreUntil: String? = null,
    @SerialName("permanently_deleted_at") val permanentlyDeletedAt: String? = null,
)

@Serializable
data class TrashNameRow(val id: String, val name: String)

/** Linhas lidas do banco, antes da regra de janela restaurável. */
data class TrashSourceRows(
    val products: List<TrashProductRow> = emptyList(),
    val categories: List<TrashCategoryRow> = emptyList(),
    val suppliers: List<TrashSupplierRow> = emptyList(),
    val entries: List<TrashEntryRow> = emptyList(),
    val conferences: List<TrashConferenceRow> = emptyList(),
    val supplierNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val productNames: Map<String, String> = emptyMap(),
)

/**
 * Monta a Lixeira Universal com a mesma regra da Web: item excluído logicamente,
 * dentro da janela de restauração e ainda não excluído definitivamente.
 * Ordena do mais recentemente excluído para o mais antigo.
 */
fun buildRestorableTrash(
    rows: TrashSourceRows,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<TrashItem> {
    fun restorable(deletedAt: String?, restoreUntil: String?, permanentlyDeletedAt: String?): Boolean =
        deletedAt != null &&
            restoreUntil != null &&
            permanentlyDeletedAt == null &&
            trashEpoch(restoreUntil) > nowMillis

    val products = rows.products
        .filter { restorable(it.deletedAt, it.restoreUntil, it.permanentlyDeletedAt) }
        .map { TrashItem(it.id, TrashItemType.Product, it.name, it.unit, it.deletedAt!!, it.restoreUntil!!) }

    val categories = rows.categories
        .filter { restorable(it.deletedAt, it.restoreUntil, it.permanentlyDeletedAt) }
        .map { TrashItem(it.id, TrashItemType.Category, it.name, null, it.deletedAt!!, it.restoreUntil!!) }

    val suppliers = rows.suppliers
        .filter { restorable(it.deletedAt, it.restoreUntil, it.permanentlyDeletedAt) }
        .map {
            TrashItem(
                id = it.id,
                type = TrashItemType.Supplier,
                name = it.name,
                detail = it.company?.takeIf(String::isNotBlank)
                    ?: it.phone?.takeIf(String::isNotBlank)
                    ?: "Cadastro pendente",
                deletedAt = it.deletedAt!!,
                restoreUntil = it.restoreUntil!!,
            )
        }

    val entries = rows.entries
        .filter { restorable(it.deletedAt, it.restoreUntil, it.permanentlyDeletedAt) }
        .map {
            TrashItem(
                id = it.id,
                type = TrashItemType.Entry,
                name = rows.supplierNames[it.supplierId] ?: "Fornecedor não disponível",
                detail = "Entrada de ${formatTrashDate(it.effectiveAt, zone)}",
                deletedAt = it.deletedAt!!,
                restoreUntil = it.restoreUntil!!,
            )
        }

    val conferences = rows.conferences
        .filter { restorable(it.deletedAt, it.restoreUntil, it.permanentlyDeletedAt) }
        .map {
            val scopeName = when (it.scopeType) {
                "category" -> it.categoryId?.let(rows.categoryNames::get)
                "product" -> it.scopeProductId?.let(rows.productNames::get)
                else -> null
            }
            val responsible = it.physicalResponsible?.takeIf(String::isNotBlank)
            TrashItem(
                id = it.id,
                type = TrashItemType.Conference,
                name = scopeName ?: "Conferência",
                detail = buildString {
                    append("Conferência de ${formatTrashDate(it.effectiveAt, zone)}")
                    if (responsible != null) append(" · Responsável: $responsible")
                },
                deletedAt = it.deletedAt!!,
                restoreUntil = it.restoreUntil!!,
            )
        }

    return (products + categories + suppliers + entries + conferences)
        .sortedByDescending { trashEpoch(it.deletedAt) }
}

/** Filtro por tipo (null = Todos) + pesquisa sem acento/caixa sobre nome, detalhe e tipo. */
fun filterTrashItems(
    items: List<TrashItem>,
    type: TrashItemType?,
    search: String,
    normalize: (String) -> String,
): List<TrashItem> {
    val term = normalize(search)
    return items.filter { item ->
        (type == null || item.type == type) &&
            (term.isBlank() ||
                normalize(listOfNotNull(item.name, item.detail, item.type.label).joinToString(" ")).contains(term))
    }
}

/** Mesma regra da Web: comparação sem espaços extras e sem diferença de caixa. */
fun isTrashConfirmationValid(value: String, phrase: String): Boolean =
    value.trim().uppercase(PT_BR) == phrase.trim().uppercase(PT_BR)

const val PERMANENT_DELETE_PHRASE = "EXCLUIR"
const val EMPTY_TRASH_PHRASE = "ESVAZIAR"

fun trashRestoreDescription(item: TrashItem): String = when (item.type) {
    TrashItemType.Product ->
        "Restaurar o Produto “${item.name}”? Ele voltará para a posição manual anterior na Categoria."
    TrashItemType.Category ->
        "Restaurar a Categoria “${item.name}”? Ela voltará para a posição manual anterior."
    TrashItemType.Supplier ->
        "Restaurar o Fornecedor “${item.name}”? Ele voltará ao cadastro ativo e poderá ser usado em novas Entradas."
    TrashItemType.Entry ->
        "Restaurar a Entrada de “${item.name}”? Ela voltará ao Histórico ativo e seus efeitos serão recolocados automaticamente nos cálculos atuais de estoque e preço."
    TrashItemType.Conference ->
        "Restaurar a Conferência de “${item.name}”? Ela voltará ao Histórico ativo e seus efeitos serão recolocados automaticamente nos cálculos de estoque, consumo e relatórios."
}

fun trashPermanentDeleteDescription(item: TrashItem): String =
    "${item.type.label} “${item.name}” não poderá mais ser restaurado. " +
        "A identidade histórica necessária pode permanecer internamente para preservar registros antigos."

fun trashEmptyDescription(count: Int): String =
    "Todos os $count ${if (count == 1) "item atualmente restaurável ficará irrecuperável" else "itens atualmente restauráveis ficarão irrecuperáveis"} no aplicativo. " +
        "A identidade histórica necessária será preservada internamente."

fun trashRestoreSuccessMessage(item: TrashItem): String =
    "${item.type.label} “${item.name}” ${if (item.type.isFeminine()) "restaurada" else "restaurado"} com sucesso."

fun trashPermanentDeleteSuccessMessage(item: TrashItem): String =
    "${item.type.label} “${item.name}” ${if (item.type.isFeminine()) "excluída" else "excluído"} definitivamente da área restaurável."

fun emptyTrashSuccessMessage(result: EmptyTrashResult): String =
    if (result.total == 0) {
        "A Lixeira já estava vazia."
    } else if (result.total == 1) {
        "Lixeira esvaziada: 1 item ficou irrecuperável no aplicativo."
    } else {
        "Lixeira esvaziada: ${result.total} itens ficaram irrecuperáveis no aplicativo."
    }

fun trashErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        message.contains("Prazo de restauração expirado", ignoreCase = true) ->
            "O prazo de 7 dias para restaurar este item expirou."
        message.contains("Item excluído definitivamente", ignoreCase = true) ->
            "Este item foi excluído definitivamente e não pode mais ser restaurado."
        message.contains("Item não está disponível para exclusão definitiva", ignoreCase = true) ->
            "Este item não está mais disponível para exclusão definitiva."
        message.contains("Dispositivo não autorizado", ignoreCase = true) ->
            "Este dispositivo não está autorizado para esta ação."
        message.contains("Acesso não autorizado", ignoreCase = true) ->
            "Seu usuário não está autorizado para esta ação."
        message.contains("não encontrado na lixeira", ignoreCase = true) ||
            message.contains("não encontrada na lixeira", ignoreCase = true) ->
            "Este item não está mais disponível para restauração."
        message.contains("Já existe um fornecedor ativo com este contato", ignoreCase = true) ->
            "Já existe um Fornecedor ativo com este mesmo contato. Revise o cadastro antes de restaurar."
        message.contains("duplicate", ignoreCase = true) ||
            message.contains("unique", ignoreCase = true) ->
            "Já existe um cadastro ativo com dados conflitantes. Revise os cadastros antes de restaurar."
        else -> "Não foi possível concluir a ação na Lixeira. Tente novamente."
    }
}

fun formatTrashDateTime(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(zone).format(DATE_TIME_FORMAT)
    }.getOrDefault(value)

internal fun formatTrashDate(value: String, zone: ZoneId): String =
    runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(zone).format(DATE_FORMAT)
    }.getOrDefault(value)

private fun TrashItemType.isFeminine(): Boolean =
    this == TrashItemType.Category || this == TrashItemType.Entry || this == TrashItemType.Conference

private fun trashEpoch(value: String): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(Long.MIN_VALUE)

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

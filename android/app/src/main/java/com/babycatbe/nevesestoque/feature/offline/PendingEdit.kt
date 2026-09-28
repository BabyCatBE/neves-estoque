package com.babycatbe.nevesestoque.feature.offline

import com.babycatbe.nevesestoque.feature.conferences.buildEditedConferenceEffectiveAt
import com.babycatbe.nevesestoque.feature.conferences.categoryConferenceDateError
import com.babycatbe.nevesestoque.feature.conferences.parseConferenceQuantity
import java.time.OffsetDateTime
import java.time.ZoneId

/*
 * Edição de pendência local antes do envio (regra aprovada do V1: enviar, editar, excluir ou manter).
 * Preserva localId e idempotencyKey. Fornecedor/Categoria/Produtos não são trocados aqui: para isso,
 * exclua a pendência e prepare outra. Todas as validações são refeitas pelo banco no envio.
 */

data class PendingEditDraft(
    /** yyyy-MM-dd. Ignorado na Conferência unitária (a data é a do registro). */
    val date: String,
    val responsible: String = "",
    val observation: String = "",
    /** Entrada: chave = índice do item. Conferência: chave = productId. */
    val quantities: Map<String, String> = emptyMap(),
    /** Somente Entrada: vazio = preço não informado; 0 = bonificação. */
    val prices: Map<String, String> = emptyMap(),
    /** Somente Entrada: índices removidos. */
    val removedItems: Set<Int> = emptySet(),
)

data class PendingEditResult(val operation: PendingOperation?, val errors: List<String>)

fun localDateOf(effectiveAt: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { OffsetDateTime.parse(effectiveAt).atZoneSameInstant(zone).toLocalDate().toString() }
        .getOrDefault(effectiveAt.take(10))

fun formatDecimalForEdit(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')

fun initialEditDraft(operation: PendingOperation): PendingEditDraft = when (operation.kind) {
    PendingKind.Entry -> {
        val payload = operation.entry!!
        PendingEditDraft(
            date = localDateOf(payload.effectiveAt),
            observation = payload.observation.orEmpty(),
            quantities = payload.items.mapIndexed { index, item -> index.toString() to formatDecimalForEdit(item.quantity) }.toMap(),
            prices = payload.items.mapIndexed { index, item ->
                index.toString() to (item.unitPrice?.let(::formatDecimalForEdit) ?: "")
            }.toMap(),
        )
    }
    PendingKind.CategoryConference -> {
        val payload = operation.categoryConference!!
        PendingEditDraft(
            date = localDateOf(payload.effectiveAt),
            responsible = payload.physicalResponsible,
            observation = payload.observation.orEmpty(),
            quantities = payload.items.associate { it.productId to formatDecimalForEdit(it.quantity) },
        )
    }
    PendingKind.ProductConference -> {
        val payload = operation.productConference!!
        PendingEditDraft(
            date = localDateOf(payload.effectiveAt),
            responsible = payload.physicalResponsible,
            observation = payload.observation.orEmpty(),
            quantities = mapOf(payload.productId to formatDecimalForEdit(payload.quantity)),
        )
    }
}

private fun cleanObservation(value: String, errors: MutableList<String>): String? {
    val trimmed = value.trim()
    if (trimmed.length > 2000) errors += "A observação deve ter no máximo 2.000 caracteres."
    return trimmed.ifBlank { null }
}

private fun cleanResponsible(value: String, errors: MutableList<String>): String? {
    val normalized = value.trim().replace(Regex("\\s+"), " ")
    when {
        normalized.isEmpty() -> errors += "Informe o responsável pela contagem física."
        normalized.length > 160 -> errors += "O responsável deve ter no máximo 160 caracteres."
    }
    return normalized.ifBlank { null }
}

private fun editedEffectiveAt(date: String, original: String, errors: MutableList<String>): String? {
    categoryConferenceDateError(date)?.let {
        errors += it
        return null
    }
    return runCatching { buildEditedConferenceEffectiveAt(date, original) }
        .getOrElse {
            errors += "Informe uma data válida."
            null
        }
}

fun applyPendingEdit(
    operation: PendingOperation,
    draft: PendingEditDraft,
    now: String = OffsetDateTime.now().toString(),
): PendingEditResult {
    val errors = mutableListOf<String>()
    val updated: PendingOperation? = when (operation.kind) {
        PendingKind.Entry -> {
            val payload = operation.entry!!
            val effectiveAt = editedEffectiveAt(draft.date, payload.effectiveAt, errors)
            val observation = cleanObservation(draft.observation, errors)
            val items = payload.items.mapIndexedNotNull { index, item ->
                if (index in draft.removedItems) return@mapIndexedNotNull null
                val key = index.toString()
                val quantity = draft.quantities[key].orEmpty().trim().replace(',', '.').toDoubleOrNull()
                if (quantity == null || !quantity.isFinite() || quantity <= 0.0) {
                    errors += "${item.productName}: a quantidade deve ser maior que zero."
                }
                val priceText = draft.prices[key].orEmpty().trim()
                val price = if (priceText.isEmpty()) {
                    null
                } else {
                    priceText.replace(',', '.').toDoubleOrNull().also { parsed ->
                        if (parsed == null || !parsed.isFinite() || parsed < 0.0) {
                            errors += "${item.productName}: preço inválido (deixe em branco ou use 0 para bonificação)."
                        }
                    }
                }
                item.copy(quantity = quantity ?: item.quantity, unitPrice = price)
            }
            if (items.isEmpty()) errors += "A Entrada precisa de pelo menos um item. Para desistir, exclua a pendência."
            effectiveAt?.let {
                operation.copy(
                    updatedAt = now,
                    entry = payload.copy(effectiveAt = it, observation = observation, items = items),
                )
            }
        }
        PendingKind.CategoryConference -> {
            val payload = operation.categoryConference!!
            val effectiveAt = editedEffectiveAt(draft.date, payload.effectiveAt, errors)
            val responsible = cleanResponsible(draft.responsible, errors)
            val observation = cleanObservation(draft.observation, errors)
            val items = payload.items.map { item ->
                val (quantity, error) = parseConferenceQuantity(draft.quantities[item.productId].orEmpty(), item.productName)
                error?.let { errors += it }
                item.copy(quantity = quantity ?: item.quantity)
            }
            if (effectiveAt != null && responsible != null) {
                operation.copy(
                    updatedAt = now,
                    categoryConference = payload.copy(
                        effectiveAt = effectiveAt,
                        physicalResponsible = responsible,
                        observation = observation,
                        items = items,
                    ),
                )
            } else {
                null
            }
        }
        PendingKind.ProductConference -> {
            val payload = operation.productConference!!
            val responsible = cleanResponsible(draft.responsible, errors)
            val observation = cleanObservation(draft.observation, errors)
            val (quantity, error) = parseConferenceQuantity(draft.quantities[payload.productId].orEmpty())
            error?.let { errors += it }
            if (responsible != null && quantity != null) {
                operation.copy(
                    updatedAt = now,
                    productConference = payload.copy(
                        physicalResponsible = responsible,
                        observation = observation,
                        quantity = quantity,
                    ),
                )
            } else {
                null
            }
        }
    }
    return if (errors.isEmpty()) PendingEditResult(updated, emptyList()) else PendingEditResult(null, errors)
}

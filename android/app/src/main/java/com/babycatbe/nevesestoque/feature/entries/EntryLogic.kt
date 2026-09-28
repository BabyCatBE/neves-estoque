package com.babycatbe.nevesestoque.feature.entries

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

private val decimalPattern = Regex("^\\d+(?:[.,]\\d+)?$")
private val repeatedSpaces = Regex("\\s+")

data class EntryDecimalResult(val value: Double?, val error: String?)

data class QuickProductValidation(
    val product: EntryDraftProduct?,
    val error: String?,
)

fun parseEntryPositiveDecimal(value: String, label: String): EntryDecimalResult {
    val normalized = value.trim()
    if (normalized.isEmpty()) return EntryDecimalResult(null, "$label é obrigatória.")
    if (!decimalPattern.matches(normalized)) return EntryDecimalResult(null, "$label inválida.")
    val parsed = normalized.replace(',', '.').toDoubleOrNull()
    return if (parsed == null || !parsed.isFinite() || parsed <= 0) {
        EntryDecimalResult(null, "$label deve ser maior que zero.")
    } else {
        EntryDecimalResult(parsed, null)
    }
}

fun parseEntryOptionalPrice(value: String): EntryDecimalResult {
    val normalized = value.trim()
    if (normalized.isEmpty()) return EntryDecimalResult(null, null)
    if (!decimalPattern.matches(normalized)) {
        return EntryDecimalResult(null, "Preço unitário inválido.")
    }
    val parsed = normalized.replace(',', '.').toDoubleOrNull()
    return if (parsed == null || !parsed.isFinite() || parsed < 0) {
        EntryDecimalResult(null, "Preço unitário não pode ser negativo.")
    } else {
        EntryDecimalResult(parsed, null)
    }
}

fun todayEntryDate(): String = LocalDate.now().toString()

fun entryDateError(value: String, today: LocalDate = LocalDate.now()): String? {
    val date = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        return "Informe uma data válida."
    }
    return if (date > today) "A data não pode ser futura." else null
}

fun buildEntryEffectiveAt(
    dateValue: String,
    referenceInstant: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    entryDateError(dateValue)?.let(::error)
    val date = LocalDate.parse(dateValue)
    val localTime = referenceInstant.atZone(zoneId).toLocalTime()
    return date.atTime(localTime).atZone(zoneId).toOffsetDateTime().toString()
}

fun entryLocalDate(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val instant = OffsetDateTime.parse(value).toInstant()
    return instant.atZone(zoneId).toLocalDate().toString()
}

fun entryInstant(value: String): Instant =
    OffsetDateTime.parse(value).toInstant()

fun normalizeEntrySearchText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .lowercase(Locale.forLanguageTag("pt-BR"))
        .trim()
        .replace(repeatedSpaces, " ")

fun entrySupplierMatches(option: EntrySupplierOption, search: String): Boolean {
    val term = normalizeEntrySearchText(search)
    if (term.isBlank()) return true
    return listOf(option.name, option.company.orEmpty(), option.phone.orEmpty())
        .any { normalizeEntrySearchText(it).contains(term) }
}

fun entryProductMatches(option: EntryProductOption, search: String): Boolean {
    val term = normalizeEntrySearchText(search)
    return term.isBlank() || normalizeEntrySearchText(option.name).contains(term)
}

fun validateQuickSupplierName(value: String): String? {
    val clean = value.trim().replace(repeatedSpaces, " ")
    return when {
        clean.isEmpty() -> "Informe o nome do Fornecedor."
        clean.length > 200 -> "O nome do Fornecedor pode ter no máximo 200 caracteres."
        else -> null
    }
}

fun normalizeQuickSupplierName(value: String): String =
    value.trim().replace(repeatedSpaces, " ")

fun validateQuickProduct(
    clientId: String,
    name: String,
    unit: String,
    categoryId: String?,
): QuickProductValidation {
    val cleanName = name.trim().replace(repeatedSpaces, " ")
    val cleanUnit = unit.trim().uppercase()
    val error = when {
        cleanName.isEmpty() -> "Informe o nome do Produto."
        cleanName.length > 200 -> "O nome do Produto pode ter no máximo 200 caracteres."
        cleanUnit !in ENTRY_PRODUCT_UNITS -> "Escolha uma Unidade válida."
        clientId.isBlank() || clientId.length > 100 -> "Identificador do Produto novo inválido."
        else -> null
    }
    return QuickProductValidation(
        product = if (error == null) EntryDraftProduct(
            clientId = clientId,
            name = cleanName,
            unit = cleanUnit,
            categoryId = categoryId?.takeIf(String::isNotBlank),
        ) else null,
        error = error,
    )
}

val ENTRY_PRODUCT_UNITS = listOf(
    "UN", "KG", "SC", "CX", "PCT", "FD", "BL", "GL", "PET", "ROLO", "LATA", "BARRA", "PT"
)

fun validateEntryObservation(value: String): String? =
    if (value.length > 2000) "A observação deve ter no máximo 2.000 caracteres." else null

fun entryErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    val known = listOf(
        "Acesso não autorizado.",
        "Dispositivo não autorizado.",
        "Fornecedor inválido ou excluído.",
        "A Entrada precisa de pelo menos um item.",
        "Chave de idempotência é obrigatória.",
        "Chave de idempotência já utilizada.",
        "Data/hora efetiva da Entrada é obrigatória.",
        "A data da Entrada não pode ser futura.",
        "Entrada inválida.",
        "Entrada não encontrada.",
        "Entrada não encontrada ou já excluída.",
        "Já existe um fornecedor ativo com este contato.",
    ).firstOrNull(message::contains)
    if (known != null) return known
    if (message.contains("Quantidade deve ser maior que zero")) {
        return "Todas as quantidades devem ser maiores que zero."
    }
    if (message.contains("Preço não pode ser negativo")) {
        return "Preço unitário não pode ser negativo."
    }
    if (message.contains("Produto inválido, excluído ou pendente de categoria")) {
        return "Há um Produto inválido, excluído ou com cadastro pendente."
    }
    if (message.contains("Já existe um Produto ativo com o nome")) {
        return message.substringAfter("message=").substringBefore(", details=").ifBlank { message }
    }
    if (message.contains("Supabase não está configurado")) {
        return "Supabase não está configurado nesta build."
    }
    return "Não foi possível concluir a Entrada. Tente novamente."
}

fun shouldReconcileEntryFailure(error: Throwable): Boolean =
    error !is PostgrestRestException &&
        !error.message.orEmpty().contains("Supabase não está configurado")

fun entryMatchesUpdate(details: EntryDetails, input: EntryUpdateInput): Boolean {
    if (details.id != input.entryId || details.supplierId != input.supplierId) return false
    if ((details.observation?.trim().orEmpty()) != (input.observation?.trim().orEmpty())) return false
    val sameTime = runCatching {
        entryInstant(details.effectiveAt) == entryInstant(input.effectiveAt)
    }.getOrDefault(details.effectiveAt == input.effectiveAt)
    if (!sameTime || details.items.size != input.items.size) return false

    return details.items.zip(input.items).all { (actual, expected) ->
        actual.productId == expected.productId &&
            abs(actual.quantity - expected.quantity) < 1e-9 &&
            when {
                actual.unitPrice == null && expected.unitPrice == null -> true
                actual.unitPrice != null && expected.unitPrice != null ->
                    abs(actual.unitPrice - expected.unitPrice) < 1e-9
                else -> false
            }
    }
}

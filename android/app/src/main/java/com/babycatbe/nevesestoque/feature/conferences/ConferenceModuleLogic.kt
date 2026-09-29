package com.babycatbe.nevesestoque.feature.conferences

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

private val conferenceLocale = Locale.forLanguageTag("pt-BR")

data class CategoryConferenceFormErrors(
    val date: String? = null,
    val responsible: String? = null,
    val observation: String? = null,
    val quantities: Map<String, String> = emptyMap(),
) {
    val hasErrors: Boolean
        get() = date != null || responsible != null || observation != null || quantities.isNotEmpty()
}

data class ValidatedCategoryConferenceDraft(
    val effectiveAt: String?,
    val responsible: String?,
    val observation: String?,
    val items: List<CategoryConferenceWriteItem>,
    val errors: CategoryConferenceFormErrors,
)

fun validateCategoryConferenceDraft(
    products: List<ConferenceProduct>,
    date: String,
    responsible: String,
    observation: String,
    quantities: Map<String, String>,
    effectiveAtBuilder: (String) -> String = ::buildCategoryConferenceEffectiveAt,
): ValidatedCategoryConferenceDraft {
    val dateError = categoryConferenceDateError(date)
    val cleanResponsible = cleanConferenceResponsible(responsible)
    val responsibleError = conferenceResponsibleError(responsible)
    val observationError = if (observation.length > 2000) {
        "A observação deve ter no máximo 2.000 caracteres."
    } else {
        null
    }

    val quantityErrors = linkedMapOf<String, String>()
    val items = products.map { product ->
        val parsed = parseConferenceQuantity(
            quantities[product.id].orEmpty(),
            "Quantidade de " + product.name,
        )
        parsed.second?.let { quantityErrors[product.id] = it }
        CategoryConferenceWriteItem(product.id, parsed.first ?: 0.0)
    }

    val errors = CategoryConferenceFormErrors(
        date = dateError,
        responsible = responsibleError,
        observation = observationError,
        quantities = quantityErrors,
    )

    return ValidatedCategoryConferenceDraft(
        effectiveAt = if (!errors.hasErrors) effectiveAtBuilder(date) else null,
        responsible = cleanResponsible.takeIf { responsibleError == null },
        observation = observation.trim().ifBlank { null },
        items = items,
        errors = errors,
    )
}

fun validateCategoryConferenceEdit(
    details: ConferenceDetails,
    date: String,
    responsible: String,
    observation: String,
    quantities: Map<String, String>,
): ValidatedCategoryConferenceDraft =
    validateCategoryConferenceDraft(
        products = details.items.map {
            ConferenceProduct(it.productId, it.productName, it.unit, it.position)
        },
        date = date,
        responsible = responsible,
        observation = observation,
        quantities = quantities,
        effectiveAtBuilder = { buildEditedConferenceEffectiveAt(it, details.effectiveAt) },
    )

fun cleanConferenceResponsible(value: String): String = value.trim().replace(Regex("\\s+"), " ")

/** Mesma regra usada ao salvar; também valida o campo ao avançar com Enter/Próximo. */
fun conferenceResponsibleError(value: String): String? {
    val clean = cleanConferenceResponsible(value)
    return when {
        clean.isEmpty() -> "Informe o responsável pela contagem física."
        clean.length > 160 -> "O responsável pode ter no máximo 160 caracteres."
        else -> null
    }
}

fun categoryConferenceDateError(value: String, today: LocalDate = LocalDate.now()): String? {
    val date = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        return "Informe uma data válida."
    }
    return if (date > today) "A data não pode ser futura." else null
}

fun buildCategoryConferenceEffectiveAt(
    dateValue: String,
    referenceInstant: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    categoryConferenceDateError(dateValue)?.let(::error)
    val date = LocalDate.parse(dateValue)
    val localTime = referenceInstant.atZone(zoneId).toLocalTime()
    return date.atTime(localTime).atZone(zoneId).toOffsetDateTime().toString()
}

fun buildEditedConferenceEffectiveAt(
    dateValue: String,
    originalEffectiveAt: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    categoryConferenceDateError(dateValue)?.let(::error)
    val date = LocalDate.parse(dateValue)
    val originalTime = OffsetDateTime.parse(originalEffectiveAt)
        .toInstant()
        .atZone(zoneId)
        .toLocalTime()
    return date.atTime(originalTime).atZone(zoneId).toOffsetDateTime().toString()
}

fun conferenceLocalDate(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        OffsetDateTime.parse(value).toInstant().atZone(zoneId).toLocalDate().toString()
    }.getOrDefault(value.take(10))

fun conferenceDateInput(value: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
    conferenceLocalDate(value, zoneId)

fun formatConferenceDateShort(value: String): String =
    runCatching {
        OffsetDateTime.parse(value).toInstant().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(conferenceLocale))
    }.getOrDefault(value)

fun formatConferenceDateLong(value: String): String =
    runCatching {
        OffsetDateTime.parse(value).toInstant().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(conferenceLocale))
    }.getOrDefault(value)

fun formatConferenceTime(value: String): String =
    runCatching {
        OffsetDateTime.parse(value).toInstant().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm", conferenceLocale))
    }.getOrDefault("")

fun formatConferenceNumber(value: Double): String =
    if (abs(value % 1.0) < 1e-9) value.toLong().toString()
    else NumberFormat.getNumberInstance(conferenceLocale).apply {
        maximumFractionDigits = 6
        minimumFractionDigits = 0
    }.format(value)

fun categoryConferenceUpdateMatches(
    details: ConferenceDetails,
    input: CategoryConferenceUpdateInput,
): Boolean {
    if (details.id != input.conferenceId) return false
    if (details.physicalResponsible.trim() != input.physicalResponsible.trim()) return false
    if (details.observation?.trim().orEmpty() != input.observation?.trim().orEmpty()) return false

    val sameEffectiveAt = runCatching {
        OffsetDateTime.parse(details.effectiveAt).toInstant() ==
            OffsetDateTime.parse(input.effectiveAt).toInstant()
    }.getOrDefault(details.effectiveAt == input.effectiveAt)
    if (!sameEffectiveAt || details.items.size != input.items.size) return false

    return details.items.zip(input.items).all { (actual, expected) ->
        actual.productId == expected.productId &&
            abs(actual.quantity - expected.quantity) < 1e-9
    }
}

fun shouldReconcileConferenceFailure(error: Throwable): Boolean =
    error !is PostgrestRestException &&
        !error.message.orEmpty().contains("Supabase não está configurado")

fun conferenceModuleErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    val known = listOf(
        "Acesso não autorizado.",
        "Dispositivo não autorizado.",
        "Categoria inválida ou excluída.",
        "A categoria não possui produtos ativos para conferir.",
        "A Conferência da categoria precisa conter todos os produtos ativos.",
        "Responsável físico inválido.",
        "Data/hora efetiva da Conferência é obrigatória.",
        "A data da Conferência não pode ser futura.",
        "Chave de idempotência é obrigatória.",
        "Chave de idempotência já utilizada.",
        "Conferência de categoria não encontrada.",
        "A Conferência não possui itens para corrigir.",
        "A correção precisa manter todos os produtos da Conferência original.",
        "A correção não pode alterar os produtos da Conferência original.",
        "Conferência não encontrada ou já excluída.",
    ).firstOrNull(message::contains)
    if (known != null) return known
    if (message.contains("Quantidade não pode ser negativa")) {
        return "A quantidade da Conferência não pode ser negativa."
    }
    if (message.contains("Produto repetido na Conferência")) {
        return "Um Produto não pode aparecer duas vezes na mesma Conferência."
    }
    if (message.contains("Produto inválido, excluído ou fora da categoria")) {
        return "Há um Produto inválido, excluído ou fora da Categoria."
    }
    if (message.contains("Supabase não está configurado")) {
        return "Supabase não está configurado nesta build."
    }
    return "Não foi possível concluir a Conferência. Tente novamente."
}

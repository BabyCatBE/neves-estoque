package com.babycatbe.nevesestoque.feature.conferences

data class ProductConferenceFormErrors(
    val responsible: String? = null,
    val quantity: String? = null,
) {
    val hasErrors: Boolean
        get() = responsible != null || quantity != null
}

data class ValidatedProductConferenceDraft(
    val responsible: String?,
    val quantity: Double?,
    val observation: String?,
    val errors: ProductConferenceFormErrors,
)

enum class ConferenceConsumptionWarningKind {
    Above,
    Below,
    Inconsistent,
}

data class ConferenceConsumptionWarning(
    val productId: String,
    val kind: ConferenceConsumptionWarningKind,
    val expectedConsumption: Double,
    val actualConsumption: Double,
    val difference: Double,
    val differencePercent: Double?,
    val intervalDays: Double,
)

fun validateProductConferenceDraft(
    responsible: String,
    quantity: String,
    observation: String,
): ValidatedProductConferenceDraft {
    val cleanResponsible = responsible.trim().replace(Regex("\\s+"), " ")
    val responsibleError = when {
        cleanResponsible.isEmpty() -> "Informe o responsável pela contagem física."
        cleanResponsible.length > 160 -> "O responsável pode ter no máximo 160 caracteres."
        else -> null
    }
    val quantityResult = parseConferenceQuantity(quantity, "Nova quantidade")

    return ValidatedProductConferenceDraft(
        responsible = cleanResponsible.takeIf { responsibleError == null },
        quantity = quantityResult.first,
        observation = observation.trim().ifBlank { null },
        errors = ProductConferenceFormErrors(
            responsible = responsibleError,
            quantity = quantityResult.second,
        ),
    )
}

fun parseConferenceQuantity(
    value: String,
    label: String = "Quantidade",
): Pair<Double?, String?> {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isEmpty()) return null to "$label é obrigatória."
    if (!Regex("^\\d+(?:\\.\\d+)?$").matches(normalized)) {
        return null to "$label inválida."
    }

    val parsed = normalized.toDoubleOrNull()
    return if (parsed == null || !parsed.isFinite() || parsed < 0) {
        null to "$label não pode ser negativa."
    } else {
        parsed to null
    }
}

fun evaluateConferenceConsumption(
    productId: String,
    expectedDailyAverage: Double,
    intervalDays: Double,
    previousQuantity: Double,
    entriesQuantity: Double,
    candidateQuantity: Double,
): ConferenceConsumptionWarning? {
    if (
        !expectedDailyAverage.isFinite() ||
        expectedDailyAverage < 0 ||
        !intervalDays.isFinite() ||
        intervalDays <= 0
    ) {
        return null
    }

    val expectedConsumption = expectedDailyAverage * intervalDays
    val actualConsumption = previousQuantity + entriesQuantity - candidateQuantity
    val difference = actualConsumption - expectedConsumption

    if (actualConsumption < 0) {
        return ConferenceConsumptionWarning(
            productId = productId,
            kind = ConferenceConsumptionWarningKind.Inconsistent,
            expectedConsumption = expectedConsumption,
            actualConsumption = actualConsumption,
            difference = difference,
            differencePercent = if (expectedConsumption > 0) {
                (difference / expectedConsumption) * 100
            } else {
                null
            },
            intervalDays = intervalDays,
        )
    }

    if (expectedConsumption == 0.0) {
        if (actualConsumption == 0.0) return null
        return ConferenceConsumptionWarning(
            productId = productId,
            kind = ConferenceConsumptionWarningKind.Above,
            expectedConsumption = expectedConsumption,
            actualConsumption = actualConsumption,
            difference = difference,
            differencePercent = null,
            intervalDays = intervalDays,
        )
    }

    val differencePercent = (difference / expectedConsumption) * 100
    if (kotlin.math.abs(differencePercent) < 50) return null

    return ConferenceConsumptionWarning(
        productId = productId,
        kind = if (differencePercent > 0) {
            ConferenceConsumptionWarningKind.Above
        } else {
            ConferenceConsumptionWarningKind.Below
        },
        expectedConsumption = expectedConsumption,
        actualConsumption = actualConsumption,
        difference = difference,
        differencePercent = differencePercent,
        intervalDays = intervalDays,
    )
}

fun conferenceErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    val known = listOf(
        "Acesso não autorizado.",
        "Dispositivo não autorizado.",
        "Produto inválido ou excluído.",
        "Responsável físico inválido.",
        "Data/hora efetiva da Conferência é obrigatória.",
        "Chave de idempotência é obrigatória.",
        "Chave de idempotência já utilizada.",
    ).firstOrNull(message::contains)

    if (known != null) return known
    if (message.contains("Quantidade não pode ser negativa.")) {
        return "A quantidade da Conferência não pode ser negativa."
    }
    if (message.contains("Supabase não está configurado")) {
        return "Supabase não está configurado nesta build."
    }
    return "Não foi possível concluir a Conferência. Tente novamente."
}

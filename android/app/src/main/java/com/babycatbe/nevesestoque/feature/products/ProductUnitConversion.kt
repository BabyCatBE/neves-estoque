package com.babycatbe.nevesestoque.feature.products

import io.github.jan.supabase.postgrest.exception.PostgrestRestException

data class ProductUnitConversionDraft(
    val productId: String,
    val newUnit: String,
    val oldQuantity: Double,
    val newQuantity: Double,
)

data class ConversionDecimalResult(
    val value: Double?,
    val error: String?,
)

fun parsePositiveConversionQuantity(
    value: String,
    label: String,
): ConversionDecimalResult {
    val normalized = value.trim().replace(',', '.')
    if (!Regex("""^\d+(?:\.\d+)?$""").matches(normalized)) {
        return ConversionDecimalResult(null, "$label inválida.")
    }

    val parsed = normalized.toDoubleOrNull()
    return if (parsed == null || !parsed.isFinite()) {
        ConversionDecimalResult(null, "$label inválida.")
    } else if (parsed <= 0) {
        ConversionDecimalResult(null, "$label deve ser maior que zero.")
    } else {
        ConversionDecimalResult(parsed, null)
    }
}

fun calculateUnitConversionFactor(
    oldQuantity: Double,
    newQuantity: Double,
): Double {
    require(oldQuantity.isFinite() && oldQuantity > 0) {
        "A quantidade da unidade atual deve ser maior que zero."
    }
    require(newQuantity.isFinite() && newQuantity > 0) {
        "A quantidade da nova unidade deve ser maior que zero."
    }
    return newQuantity / oldQuantity
}

fun convertQuantityForUnit(value: Double?, factor: Double): Double? =
    value?.times(factor)

fun convertPriceForUnit(value: Double?, factor: Double): Double? =
    value?.div(factor)

fun productUnitConversionErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    val code = (error as? PostgrestRestException)?.code

    val known = listOf(
        "Acesso não autorizado.",
        "Dispositivo não autorizado.",
        "Produto inválido.",
        "Produto não encontrado.",
        "A equivalência deve usar quantidades maiores que zero.",
        "Unidade inválida.",
        "Escolha uma unidade diferente da atual.",
    ).firstOrNull(message::contains)

    if (known != null) return known
    if (code == "22P02") return "A equivalência informada é inválida."
    if (message.contains("Supabase não está configurado")) {
        return "Supabase não está configurado nesta build."
    }
    return "Não foi possível alterar a unidade. Tente novamente."
}

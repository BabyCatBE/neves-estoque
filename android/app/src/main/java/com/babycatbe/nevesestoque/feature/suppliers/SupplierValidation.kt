package com.babycatbe.nevesestoque.feature.suppliers

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.text.Normalizer
import java.util.Locale

data class SupplierWeekday(val value: Int, val label: String)

val SUPPLIER_WEEKDAYS = listOf(
    SupplierWeekday(1, "Segunda-feira"),
    SupplierWeekday(2, "Terça-feira"),
    SupplierWeekday(3, "Quarta-feira"),
    SupplierWeekday(4, "Quinta-feira"),
    SupplierWeekday(5, "Sexta-feira"),
    SupplierWeekday(6, "Sábado"),
    SupplierWeekday(7, "Domingo"),
)

data class SupplierFormErrors(
    val name: String? = null,
    val company: String? = null,
    val phone: String? = null,
    val observation: String? = null,
    val purchaseFrequencyDays: String? = null,
    val preferredOrderWeekday: String? = null,
    val averageDeliveryDays: String? = null,
    val safetyMarginDays: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(
            name,
            company,
            phone,
            observation,
            purchaseFrequencyDays,
            preferredOrderWeekday,
            averageDeliveryDays,
            safetyMarginDays,
        ).any { it != null }
}

data class OptionalIntegerResult(val value: Int?, val error: String?)

data class ValidatedSupplierDraft(
    val input: SupplierMutationInput?,
    val errors: SupplierFormErrors,
)

fun supplierPhoneDigits(value: String?): String =
    value.orEmpty().replace(Regex("\\D"), "")

fun isSupplierPhoneComplete(value: String?): Boolean =
    supplierPhoneDigits(value).length == 11

fun formatSupplierPhoneInput(value: String): String {
    val digits = supplierPhoneDigits(value).take(11)
    if (digits.isEmpty()) return ""
    if (digits.length <= 2) return "(" + digits

    var formatted = "(" + digits.take(2) + ")"
    if (digits.length >= 3) formatted += " " + digits.substring(2, 3)
    if (digits.length >= 4) formatted += " " + digits.substring(3, minOf(7, digits.length))
    if (digits.length >= 8) formatted += "-" + digits.substring(7, minOf(11, digits.length))
    return formatted
}

fun formatSupplierPhoneDisplay(value: String?): String =
    when {
        value.isNullOrBlank() -> "Não informado"
        isSupplierPhoneComplete(value) -> formatSupplierPhoneInput(value)
        else -> value
    }

fun parseOptionalSupplierInteger(
    raw: String,
    label: String,
    min: Int,
    max: Int,
): OptionalIntegerResult {
    val value = raw.trim()
    if (value.isEmpty()) return OptionalIntegerResult(null, null)
    if (!Regex("^\\d+$").matches(value)) {
        return OptionalIntegerResult(null, "$label deve ser um número inteiro.")
    }
    val parsed = value.toIntOrNull()
    return if (parsed == null || parsed !in min..max) {
        OptionalIntegerResult(null, "$label deve ficar entre $min e $max.")
    } else {
        OptionalIntegerResult(parsed, null)
    }
}

fun validateSupplierDraft(
    name: String,
    company: String,
    phone: String,
    observation: String,
    purchaseFrequencyDays: String,
    preferredOrderWeekday: String,
    averageDeliveryDays: String,
    safetyMarginDays: String,
): ValidatedSupplierDraft {
    val cleanName = name.trim()
    val cleanCompany = company.trim()
    val cleanPhone = supplierPhoneDigits(phone)
    val cleanObservation = observation.trim()

    val frequency = parseOptionalSupplierInteger(
        purchaseFrequencyDays,
        "Frequência de compra",
        1,
        3650,
    )
    val weekday = parseOptionalSupplierInteger(
        preferredOrderWeekday,
        "Dia preferencial",
        1,
        7,
    )
    val delivery = parseOptionalSupplierInteger(
        averageDeliveryDays,
        "Prazo de entrega",
        0,
        365,
    )
    val safety = parseOptionalSupplierInteger(
        safetyMarginDays,
        "Margem de segurança",
        0,
        365,
    )

    val errors = SupplierFormErrors(
        name = when {
            cleanName.isEmpty() -> "Informe o contato ou vendedor."
            cleanName.length > 200 -> "O contato deve ter no máximo 200 caracteres."
            else -> null
        },
        company = when {
            cleanCompany.isEmpty() -> "Informe a empresa."
            cleanCompany.length > 200 -> "A empresa deve ter no máximo 200 caracteres."
            else -> null
        },
        phone = if (cleanPhone.length != 11) {
            "Informe exatamente 11 dígitos no telefone."
        } else {
            null
        },
        observation = if (observation.length > 2000) {
            "A observação deve ter no máximo 2.000 caracteres."
        } else {
            null
        },
        purchaseFrequencyDays = frequency.error,
        preferredOrderWeekday = weekday.error,
        averageDeliveryDays = delivery.error,
        safetyMarginDays = safety.error,
    )

    return ValidatedSupplierDraft(
        input = if (errors.hasErrors) null else SupplierMutationInput(
            name = cleanName,
            company = cleanCompany,
            phone = cleanPhone,
            observation = cleanObservation.ifBlank { null },
            purchaseFrequencyDays = frequency.value,
            preferredOrderWeekday = weekday.value,
            averageDeliveryDays = delivery.value,
            safetyMarginDays = safety.value,
        ),
        errors = errors,
    )
}

fun supplierWeekdayLabel(value: Int?): String =
    SUPPLIER_WEEKDAYS.firstOrNull { it.value == value }?.label ?: "Não informado"

fun normalizeSupplierSearchText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .lowercase(Locale.forLanguageTag("pt-BR"))
        .trim()
        .replace(Regex("\\s+"), " ")

fun matchesSupplierSearch(supplier: SupplierDetails, search: String): Boolean {
    val term = normalizeSupplierSearchText(search)
    if (term.isEmpty()) return true
    return listOf(
        supplier.name,
        supplier.company.orEmpty(),
        supplier.phone.orEmpty(),
        formatSupplierPhoneDisplay(supplier.phone),
    ).any { normalizeSupplierSearchText(it).contains(term) }
}

fun supplierErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    val code = (error as? PostgrestRestException)?.code

    if (
        code == "23505" ||
        message.contains("suppliers_active_name_uq") ||
        message.contains("Já existe um fornecedor ativo com este contato")
    ) {
        return "Já existe um Fornecedor ativo com este contato."
    }

    val known = listOf(
        "Acesso não autorizado.",
        "Fornecedor inválido.",
        "Fornecedor não encontrado.",
        "Fornecedor não encontrado na lixeira.",
        "Prazo de restauração expirado.",
        "Contato do fornecedor inválido.",
        "Telefone do fornecedor deve ter exatamente 11 dígitos.",
    ).firstOrNull(message::contains)

    if (known != null) return known
    if (message.contains("Supabase não está configurado")) {
        return "Supabase não está configurado nesta build."
    }
    return "Não foi possível concluir a operação com o Fornecedor. Tente novamente."
}

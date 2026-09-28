package com.babycatbe.nevesestoque.feature.products

import io.github.jan.supabase.postgrest.exception.PostgrestRestException

val PRODUCT_UNITS = listOf(
    "UN", "KG", "SC", "CX", "PCT", "FD", "BL", "GL", "PET", "ROLO", "LATA", "BARRA", "PT"
)

private val repeatedSpaces = Regex("\\s+")
private val decimalPattern = Regex("^\\d+(?:[.,]\\d+)?$")

data class ProductFormErrors(
    val name: String? = null,
    val category: String? = null,
    val unit: String? = null,
    val initialStock: String? = null,
    val initialPrice: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(name, category, unit, initialStock, initialPrice).any { it != null }
}

data class ValidatedProductDraft(
    val input: ProductMutationInput?,
    val errors: ProductFormErrors,
)

fun normalizeProductName(value: String): String = value.trim().replace(repeatedSpaces, " ")

fun normalizeCategoryName(value: String): String = value.trim()

fun validateProductDraft(
    name: String,
    categoryId: String,
    unit: String,
    initialStock: String,
    initialPrice: String,
): ValidatedProductDraft {
    val normalizedName = normalizeProductName(name)
    var errors = ProductFormErrors(
        name = when {
            normalizedName.isEmpty() -> "Informe o nome do Produto."
            normalizedName.length > 200 -> "O nome do Produto pode ter no máximo 200 caracteres."
            else -> null
        },
        category = if (categoryId.isBlank()) "Escolha a Categoria." else null,
        unit = if (unit !in PRODUCT_UNITS) "Escolha a Unidade." else null,
    )

    val stock = parseOptionalNonNegativeDecimal(initialStock, "Estoque inicial")
    val price = parseOptionalNonNegativeDecimal(initialPrice, "Preço inicial")
    errors = errors.copy(initialStock = stock.error, initialPrice = price.error)

    return ValidatedProductDraft(
        input = if (errors.hasErrors) null else ProductMutationInput(
            name = normalizedName,
            categoryId = categoryId,
            unit = unit,
            initialStockQuantity = stock.value,
            initialPrice = price.value,
        ),
        errors = errors,
    )
}

data class OptionalDecimalResult(val value: Double?, val error: String?)

fun parseOptionalNonNegativeDecimal(value: String, label: String): OptionalDecimalResult {
    val normalized = value.trim()
    if (normalized.isEmpty()) return OptionalDecimalResult(null, null)
    if (normalized.startsWith("-")) return OptionalDecimalResult(null, "$label não pode ser negativo.")
    if (!decimalPattern.matches(normalized)) return OptionalDecimalResult(null, "$label inválido.")

    val parsed = normalized.replace(',', '.').toDoubleOrNull()
    return if (parsed == null || !parsed.isFinite() || parsed < 0) {
        OptionalDecimalResult(null, "$label inválido.")
    } else {
        OptionalDecimalResult(parsed, null)
    }
}

fun validateCategoryName(value: String): String? {
    val normalized = normalizeCategoryName(value)
    return when {
        normalized.isEmpty() -> "Informe o nome da Categoria."
        normalized.length > 120 -> "O nome da Categoria pode ter no máximo 120 caracteres."
        else -> null
    }
}

fun productErrorMessage(error: Throwable): String {
    val code = (error as? PostgrestRestException)?.code
    val message = error.message.orEmpty()
    if (code == "23505" || message.contains("23505")) {
        return "Já existe um Produto ativo com esse nome."
    }

    val known = listOf(
        "Nome do produto inválido.",
        "Categoria é obrigatória.",
        "Categoria inválida ou excluída.",
        "Unidade inválida.",
        "Estoque inicial não pode ser negativo.",
        "Preço inicial não pode ser negativo.",
        "Estoque inicial inválido.",
        "Preço inicial inválido.",
        "Produto inválido.",
        "Produto não encontrado.",
        "Acesso não autorizado.",
    ).firstOrNull(message::contains)

    return known ?: "Não foi possível salvar o Produto. Tente novamente."
}

fun categoryErrorMessage(error: Throwable): String {
    val code = (error as? PostgrestRestException)?.code
    val message = error.message.orEmpty()
    if (code == "23505" || message.contains("23505")) {
        return "Já existe uma Categoria ativa com esse nome."
    }
    if (message.contains("Acesso não autorizado.")) return "Acesso não autorizado."
    return "Não foi possível salvar a Categoria. Tente novamente."
}

package com.babycatbe.nevesestoque.feature.products

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MergeInitialPriceSource(val wireValue: String) {
    Survivor("survivor"),
    Absorbed("absorbed"),
    None("none"),
}

data class ProductMergePair(
    val survivor: ProductDetails,
    val absorbed: ProductDetails,
)

data class ProductMergeFactors(
    val survivorFactor: Double,
    val absorbedFactor: Double,
)

data class ProductMergeDraft(
    val productAId: String,
    val productBId: String,
    val finalName: String,
    val finalCategoryId: String,
    val finalUnit: String,
    val survivorEquivalentQuantity: Double?,
    val absorbedEquivalentQuantity: Double?,
    val initialPriceSource: MergeInitialPriceSource,
)

data class ValidatedProductMergeDraft(
    val draft: ProductMergeDraft?,
    val error: String?,
)

data class ProductMergeResult(
    val survivorProductId: String,
    val absorbedProductId: String,
    val entryItemsCount: Int?,
    val conferenceItemsCount: Int?,
    val overlapConferenceCount: Int?,
    val reconciledAfterAmbiguousFailure: Boolean = false,
)

@Serializable
data class ProductMergeRpcResponse(
    @SerialName("survivor_product_id") val survivorProductId: String,
    @SerialName("absorbed_product_id") val absorbedProductId: String,
    @SerialName("entry_items_count") val entryItemsCount: Int = 0,
    @SerialName("conference_items_count") val conferenceItemsCount: Int = 0,
    @SerialName("overlap_conference_count") val overlapConferenceCount: Int = 0,
) {
    fun toResult() = ProductMergeResult(
        survivorProductId = survivorProductId,
        absorbedProductId = absorbedProductId,
        entryItemsCount = entryItemsCount,
        conferenceItemsCount = conferenceItemsCount,
        overlapConferenceCount = overlapConferenceCount,
    )
}

fun determineMergePair(a: ProductDetails, b: ProductDetails): ProductMergePair {
    val aIsOlder = a.createdAt < b.createdAt ||
        (a.createdAt == b.createdAt && a.id <= b.id)
    return if (aIsOlder) {
        ProductMergePair(survivor = a, absorbed = b)
    } else {
        ProductMergePair(survivor = b, absorbed = a)
    }
}

fun calculateMergeUnitFactors(
    survivorUnit: String,
    absorbedUnit: String,
    finalUnit: String,
    survivorEquivalentQuantity: Double?,
    absorbedEquivalentQuantity: Double?,
): ProductMergeFactors {
    require(finalUnit == survivorUnit || finalUnit == absorbedUnit) {
        "A unidade final deve ser uma das unidades atuais."
    }

    if (survivorUnit == absorbedUnit) {
        return ProductMergeFactors(survivorFactor = 1.0, absorbedFactor = 1.0)
    }

    require(
        survivorEquivalentQuantity != null &&
            survivorEquivalentQuantity.isFinite() &&
            survivorEquivalentQuantity > 0 &&
            absorbedEquivalentQuantity != null &&
            absorbedEquivalentQuantity.isFinite() &&
            absorbedEquivalentQuantity > 0
    ) {
        "Informe a equivalência entre as duas unidades."
    }

    return if (finalUnit == survivorUnit) {
        ProductMergeFactors(
            survivorFactor = 1.0,
            absorbedFactor = survivorEquivalentQuantity / absorbedEquivalentQuantity,
        )
    } else {
        ProductMergeFactors(
            survivorFactor = absorbedEquivalentQuantity / survivorEquivalentQuantity,
            absorbedFactor = 1.0,
        )
    }
}

fun defaultMergeInitialPriceSource(pair: ProductMergePair): MergeInitialPriceSource =
    when {
        pair.survivor.initialPrice != null -> MergeInitialPriceSource.Survivor
        pair.absorbed.initialPrice != null -> MergeInitialPriceSource.Absorbed
        else -> MergeInitialPriceSource.None
    }

fun validateProductMergeDraft(
    pair: ProductMergePair,
    finalName: String,
    finalCategoryId: String,
    finalUnit: String,
    survivorEquivalentQuantity: String,
    absorbedEquivalentQuantity: String,
    initialPriceSource: MergeInitialPriceSource,
): ValidatedProductMergeDraft {
    val cleanName = normalizeProductName(finalName)
    if (cleanName.isEmpty() || cleanName.length > 200) {
        return ValidatedProductMergeDraft(null, "Nome final do Produto inválido.")
    }
    if (finalCategoryId.isBlank()) {
        return ValidatedProductMergeDraft(null, "Escolha a Categoria final.")
    }
    if (finalUnit !in PRODUCT_UNITS) {
        return ValidatedProductMergeDraft(null, "Escolha a Unidade final.")
    }
    if (finalUnit != pair.survivor.unit && finalUnit != pair.absorbed.unit) {
        return ValidatedProductMergeDraft(
            null,
            "A unidade final deve ser uma das unidades atuais.",
        )
    }

    val unitsDiffer = pair.survivor.unit != pair.absorbed.unit
    val survivorQuantity = if (unitsDiffer) {
        val parsed = parsePositiveConversionQuantity(
            survivorEquivalentQuantity,
            "Quantidade em " + pair.survivor.unit,
        )
        if (parsed.error != null || parsed.value == null) {
            return ValidatedProductMergeDraft(
                null,
                parsed.error ?: "Informe a equivalência entre as duas unidades.",
            )
        }
        parsed.value
    } else {
        null
    }
    val absorbedQuantity = if (unitsDiffer) {
        val parsed = parsePositiveConversionQuantity(
            absorbedEquivalentQuantity,
            "Quantidade em " + pair.absorbed.unit,
        )
        if (parsed.error != null || parsed.value == null) {
            return ValidatedProductMergeDraft(
                null,
                parsed.error ?: "Informe a equivalência entre as duas unidades.",
            )
        }
        parsed.value
    } else {
        null
    }

    runCatching {
        calculateMergeUnitFactors(
            survivorUnit = pair.survivor.unit,
            absorbedUnit = pair.absorbed.unit,
            finalUnit = finalUnit,
            survivorEquivalentQuantity = survivorQuantity,
            absorbedEquivalentQuantity = absorbedQuantity,
        )
    }.exceptionOrNull()?.let {
        return ValidatedProductMergeDraft(null, it.message ?: "Equivalência inválida.")
    }

    if (
        initialPriceSource == MergeInitialPriceSource.Survivor &&
        pair.survivor.initialPrice == null
    ) {
        return ValidatedProductMergeDraft(
            null,
            "O cadastro mais antigo não possui preço inicial.",
        )
    }
    if (
        initialPriceSource == MergeInitialPriceSource.Absorbed &&
        pair.absorbed.initialPrice == null
    ) {
        return ValidatedProductMergeDraft(
            null,
            "O cadastro mais novo não possui preço inicial.",
        )
    }

    return ValidatedProductMergeDraft(
        draft = ProductMergeDraft(
            productAId = pair.survivor.id,
            productBId = pair.absorbed.id,
            finalName = cleanName,
            finalCategoryId = finalCategoryId,
            finalUnit = finalUnit,
            survivorEquivalentQuantity = survivorQuantity,
            absorbedEquivalentQuantity = absorbedQuantity,
            initialPriceSource = initialPriceSource,
        ),
        error = null,
    )
}

fun matchesMergeSearch(value: String, search: String): Boolean {
    val term = normalizeMergeSearch(search)
    return term.isEmpty() || normalizeMergeSearch(value).contains(term)
}

private fun normalizeMergeSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .lowercase(Locale.forLanguageTag("pt-BR"))
        .trim()
        .replace(Regex("\\s+"), " ")

fun isMergeAppliedSnapshot(
    input: ProductMergeDraft,
    pair: ProductMergePair,
    activeProducts: List<ProductListItem>,
    survivorDetails: ProductDetails,
): Boolean {
    if (activeProducts.any { it.id == pair.absorbed.id }) return false
    val survivor = activeProducts.firstOrNull { it.id == pair.survivor.id } ?: return false
    return normalizeProductName(survivor.name) == normalizeProductName(input.finalName) &&
        survivor.categoryId == input.finalCategoryId &&
        survivor.unit == input.finalUnit &&
        survivorDetails.id == pair.survivor.id &&
        survivorDetails.stockRequiresConference
}

fun productMergeErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    val code = (error as? PostgrestRestException)?.code

    val known = listOf(
        "Acesso não autorizado.",
        "Dispositivo não autorizado.",
        "Escolha dois produtos diferentes para mesclar.",
        "O primeiro produto não está ativo.",
        "O segundo produto não está ativo.",
        "Produtos com cadastro pendente não podem ser mesclados.",
        "Nome final do produto inválido.",
        "Categoria final inválida ou excluída.",
        "Já existe outro produto ativo com esse nome.",
        "Unidade final inválida.",
        "A unidade final deve ser uma das unidades atuais dos produtos.",
        "Informe a equivalência entre as duas unidades.",
        "Referência inicial de preço inválida.",
        "O produto mais antigo não possui preço inicial para manter.",
        "O produto absorvido não possui preço inicial para manter.",
    ).firstOrNull(message::contains)

    if (known != null) return known
    if (code == "23505") return "Já existe outro Produto ativo com esse nome."
    if (message.contains("Supabase não está configurado")) {
        return "Supabase não está configurado nesta build."
    }
    return "Não foi possível mesclar os Produtos. Tente novamente."
}

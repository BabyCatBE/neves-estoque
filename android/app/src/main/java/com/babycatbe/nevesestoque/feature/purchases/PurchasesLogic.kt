package com.babycatbe.nevesestoque.feature.purchases

import com.babycatbe.nevesestoque.feature.products.ProductUsageInsights
import com.babycatbe.nevesestoque.feature.products.normalizeProductSearch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/*
 * Porte fiel de src/features/purchases/lib/purchaseProjection.ts e das regras do editor
 * de lista da Web. Compras é uma simulação temporária: nada é salvo, o estoque não muda
 * e nenhuma relação Produto–Fornecedor é criada.
 */

enum class PurchaseRisk { Protected, Vulnerable, Risk, Unknown }

enum class PurchaseProjectionStatus {
    Recommended,
    StockSufficient,
    InsufficientHistory,
    MissingStock,
    MissingSupplier,
    MissingSupplierConfig,
    NoConsumption,
    OtherSupplier,
}

data class PurchaseProjection(
    val status: PurchaseProjectionStatus,
    val suggestedQuantity: Double? = null,
    val targetStock: Double? = null,
    val cycleDays: Double? = null,
    val daysUntilNextOrder: Int? = null,
    val coverageDays: Double? = null,
    val risk: PurchaseRisk = PurchaseRisk.Unknown,
)

data class PurchaseProjectionInput(
    val usageReady: Boolean,
    val dailyAverage: Double?,
    val currentQuantity: Double?,
    val unit: String,
    val hasSupplier: Boolean,
    val supplierActive: Boolean,
    val purchaseFrequencyDays: Int?,
    val preferredOrderWeekday: Int?,
    val deliveryDays: Int?,
    val safetyMarginDays: Int?,
    /** Dia da semana de hoje no aparelho: 1 = segunda … 7 = domingo (mesma convenção da Web). */
    val todayWeekday: Int,
)

/** Frequência semanal com dia preferencial usa a próxima ocorrência real; hoje conta como a próxima semana. */
fun daysUntilNextOrder(purchaseFrequencyDays: Int, preferredOrderWeekday: Int?, todayWeekday: Int): Int {
    if (purchaseFrequencyDays == 7 && preferredOrderWeekday != null && preferredOrderWeekday in 1..7) {
        val difference = ((preferredOrderWeekday - todayWeekday) % 7 + 7) % 7
        return if (difference == 0) 7 else difference
    }
    return purchaseFrequencyDays
}

fun classifyPurchaseRisk(coverageDays: Double?, deliveryDays: Int?, safetyMarginDays: Int?): PurchaseRisk {
    if (coverageDays == null || deliveryDays == null || safetyMarginDays == null) return PurchaseRisk.Unknown
    return when {
        coverageDays < deliveryDays -> PurchaseRisk.Risk
        coverageDays < deliveryDays + safetyMarginDays -> PurchaseRisk.Vulnerable
        else -> PurchaseRisk.Protected
    }
}

/** KG arredonda para cima em até 2 casas; demais unidades sempre para cima no inteiro. */
fun roundPurchaseSuggestion(value: Double, unit: String): Double {
    if (value <= 0.0) return 0.0
    return if (unit == "KG") {
        ceil((value - JS_EPSILON) * 100.0) / 100.0
    } else {
        ceil(value - JS_EPSILON)
    }
}

private const val JS_EPSILON = 2.220446049250313E-16

fun calculatePurchaseProjection(input: PurchaseProjectionInput): PurchaseProjection {
    if (!input.usageReady) return PurchaseProjection(PurchaseProjectionStatus.InsufficientHistory)

    val dailyAverage = input.dailyAverage
    if (dailyAverage == null || dailyAverage <= 0.0) return PurchaseProjection(PurchaseProjectionStatus.NoConsumption)

    val currentQuantity = input.currentQuantity
    if (currentQuantity == null || currentQuantity < 0.0) return PurchaseProjection(PurchaseProjectionStatus.MissingStock)

    val coverageDays = currentQuantity / dailyAverage
    val risk = classifyPurchaseRisk(coverageDays, input.deliveryDays, input.safetyMarginDays)

    if (!input.hasSupplier || !input.supplierActive) {
        return PurchaseProjection(PurchaseProjectionStatus.MissingSupplier, coverageDays = coverageDays, risk = risk)
    }

    val frequency = input.purchaseFrequencyDays
    val delivery = input.deliveryDays
    val margin = input.safetyMarginDays
    if (frequency == null || delivery == null || margin == null) {
        return PurchaseProjection(PurchaseProjectionStatus.MissingSupplierConfig, coverageDays = coverageDays, risk = risk)
    }

    val untilNextOrder = daysUntilNextOrder(frequency, input.preferredOrderWeekday, input.todayWeekday)
    val cycleDays = (untilNextOrder + delivery + margin).toDouble()
    val targetStock = dailyAverage * cycleDays
    val suggestion = roundPurchaseSuggestion(max(0.0, targetStock - currentQuantity), input.unit)

    return PurchaseProjection(
        status = if (suggestion > 0.0) PurchaseProjectionStatus.Recommended else PurchaseProjectionStatus.StockSufficient,
        suggestedQuantity = suggestion,
        targetStock = targetStock,
        cycleDays = cycleDays,
        daysUntilNextOrder = untilNextOrder,
        coverageDays = coverageDays,
        risk = risk,
    )
}

fun todayWeekday(day: DayOfWeek): Int = day.value

// ---------- Itens de Compras ----------

data class PurchaseProduct(
    val productId: String,
    val productName: String,
    val unit: String,
    val categoryId: String?,
    val sortOrder: Int?,
    val currentQuantity: Double?,
    val currentSupplierId: String?,
    val historicalSupplierIds: Set<String>,
    val usageInsights: ProductUsageInsights?,
    val projection: PurchaseProjection,
)

data class PurchaseListItem(
    val productId: String,
    val productName: String,
    val unit: String,
    val currentQuantity: Double?,
    val projection: PurchaseProjection?,
    val isManualAddition: Boolean = false,
)

fun PurchaseProduct.toListItem(): PurchaseListItem =
    PurchaseListItem(productId, productName, unit, currentQuantity, projection)

private val ptBrCollator = java.text.Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply {
    strength = java.text.Collator.PRIMARY
}

fun comparePurchaseNames(a: String, b: String): Int = ptBrCollator.compare(a, b)

/** Por fornecedor: Produtos com histórico desse fornecedor; se a Entrada mais recente for de outro, a sugestão pertence a ele. */
fun supplierPurchaseProducts(products: List<PurchaseProduct>, supplierId: String): List<PurchaseProduct> =
    products
        .filter { supplierId in it.historicalSupplierIds }
        .map { product ->
            if (product.currentSupplierId == null || product.currentSupplierId == supplierId) {
                product
            } else {
                product.copy(
                    projection = product.projection.copy(
                        status = PurchaseProjectionStatus.OtherSupplier,
                        suggestedQuantity = null,
                        targetStock = null,
                        cycleDays = null,
                        daysUntilNextOrder = null,
                    )
                )
            }
        }

/** Recomendados primeiro, depois ordem alfabética (Por fornecedor, incluindo adições manuais). */
fun sortSupplierItems(items: List<PurchaseListItem>): List<PurchaseListItem> =
    items.sortedWith { a, b ->
        val aRec = a.projection?.status == PurchaseProjectionStatus.Recommended
        val bRec = b.projection?.status == PurchaseProjectionStatus.Recommended
        if (aRec != bRec) (if (aRec) -1 else 1) else comparePurchaseNames(a.productName, b.productName)
    }

private fun riskOrder(risk: PurchaseRisk): Int = when (risk) {
    PurchaseRisk.Risk -> 0
    PurchaseRisk.Vulnerable -> 1
    PurchaseRisk.Protected -> 2
    PurchaseRisk.Unknown -> 3
}

/** Por estoque: recomendados por risco, cobertura e nome; restante do menor para o maior estoque, sem dados no fim. */
fun stockPurchaseGroups(products: List<PurchaseProduct>): Pair<List<PurchaseListItem>, List<PurchaseListItem>> {
    val recommended = products
        .filter { it.projection.status == PurchaseProjectionStatus.Recommended }
        .sortedWith { a, b ->
            riskOrder(a.projection.risk).compareTo(riskOrder(b.projection.risk)).takeIf { it != 0 }
                ?: (a.projection.coverageDays ?: Double.POSITIVE_INFINITY)
                    .compareTo(b.projection.coverageDays ?: Double.POSITIVE_INFINITY).takeIf { it != 0 }
                ?: comparePurchaseNames(a.productName, b.productName)
        }
        .map { it.toListItem() }

    val remaining = products
        .filter { it.projection.status != PurchaseProjectionStatus.Recommended }
        .sortedWith { a, b ->
            val aq = a.currentQuantity
            val bq = b.currentQuantity
            when {
                aq == null && bq != null -> 1
                aq != null && bq == null -> -1
                else -> (aq ?: 0.0).compareTo(bq ?: 0.0).takeIf { it != 0 }
                    ?: comparePurchaseNames(a.productName, b.productName)
            }
        }
        .map { it.toListItem() }

    return recommended to remaining
}

/** Por categoria: ordem manual da Categoria, depois nome. */
fun categoryPurchaseItems(products: List<PurchaseProduct>, categoryId: String): List<PurchaseListItem> =
    products
        .filter { it.categoryId == categoryId }
        .sortedWith { a, b ->
            (a.sortOrder ?: Int.MAX_VALUE).compareTo(b.sortOrder ?: Int.MAX_VALUE).takeIf { it != 0 }
                ?: comparePurchaseNames(a.productName, b.productName)
        }
        .map { it.toListItem() }

fun matchesPurchaseSearch(parts: List<String?>, search: String): Boolean {
    val term = normalizeProductSearch(search)
    return term.isBlank() || normalizeProductSearch(parts.filterNotNull().joinToString(" ")).contains(term)
}

// ---------- Quantidades e texto do pedido ----------

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

fun parsePurchaseQuantity(value: String): Double? {
    val normalized = value.trim().replace(",", ".")
    if (normalized.isEmpty()) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** A edição seleciona somente quantidades positivas; o checkbox continua podendo ser usado. */
fun selectPurchaseQuantity(selected: Set<String>, productId: String, value: String): Set<String> =
    if ((parsePurchaseQuantity(value) ?: 0.0) > 0.0) selected + productId else selected - productId

/** Valor editável: até 2 casas, vírgula decimal, sem separador de milhar. */
fun formatEditablePurchaseQuantity(value: Double): String =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_EVEN).stripTrailingZeros()
        .toPlainString().replace(".", ",")

fun formatPurchaseNumber(value: Double, maxFractionDigits: Int = 2): String =
    NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = maxFractionDigits }.format(value)

fun formatPurchaseStock(value: Double?, unit: String): String =
    if (value == null) "Sem dados" else "${formatPurchaseNumber(value)} $unit"

fun stepPurchaseQuantity(current: String, direction: Int): String {
    val next = max(0.0, (parsePurchaseQuantity(current) ?: 0.0) + direction)
    return formatEditablePurchaseQuantity(next)
}

sealed interface PurchaseOrderResult {
    data class Ready(val text: String) : PurchaseOrderResult
    data class Invalid(val message: String, val invalidProductIds: Set<String> = emptySet()) : PurchaseOrderResult
}

fun buildPurchaseOrderText(
    title: String,
    items: List<PurchaseListItem>,
    selected: Set<String>,
    quantities: Map<String, String>,
): PurchaseOrderResult {
    val chosen = items.filter { it.productId in selected }
    if (chosen.isEmpty()) return PurchaseOrderResult.Invalid("Selecione pelo menos um produto.")

    val invalid = chosen.filter { (parsePurchaseQuantity(quantities[it.productId].orEmpty()) ?: 0.0) <= 0.0 }
        .map { it.productId }
        .toSet()
    if (invalid.isNotEmpty()) {
        return PurchaseOrderResult.Invalid(
            "Informe uma quantidade maior que zero para todos os itens selecionados.",
            invalid,
        )
    }

    val lines = chosen.map { item ->
        val quantity = formatPurchaseNumber(parsePurchaseQuantity(quantities[item.productId].orEmpty()) ?: 0.0)
        "${item.productName} — $quantity — ${item.unit}"
    }
    return PurchaseOrderResult.Ready((listOf(title, "") + lines).joinToString("\n"))
}

fun purchaseProjectionMessage(item: PurchaseListItem): String {
    val projection = item.projection ?: return ""
    return when (projection.status) {
        PurchaseProjectionStatus.Recommended -> projection.suggestedQuantity?.let {
            val cycle = projection.cycleDays?.let { days -> "${formatPurchaseNumber(days, 1)} dias" } ?: "—"
            "Sugestão: ${formatPurchaseStock(it, item.unit)} · cobertura planejada: $cycle"
        }.orEmpty()
        PurchaseProjectionStatus.StockSufficient -> "Estoque suficiente para o ciclo planejado."
        PurchaseProjectionStatus.InsufficientHistory -> "Histórico insuficiente para recomendação automática."
        PurchaseProjectionStatus.MissingStock -> "Estoque atual ainda não está contabilizado."
        PurchaseProjectionStatus.MissingSupplier -> "Sem fornecedor ativo de referência para projetar."
        PurchaseProjectionStatus.MissingSupplierConfig -> "Complete frequência, prazo e margem do fornecedor."
        PurchaseProjectionStatus.NoConsumption -> "Sem consumo médio positivo para sugerir compra."
        PurchaseProjectionStatus.OtherSupplier -> "A recomendação automática pertence ao fornecedor da Entrada mais recente."
    }
}

fun purchaseRiskLabel(risk: PurchaseRisk): String? = when (risk) {
    PurchaseRisk.Risk -> "Risco de falta"
    PurchaseRisk.Vulnerable -> "Vulnerável a atraso"
    PurchaseRisk.Protected -> "Protegido"
    PurchaseRisk.Unknown -> null
}

/** Sugestão a preencher ao marcar um item recomendado (quando o campo ainda está vazio). */
fun purchaseSuggestionToFill(item: PurchaseListItem?): String? {
    val projection = item?.projection ?: return null
    if (projection.status != PurchaseProjectionStatus.Recommended) return null
    val suggestion = projection.suggestedQuantity ?: return null
    return if (suggestion > 0.0) formatEditablePurchaseQuantity(suggestion) else null
}

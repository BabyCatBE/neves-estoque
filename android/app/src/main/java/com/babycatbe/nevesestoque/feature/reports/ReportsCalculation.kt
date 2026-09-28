package com.babycatbe.nevesestoque.feature.reports

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong

/*
 * Porte fiel de src/features/reports/lib (Web): monthlyStockSnapshot, monthlyStockReport e
 * monthlyStockSeries. Regra oficial: última Conferência física válida até a data de referência
 * + Entradas posteriores até a mesma data. Não inventa consumo, saída, interpolação ou
 * distribuição diária. Datas de referência são dias civis no fuso oficial dos relatórios.
 */

val REPORT_ZONE: ZoneId = ZoneId.of("America/Bahia")

data class HistoricalConferenceFact(
    val id: String,
    val productId: String,
    val effectiveAt: String,
    val createdAt: String,
    val quantity: Double,
)

data class HistoricalEntryFact(
    val id: String,
    val productId: String,
    val effectiveAt: String,
    val createdAt: String,
    val quantity: Double,
    val unitPrice: Double?,
    val position: Int,
)

data class HistoricalReportProduct(
    val id: String,
    val name: String,
    val unit: String,
    val createdAt: String,
    val deletedAt: String?,
    val initialStockQuantity: Double?,
    val initialStockAt: String?,
    val initialPrice: Double?,
    val initialPriceAt: String?,
)

data class HistoricalProductMergeFact(val id: Long, val productId: String, val createdAt: String)

enum class LifecycleAction { SoftDelete, Restore }

data class HistoricalProductLifecycleFact(
    val id: Long,
    val productId: String,
    val action: LifecycleAction,
    val createdAt: String,
)

data class MonthlyStockReportFacts(
    val products: List<HistoricalReportProduct> = emptyList(),
    val conferences: List<HistoricalConferenceFact> = emptyList(),
    val entries: List<HistoricalEntryFact> = emptyList(),
    val merges: List<HistoricalProductMergeFact> = emptyList(),
    val lifecycle: List<HistoricalProductLifecycleFact> = emptyList(),
)

enum class HistoricalQuantitySource {
    Conference, ConferencePlusEntries, InitialStock, InitialStockPlusEntries, EntriesOnly, None,
}

enum class HistoricalPriceSource { Entry, Initial, None }

data class ProductStockSnapshot(
    val referenceDate: String,
    val quantity: Double?,
    val quantitySource: HistoricalQuantitySource,
    val checkpointConferenceId: String?,
    val price: Double?,
    val priceSource: HistoricalPriceSource,
    val value: Double?,
    val hasStockData: Boolean,
    val hasMissingPrice: Boolean,
    val hasExactPhysicalClose: Boolean,
)

data class MonthlyStockProductItem(
    val productId: String,
    val productName: String,
    val unit: String,
    val stockRequiresConference: Boolean,
    val snapshot: ProductStockSnapshot,
)

data class MonthlyStockValueReport(
    val referenceDate: String,
    val totalKnown: Double,
    val productCount: Int,
    val valuedProductCount: Int,
    val missingPriceProductCount: Int,
    val unknownStockProductCount: Int,
    val noStockDataProductCount: Int,
    val hasMissingPrice: Boolean,
    val hasUnknownStock: Boolean,
    val hasMissingStockData: Boolean,
    val hasExactPhysicalClose: Boolean,
    val isEstimatedFromAvailableRecords: Boolean,
    val products: List<MonthlyStockProductItem>,
) {
    val isIncomplete: Boolean get() = hasMissingPrice || hasUnknownStock || hasMissingStockData
}

data class MonthlyStockSeriesPoint(
    val month: String,
    val referenceDate: String,
    val isProvisional: Boolean,
    val report: MonthlyStockValueReport,
)

data class MonthlyStockValueSeries(
    val startMonth: String?,
    val endMonth: String?,
    val points: List<MonthlyStockSeriesPoint>,
)

// ---------- Datas ----------

private fun epochMillis(value: String): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrElse { throw IllegalArgumentException("Data histórica inválida.") }

/** Dia civil (yyyy-MM-dd) do instante no fuso oficial dos relatórios. */
fun historicalDateKey(value: String): String =
    runCatching { OffsetDateTime.parse(value).atZoneSameInstant(REPORT_ZONE).toLocalDate().toString() }
        .getOrElse { throw IllegalArgumentException("Data histórica inválida.") }

fun reportCurrentDateKey(now: Instant = Instant.now()): String =
    now.atZone(REPORT_ZONE).toLocalDate().toString()

private fun assertReferenceDate(value: String) {
    require(Regex("""^\d{4}-\d{2}-\d{2}$""").matches(value)) { "Data de referência inválida." }
    runCatching { LocalDate.parse(value) }.getOrElse { throw IllegalArgumentException("Data de referência inválida.") }
}

private fun isVisibleAtReference(effectiveAt: String, referenceDate: String): Boolean =
    historicalDateKey(effectiveAt) <= referenceDate

fun lastDayOfMonth(monthKey: String): String = parseMonth(monthKey).atEndOfMonth().toString()

private fun parseMonth(value: String): YearMonth {
    require(Regex("""^\d{4}-\d{2}$""").matches(value)) { "Mês inválido." }
    return runCatching { YearMonth.parse(value) }.getOrElse { throw IllegalArgumentException("Mês inválido.") }
}

/** Mesmo arredondamento da Web: Math.round((valor + Number.EPSILON) * 100) / 100. */
private fun roundMoney(value: Double): Double = ((value + JS_EPSILON) * 100.0).roundToLong() / 100.0

private const val JS_EPSILON = 2.220446049250313E-16

// ---------- Snapshot por Produto ----------

private val conferenceNewestFirst = Comparator<HistoricalConferenceFact> { a, b ->
    epochMillis(b.effectiveAt).compareTo(epochMillis(a.effectiveAt)).takeIf { it != 0 }
        ?: epochMillis(b.createdAt).compareTo(epochMillis(a.createdAt)).takeIf { it != 0 }
        ?: b.id.compareTo(a.id)
}

private val entryPriceNewestFirst = Comparator<HistoricalEntryFact> { a, b ->
    epochMillis(b.effectiveAt).compareTo(epochMillis(a.effectiveAt)).takeIf { it != 0 }
        ?: epochMillis(b.createdAt).compareTo(epochMillis(a.createdAt)).takeIf { it != 0 }
        ?: b.position.compareTo(a.position).takeIf { it != 0 }
        ?: b.id.compareTo(a.id)
}

fun calculateProductStockSnapshot(
    referenceDate: String,
    product: HistoricalReportProduct,
    conferences: List<HistoricalConferenceFact>,
    entries: List<HistoricalEntryFact>,
): ProductStockSnapshot {
    assertReferenceDate(referenceDate)

    val eligibleEntries = entries.filter { isVisibleAtReference(it.effectiveAt, referenceDate) }
    val lastConference = conferences
        .filter { isVisibleAtReference(it.effectiveAt, referenceDate) }
        .sortedWith(conferenceNewestFirst)
        .firstOrNull()

    val initialStockIsAvailable = product.initialStockQuantity != null &&
        product.initialStockAt != null &&
        isVisibleAtReference(product.initialStockAt, referenceDate)

    val checkpointAt = lastConference?.effectiveAt ?: if (initialStockIsAvailable) product.initialStockAt else null

    val entriesAfterCheckpoint = eligibleEntries.filter { entry ->
        checkpointAt == null || epochMillis(entry.effectiveAt) > epochMillis(checkpointAt)
    }
    val entriesAfterQuantity = entriesAfterCheckpoint.sumOf { it.quantity }

    val quantity: Double?
    val quantitySource: HistoricalQuantitySource
    when {
        lastConference != null -> {
            quantity = lastConference.quantity + entriesAfterQuantity
            quantitySource = if (entriesAfterCheckpoint.isNotEmpty()) {
                HistoricalQuantitySource.ConferencePlusEntries
            } else {
                HistoricalQuantitySource.Conference
            }
        }
        initialStockIsAvailable && product.initialStockQuantity != null -> {
            quantity = product.initialStockQuantity + entriesAfterQuantity
            quantitySource = if (entriesAfterCheckpoint.isNotEmpty()) {
                HistoricalQuantitySource.InitialStockPlusEntries
            } else {
                HistoricalQuantitySource.InitialStock
            }
        }
        entriesAfterCheckpoint.isNotEmpty() -> {
            quantity = entriesAfterQuantity
            quantitySource = HistoricalQuantitySource.EntriesOnly
        }
        else -> {
            quantity = null
            quantitySource = HistoricalQuantitySource.None
        }
    }

    val lastEntryPrice = entries
        .filter { it.unitPrice != null && it.unitPrice > 0.0 && isVisibleAtReference(it.effectiveAt, referenceDate) }
        .sortedWith(entryPriceNewestFirst)
        .firstOrNull()
    val initialPriceIsAvailable = product.initialPrice != null &&
        product.initialPriceAt != null &&
        isVisibleAtReference(product.initialPriceAt, referenceDate)

    val price = lastEntryPrice?.unitPrice ?: if (initialPriceIsAvailable) product.initialPrice else null
    val priceSource = when {
        lastEntryPrice != null -> HistoricalPriceSource.Entry
        initialPriceIsAvailable -> HistoricalPriceSource.Initial
        else -> HistoricalPriceSource.None
    }

    val value = when {
        quantity == null -> null
        quantity == 0.0 -> 0.0
        price == null -> null
        else -> roundMoney(quantity * price)
    }

    return ProductStockSnapshot(
        referenceDate = referenceDate,
        quantity = quantity,
        quantitySource = quantitySource,
        checkpointConferenceId = lastConference?.id,
        price = price,
        priceSource = priceSource,
        value = value,
        hasStockData = quantity != null,
        hasMissingPrice = quantity != null && quantity > 0.0 && price == null,
        hasExactPhysicalClose = lastConference != null && historicalDateKey(lastConference.effectiveAt) == referenceDate,
    )
}

// ---------- Relatório de um fechamento ----------

private fun productHasHistoricalPresence(
    product: HistoricalReportProduct,
    entries: List<HistoricalEntryFact>,
    conferences: List<HistoricalConferenceFact>,
    referenceDate: String,
): Boolean {
    val candidates = listOfNotNull(product.createdAt, product.initialStockAt, product.initialPriceAt) +
        entries.map { it.effectiveAt } +
        conferences.map { it.effectiveAt }
    return candidates.any { historicalDateKey(it) <= referenceDate }
}

fun isProductIncludedAtReference(
    product: HistoricalReportProduct,
    entries: List<HistoricalEntryFact>,
    conferences: List<HistoricalConferenceFact>,
    referenceDate: String,
    lifecycle: List<HistoricalProductLifecycleFact> = emptyList(),
): Boolean {
    if (!productHasHistoricalPresence(product, entries, conferences, referenceDate)) return false

    val latestLifecycle = lifecycle
        .filter { it.productId == product.id && historicalDateKey(it.createdAt) <= referenceDate }
        .sortedWith(
            compareByDescending<HistoricalProductLifecycleFact> { epochMillis(it.createdAt) }
                .thenByDescending { it.id }
        )
        .firstOrNull()

    if (latestLifecycle != null) return latestLifecycle.action == LifecycleAction.Restore
    if (product.deletedAt != null && historicalDateKey(product.deletedAt) <= referenceDate) return false
    return true
}

fun requiresMergeReconfirmationAtReference(
    productId: String,
    referenceDate: String,
    merges: List<HistoricalProductMergeFact>,
    conferences: List<HistoricalConferenceFact>,
): Boolean {
    val latestMerge = merges
        .filter { it.productId == productId && historicalDateKey(it.createdAt) <= referenceDate }
        .sortedWith(
            compareByDescending<HistoricalProductMergeFact> { epochMillis(it.createdAt) }
                .thenByDescending { it.id }
        )
        .firstOrNull()
        ?: return false

    val mergeAt = epochMillis(latestMerge.createdAt)
    return conferences.none {
        it.productId == productId &&
            epochMillis(it.createdAt) >= mergeAt &&
            historicalDateKey(it.effectiveAt) <= referenceDate
    }
}

fun calculateMonthlyStockValueReport(
    referenceDate: String,
    facts: MonthlyStockReportFacts,
): MonthlyStockValueReport {
    val entriesByProduct = facts.entries.groupBy { it.productId }
    val conferencesByProduct = facts.conferences.groupBy { it.productId }

    val products = facts.products.mapNotNull { product ->
        val productEntries = entriesByProduct[product.id].orEmpty()
        val productConferences = conferencesByProduct[product.id].orEmpty()
        if (!isProductIncludedAtReference(product, productEntries, productConferences, referenceDate, facts.lifecycle)) {
            return@mapNotNull null
        }
        MonthlyStockProductItem(
            productId = product.id,
            productName = product.name,
            unit = product.unit,
            stockRequiresConference = requiresMergeReconfirmationAtReference(
                product.id, referenceDate, facts.merges, productConferences,
            ),
            snapshot = calculateProductStockSnapshot(referenceDate, product, productConferences, productEntries),
        )
    }

    var totalKnown = 0.0
    var valued = 0
    var missingPrice = 0
    var unknownStock = 0
    var noStockData = 0
    for (item in products) {
        val snapshotValue = item.snapshot.value
        when {
            item.stockRequiresConference -> unknownStock += 1
            !item.snapshot.hasStockData -> noStockData += 1
            item.snapshot.hasMissingPrice -> missingPrice += 1
            snapshotValue != null -> {
                totalKnown += snapshotValue
                valued += 1
            }
        }
    }

    val hasExactPhysicalClose = products.isNotEmpty() && products.all {
        !it.stockRequiresConference && it.snapshot.hasStockData && it.snapshot.hasExactPhysicalClose
    }

    return MonthlyStockValueReport(
        referenceDate = referenceDate,
        totalKnown = roundMoney(totalKnown),
        productCount = products.size,
        valuedProductCount = valued,
        missingPriceProductCount = missingPrice,
        unknownStockProductCount = unknownStock,
        noStockDataProductCount = noStockData,
        hasMissingPrice = missingPrice > 0,
        hasUnknownStock = unknownStock > 0,
        hasMissingStockData = noStockData > 0,
        hasExactPhysicalClose = hasExactPhysicalClose,
        isEstimatedFromAvailableRecords = !hasExactPhysicalClose,
        products = products,
    )
}

// ---------- Série mensal ----------

fun firstHistoricalMonth(facts: MonthlyStockReportFacts): String? {
    val dates = facts.products.flatMap { listOfNotNull(it.createdAt, it.initialStockAt, it.initialPriceAt) } +
        facts.entries.map { it.effectiveAt } +
        facts.conferences.map { it.effectiveAt }
    return dates.minOfOrNull { historicalDateKey(it) }?.substring(0, 7)
}

/** Remove fatos ainda não ocorridos no instante informado (ex.: data efetiva futura). */
fun filterFactsAvailableAt(facts: MonthlyStockReportFacts, asOf: Instant): MonthlyStockReportFacts {
    val limit = asOf.toEpochMilli()
    fun reached(value: String?) = value != null && epochMillis(value) <= limit
    return facts.copy(
        products = facts.products.map { product ->
            val stockOk = reached(product.initialStockAt)
            val priceOk = reached(product.initialPriceAt)
            product.copy(
                initialStockAt = if (stockOk) product.initialStockAt else null,
                initialStockQuantity = if (stockOk) product.initialStockQuantity else null,
                initialPriceAt = if (priceOk) product.initialPriceAt else null,
                initialPrice = if (priceOk) product.initialPrice else null,
            )
        },
        entries = facts.entries.filter { reached(it.effectiveAt) },
        conferences = facts.conferences.filter { reached(it.effectiveAt) },
        merges = facts.merges.filter { reached(it.createdAt) },
        lifecycle = facts.lifecycle.filter { reached(it.createdAt) },
    )
}

fun calculateMonthlyStockValueSeries(currentDate: String, facts: MonthlyStockReportFacts): MonthlyStockValueSeries {
    assertReferenceDate(currentDate)
    val startMonth = firstHistoricalMonth(facts)
    val endMonth = currentDate.substring(0, 7)
    if (startMonth == null || startMonth > endMonth) return MonthlyStockValueSeries(null, null, emptyList())

    val points = mutableListOf<MonthlyStockSeriesPoint>()
    var month = parseMonth(startMonth)
    val end = parseMonth(endMonth)
    while (month <= end) {
        val key = month.toString()
        val provisional = key == endMonth
        val referenceDate = if (provisional) currentDate else lastDayOfMonth(key)
        points += MonthlyStockSeriesPoint(
            month = key,
            referenceDate = referenceDate,
            isProvisional = provisional,
            report = calculateMonthlyStockValueReport(referenceDate, facts),
        )
        month = month.plusMonths(1)
    }
    return MonthlyStockValueSeries(startMonth, endMonth, points)
}

fun filterSeriesPoints(
    points: List<MonthlyStockSeriesPoint>,
    startMonth: String,
    endMonth: String,
): List<MonthlyStockSeriesPoint> {
    parseMonth(startMonth)
    parseMonth(endMonth)
    require(startMonth <= endMonth) { "Período mensal inválido." }
    return points.filter { it.month in startMonth..endMonth }
}

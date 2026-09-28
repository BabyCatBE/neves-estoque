package com.babycatbe.nevesestoque.feature.products

import java.time.OffsetDateTime

private const val DAY_MS = 24.0 * 60 * 60 * 1000
private const val HISTORY_WINDOW_DAYS = 84
private const val RECENT_WINDOW_DAYS = 28
private const val MIN_HISTORY_DAYS = 28
private const val MIN_VALID_INTERVALS = 3

data class UsageConferencePoint(
    val effectiveAt: String,
    val createdAt: String,
    val quantity: Double,
)

data class UsageEntryPoint(
    val effectiveAt: String,
    val quantity: Double,
)

private data class UsageInterval(
    val start: UsageConferencePoint,
    val end: UsageConferencePoint,
    val days: Double,
    val entries: Double,
    val consumption: Double,
    val weight: Double,
)

private fun parseTime(value: String): Long? =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

private fun insufficient(
    reason: UsageReason,
    conferencesUsed: Int,
    ignoredNegativeIntervals: Int = 0,
): ProductUsageInsights = ProductUsageInsights(
    status = UsageStatus.Insufficient,
    reason = reason,
    conferencesUsed = conferencesUsed,
    validIntervals = 0,
    ignoredNegativeIntervals = ignoredNegativeIntervals,
    historyDays = null,
    intervalStart = null,
    intervalEnd = null,
    entriesDuringInterval = null,
    estimatedConsumption = null,
    dailyAverage = null,
    weeklyAverage = null,
    coverageDays = null,
)

fun calculateProductUsageInsights(
    conferences: List<UsageConferencePoint>,
    entries: List<UsageEntryPoint>,
    currentQuantity: Double?,
    referenceTimeMillis: Long = System.currentTimeMillis(),
): ProductUsageInsights {
    val cutoff = referenceTimeMillis - (HISTORY_WINDOW_DAYS * DAY_MS).toLong()
    val recentCutoff = referenceTimeMillis - (RECENT_WINDOW_DAYS * DAY_MS).toLong()

    val validConferences = conferences
        .filter { point ->
            val effective = parseTime(point.effectiveAt)
            val created = parseTime(point.createdAt)
            point.quantity.isFinite() && effective != null && created != null && effective <= referenceTimeMillis
        }
        .sortedWith { a, b ->
            val effectiveDiff = (parseTime(a.effectiveAt) ?: 0L).compareTo(parseTime(b.effectiveAt) ?: 0L)
            if (effectiveDiff != 0) effectiveDiff
            else (parseTime(a.createdAt) ?: 0L).compareTo(parseTime(b.createdAt) ?: 0L)
        }

    if (validConferences.size < 2) {
        return insufficient(UsageReason.NeedsTwoConferences, validConferences.size)
    }

    val validEntries = entries.mapNotNull { entry ->
        val time = parseTime(entry.effectiveAt)
        if (!entry.quantity.isFinite() || time == null || time > referenceTimeMillis) null
        else entry to time
    }

    val intervals = mutableListOf<UsageInterval>()
    var ignoredNegative = 0

    for (index in 0 until validConferences.lastIndex) {
        val start = validConferences[index]
        val end = validConferences[index + 1]
        val startTime = parseTime(start.effectiveAt) ?: continue
        val endTime = parseTime(end.effectiveAt) ?: continue

        if (startTime < cutoff || endTime > referenceTimeMillis || endTime <= startTime) continue

        val days = (endTime - startTime) / DAY_MS
        val intervalEntries = validEntries
            .filter { (_, time) -> time > startTime && time <= endTime }
            .sumOf { (entry, _) -> entry.quantity }
        val consumption = start.quantity + intervalEntries - end.quantity

        if (consumption < 0) {
            ignoredNegative += 1
            continue
        }

        intervals += UsageInterval(
            start = start,
            end = end,
            days = days,
            entries = intervalEntries,
            consumption = consumption,
            weight = if (endTime > recentCutoff) 2.0 else 1.0,
        )
    }

    if (intervals.isEmpty()) {
        return insufficient(
            reason = if (ignoredNegative > 0) UsageReason.NegativeConsumption else UsageReason.InvalidInterval,
            conferencesUsed = validConferences.size,
            ignoredNegativeIntervals = ignoredNegative,
        )
    }

    val historyDays = intervals.sumOf { it.days }
    val weightedConsumption = intervals.sumOf { it.consumption * it.weight }
    val weightedDays = intervals.sumOf { it.days * it.weight }
    val dailyAverage = if (weightedDays > 0) weightedConsumption / weightedDays else 0.0
    val weeklyAverage = dailyAverage * 7
    val coverageDays = if (dailyAverage > 0 && currentQuantity != null && currentQuantity >= 0) {
        currentQuantity / dailyAverage
    } else null
    val ready = intervals.size >= MIN_VALID_INTERVALS && historyDays >= MIN_HISTORY_DAYS

    return ProductUsageInsights(
        status = if (ready) UsageStatus.Ready else UsageStatus.Insufficient,
        reason = if (ready) null else UsageReason.NeedsMoreHistory,
        conferencesUsed = intervals.size + 1,
        validIntervals = intervals.size,
        ignoredNegativeIntervals = ignoredNegative,
        historyDays = historyDays,
        intervalStart = intervals.first().start.effectiveAt,
        intervalEnd = intervals.last().end.effectiveAt,
        entriesDuringInterval = intervals.sumOf { it.entries },
        estimatedConsumption = intervals.sumOf { it.consumption },
        dailyAverage = dailyAverage,
        weeklyAverage = weeklyAverage,
        coverageDays = coverageDays,
    )
}

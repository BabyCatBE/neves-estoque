package com.babycatbe.nevesestoque.feature.entries

import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val brLocale = Locale.forLanguageTag("pt-BR")

fun formatEntryMoney(value: Double): String =
    NumberFormat.getCurrencyInstance(brLocale).format(value)

fun formatEntryDateShort(value: String): String =
    runCatching {
        OffsetDateTime.parse(value).toInstant().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(brLocale))
    }.getOrDefault(value)

fun formatEntryDateLong(value: String): String =
    runCatching {
        OffsetDateTime.parse(value).toInstant().atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(brLocale))
    }.getOrDefault(value)

fun formatEntryNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else value.toString().replace('.', ',')

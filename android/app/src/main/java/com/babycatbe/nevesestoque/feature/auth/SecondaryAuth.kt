package com.babycatbe.nevesestoque.feature.auth

import java.text.Normalizer

private const val SECONDARY_EMAIL_DOMAIN = "usuarios.neves.invalid"
private val usernamePattern = Regex("^[a-z0-9][a-z0-9._-]{2,31}$")
private val combiningMarks = Regex("\\p{Mn}+")

fun normalizeSecondaryUsername(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .trim()
        .lowercase()

fun isValidSecondaryUsername(value: String): Boolean =
    usernamePattern.matches(normalizeSecondaryUsername(value))

fun secondaryAuthEmail(value: String): String =
    "${normalizeSecondaryUsername(value)}@$SECONDARY_EMAIL_DOMAIN"

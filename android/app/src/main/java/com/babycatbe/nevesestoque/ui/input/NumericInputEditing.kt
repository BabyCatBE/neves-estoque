package com.babycatbe.nevesestoque.ui.input

import java.math.BigDecimal

/**
 * Regras de digitação do teclado numérico próprio do app (Conferência e Entrada).
 *
 * Só monta o texto do campo; a validação oficial continua nas funções de cada módulo
 * (ex.: Conferência aceita zero, Entrada exige quantidade maior que zero).
 * A vírgula é o separador decimal exibido; as funções de validação já aceitam vírgula ou ponto.
 */
object NumericInputEditing {
    const val MAX_LENGTH = 12
    const val DECIMAL_SEPARATOR = ','

    fun appendDigit(value: String, digit: Char): String {
        require(digit in '0'..'9') { "Tecla numérica inválida." }
        if (value.length >= MAX_LENGTH) return value
        // Evita zeros à esquerda ("0" + "5" vira "5"; "0,5" continua permitido).
        if (value == "0") return digit.toString()
        return value + digit
    }

    fun appendDecimalSeparator(value: String): String {
        if (value.any { it == ',' || it == '.' }) return value
        if (value.length >= MAX_LENGTH - 1) return value
        return if (value.isEmpty()) "0$DECIMAL_SEPARATOR" else value + DECIMAL_SEPARATOR
    }

    fun backspace(value: String): String = value.dropLast(1)

    /**
     * Soma [delta] ao valor atual (botões − e + da Conferência). Campo vazio conta como zero;
     * o resultado nunca fica negativo. Devolve null quando o texto atual não é um número válido,
     * para não sobrescrever o que a pessoa digitou.
     */
    fun step(value: String, delta: Int): String? {
        val current = parse(value) ?: return null
        val next = current.add(BigDecimal(delta)).max(BigDecimal.ZERO)
        return format(next)
    }

    fun parse(value: String): BigDecimal? {
        val normalized = value.trim().replace(',', '.')
        if (normalized.isEmpty()) return BigDecimal.ZERO
        if (normalized.endsWith('.')) return normalized.dropLast(1).toBigDecimalOrNull()
        if (!Regex("^\\d+(?:\\.\\d+)?$").matches(normalized)) return null
        return normalized.toBigDecimalOrNull()
    }

    /** Número sem separador de milhar e com vírgula decimal (ex.: 12, 2,5). */
    fun format(value: BigDecimal): String {
        val plain = value.stripTrailingZeros().toPlainString()
        return if (plain.contains('.')) plain.replace('.', DECIMAL_SEPARATOR) else plain
    }
}

package com.babycatbe.nevesestoque.feature.conferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.babycatbe.nevesestoque.ui.input.requestFocusSafely

/**
 * Ordem de foco da Conferência por Categoria (nova e correção):
 * Data → Responsável → quantidade de cada Produto, na ordem da tela → Observação.
 *
 * Quantidades usam o teclado do app; Data, Responsável e Observação usam o teclado do celular.
 * Quem chama valida o campo atual antes de avançar: com erro, o foco não sai do campo.
 */
@Stable
class ConferenceFocusFlow internal constructor(
    private val productIds: List<String>,
    private val keyboard: SoftwareKeyboardController?,
) {
    val date = FocusRequester()
    val responsible = FocusRequester()
    val observation = FocusRequester()
    private val quantities = LinkedHashMap<String, FocusRequester>().apply {
        productIds.forEach { put(it, FocusRequester()) }
    }

    fun quantity(productId: String): FocusRequester = quantities.getOrPut(productId) { FocusRequester() }

    fun afterDate() {
        focusTextField(responsible)
    }

    fun afterResponsible() {
        val first = productIds.firstOrNull()
        if (first != null) quantity(first).requestFocusSafely() else focusObservation()
    }

    fun afterQuantity(productId: String) {
        val next = nextConferenceProductId(productIds, productId)
        if (next != null) quantity(next).requestFocusSafely() else focusObservation()
    }

    fun focusObservation() {
        focusTextField(observation)
    }

    /** Leva o foco ao primeiro campo com erro, na mesma ordem do preenchimento. */
    fun focusFirstError(errors: CategoryConferenceFormErrors) {
        when {
            errors.date != null -> focusTextField(date)
            errors.responsible != null -> focusTextField(responsible)
            errors.quantities.isNotEmpty() -> {
                val first = productIds.firstOrNull { it in errors.quantities } ?: errors.quantities.keys.first()
                quantity(first).requestFocusSafely()
            }
            errors.observation != null -> focusObservation()
        }
    }

    private fun focusTextField(requester: FocusRequester) {
        if (requester.requestFocusSafely()) {
            keyboard?.show()
        }
    }
}

@Composable
fun rememberConferenceFocusFlow(productIds: List<String>): ConferenceFocusFlow {
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(productIds, keyboard) { ConferenceFocusFlow(productIds, keyboard) }
}

/** Próximo Produto na ordem da tela, ou null quando [current] é o último (ou não existe). */
fun nextConferenceProductId(productIds: List<String>, current: String): String? {
    val index = productIds.indexOf(current)
    if (index < 0) return null
    return productIds.getOrNull(index + 1)
}

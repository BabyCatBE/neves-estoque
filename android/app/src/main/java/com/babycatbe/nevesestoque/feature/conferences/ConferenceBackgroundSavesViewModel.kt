package com.babycatbe.nevesestoque.feature.conferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Envio iniciado explicitamente pelo usuário, mas independente da tela de formulário.
 * O ViewModel fica no escopo do aplicativo autenticado (fora das rotas da navegação).
 * NÃO é uma fila durável nem faz envio automático de pendências ao reconectar.
 */
enum class ConferenceSavePhase { Saving, Saved, Failed }

data class ConferenceSaveStatus(
    val phase: ConferenceSavePhase,
    val message: String? = null,
)

class ConferenceBackgroundSavesViewModel : ViewModel() {
    private val repository = ConferenceModuleRepository()
    private val _statuses = MutableStateFlow<Map<String, ConferenceSaveStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ConferenceSaveStatus>> = _statuses.asStateFlow()

    private val retryInputs = mutableMapOf<String, CategoryConferenceWriteInput>()
    private val jobs = mutableMapOf<String, Job>()
    private var currentUserId: String? = null

    /** Evita transportar dados da contagem entre contas no mesmo aparelho. */
    fun bindUser(userId: String) {
        if (currentUserId == userId) return
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        retryInputs.clear()
        _statuses.value = emptyMap()
        currentUserId = userId
    }

    /** Aceita uma nova contagem somente quando não há envio incerto para esta categoria. */
    fun submit(input: CategoryConferenceWriteInput): Boolean {
        if (currentUserId == null) return false
        val current = _statuses.value[input.categoryId]?.phase
        if (current == ConferenceSavePhase.Saving || current == ConferenceSavePhase.Failed) return false
        retryInputs[input.categoryId] = input
        start(input)
        return true
    }

    /** Não cria nova chave: repete exatamente o envio que o usuário já confirmou. */
    fun retry(categoryId: String) {
        if (_statuses.value[categoryId]?.phase != ConferenceSavePhase.Failed) return
        val input = retryInputs[categoryId] ?: return
        start(input)
    }

    /** Deve ser chamada somente após confirmação de descarte na interface. */
    fun discardFailed(categoryId: String) {
        if (_statuses.value[categoryId]?.phase != ConferenceSavePhase.Failed) return
        retryInputs.remove(categoryId)
        _statuses.value = _statuses.value - categoryId
    }

    private fun start(input: CategoryConferenceWriteInput) {
        val categoryId = input.categoryId
        if (jobs[categoryId]?.isActive == true) return
        _statuses.value = _statuses.value + (
            categoryId to ConferenceSaveStatus(ConferenceSavePhase.Saving)
        )
        jobs[categoryId] = viewModelScope.launch {
            try {
                repository.createCategoryConference(input)
                retryInputs.remove(categoryId)
                _statuses.value = _statuses.value + (
                    categoryId to ConferenceSaveStatus(ConferenceSavePhase.Saved)
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // Falha ambígua: checar a chave original antes de oferecer repetição manual.
                val reconciled = if (shouldReconcileConferenceFailure(error)) {
                    try {
                        repository.findConferenceIdByIdempotencyKey(input.idempotencyKey) != null
                    } catch (lookupError: Throwable) {
                        if (lookupError is CancellationException) throw lookupError
                        false
                    }
                } else false
                if (reconciled) {
                    retryInputs.remove(categoryId)
                    _statuses.value = _statuses.value + (
                        categoryId to ConferenceSaveStatus(ConferenceSavePhase.Saved)
                    )
                } else {
                    _statuses.value = _statuses.value + (
                        categoryId to ConferenceSaveStatus(
                            ConferenceSavePhase.Failed,
                            conferenceModuleErrorMessage(error),
                        )
                    )
                }
            } finally {
                jobs.remove(categoryId)
            }
        }
    }
}

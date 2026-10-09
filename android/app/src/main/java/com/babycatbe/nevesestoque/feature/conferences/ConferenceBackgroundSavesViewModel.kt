package com.babycatbe.nevesestoque.feature.conferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babycatbe.nevesestoque.feature.offline.PendingMutationResult
import com.babycatbe.nevesestoque.feature.offline.PendingStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Um clique consciente em Salvar inicia uma gravação protegida localmente e acompanhada
 * fora da tela do formulário. Nenhuma pendência é enviada automaticamente ao reconectar:
 * caso esta tentativa falhe, o usuário deve fazer nova ação manual.
 */
enum class ConferenceSavePhase { Saving, Saved, Failed }

data class ConferenceSaveStatus(
    val phase: ConferenceSavePhase,
    val message: String? = null,
    val effectiveDate: String? = null,
)

private data class StagedConference(
    val input: CategoryConferenceWriteInput,
    val localId: String,
)

class ConferenceBackgroundSavesViewModel : ViewModel() {
    private val repository = ConferenceModuleRepository()
    private val _statuses = MutableStateFlow<Map<String, ConferenceSaveStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ConferenceSaveStatus>> = _statuses.asStateFlow()

    private val retryInputs = mutableMapOf<String, StagedConference>()
    private val jobs = mutableMapOf<String, Job>()
    private var currentUserId: String? = null

    /** Não transportar dados de uma conta para outra na mesma sessão do aparelho. */
    fun bindUser(userId: String) {
        if (currentUserId == userId) return
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        retryInputs.clear()
        _statuses.value = emptyMap()
        currentUserId = userId
    }

    /**
     * O formulário já validou as quantidades, revisão de consumo, mesma data e
     * guardou o payload no PendingStore em armazenamento privado/atômico.
     */
    fun submit(input: CategoryConferenceWriteInput, localId: String): Boolean {
        if (currentUserId.isNullOrBlank()) return false
        val current = _statuses.value[input.categoryId]?.phase
        if (current == ConferenceSavePhase.Saving || current == ConferenceSavePhase.Failed) return false
        val staged = StagedConference(input, localId)
        retryInputs[input.categoryId] = staged
        start(staged)
        return true
    }

    /** Reenvio somente por toque; preserva a MESMA idempotencyKey da primeira tentativa. */
    fun retry(categoryId: String) {
        if (_statuses.value[categoryId]?.phase != ConferenceSavePhase.Failed) return
        val staged = retryInputs[categoryId] ?: return
        start(staged)
    }

    /** Depois do alerta explícito, descarta o arquivo local, sem excluir nada do servidor. */
    fun discardFailed(categoryId: String) {
        if (_statuses.value[categoryId]?.phase != ConferenceSavePhase.Failed) return
        val staged = retryInputs[categoryId] ?: return
        _statuses.value = _statuses.value + (
            categoryId to ConferenceSaveStatus(ConferenceSavePhase.Saving, "Descartando cópia local…")
        )
        viewModelScope.launch {
            when (val result = PendingStore.delete(staged.localId)) {
                PendingMutationResult.Success -> {
                    retryInputs.remove(categoryId)
                    _statuses.value = _statuses.value - categoryId
                }
                is PendingMutationResult.Failure -> {
                    _statuses.value = _statuses.value + (
                        categoryId to ConferenceSaveStatus(ConferenceSavePhase.Failed, result.userMessage)
                    )
                }
            }
        }
    }

    private fun start(staged: StagedConference) {
        val categoryId = staged.input.categoryId
        if (jobs[categoryId]?.isActive == true) return
        _statuses.value = _statuses.value + (
            categoryId to ConferenceSaveStatus(
                ConferenceSavePhase.Saving,
                effectiveDate = conferenceLocalDate(staged.input.effectiveAt),
            )
        )
        jobs[categoryId] = viewModelScope.launch {
            try {
                repository.createCategoryConference(staged.input)
                finishSuccessful(staged)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val reconciled = if (shouldReconcileConferenceFailure(error)) {
                    try {
                        repository.findConferenceIdByIdempotencyKey(staged.input.idempotencyKey) != null
                    } catch (lookupError: Throwable) {
                        if (lookupError is CancellationException) throw lookupError
                        false
                    }
                } else false
                if (reconciled) {
                    finishSuccessful(staged)
                } else {
                    // O arquivo local permanece até retry, descarte explícito ou recuperação em Alertas.
                    _statuses.value = _statuses.value + (
                        categoryId to ConferenceSaveStatus(
                            ConferenceSavePhase.Failed,
                            conferenceModuleErrorMessage(error),
                            effectiveDate = conferenceLocalDate(staged.input.effectiveAt),
                        )
                    )
                }
            } finally {
                jobs.remove(categoryId)
            }
        }
    }

    private suspend fun finishSuccessful(staged: StagedConference) {
        val result = PendingStore.delete(staged.localId)
        retryInputs.remove(staged.input.categoryId)
        _statuses.value = _statuses.value + (
            staged.input.categoryId to ConferenceSaveStatus(
                ConferenceSavePhase.Saved,
                if (result is PendingMutationResult.Failure) {
                    "Salva no servidor. Não foi possível limpar a cópia local; revise Alertas → Pendências locais."
                } else null,
                effectiveDate = conferenceLocalDate(staged.input.effectiveAt),
            )
        )
    }
}

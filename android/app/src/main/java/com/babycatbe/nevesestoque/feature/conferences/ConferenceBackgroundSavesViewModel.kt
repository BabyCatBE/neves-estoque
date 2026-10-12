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
enum class ConferenceSavePhase { Checking, ReviewConsumption, ReviewSameDay, Saving, Saved, Failed }

data class ConferenceSaveStatus(
    val phase: ConferenceSavePhase,
    val message: String? = null,
    val effectiveDate: String? = null,
    val warnings: List<ConferenceConsumptionWarning> = emptyList(),
    val sameDay: List<ConferenceHistoryItem> = emptyList(),
    val products: List<ConferenceProduct> = emptyList(),
)

private data class StagedConference(
    val input: CategoryConferenceWriteInput,
    val localId: String,
    val products: List<ConferenceProduct>,
    val consumptionApproved: Boolean = false,
    val sameDayApproved: Boolean = false,
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

    /** O formulário validou os campos e criou cópia local. Checagens remotas ocorrem aqui. */
    fun submit(input: CategoryConferenceWriteInput, localId: String, products: List<ConferenceProduct>): Boolean {
        if (currentUserId.isNullOrBlank()) return false
        val current = _statuses.value[input.categoryId]?.phase
        if (current == ConferenceSavePhase.Checking || current == ConferenceSavePhase.Saving ||
            current == ConferenceSavePhase.ReviewConsumption || current == ConferenceSavePhase.ReviewSameDay ||
            current == ConferenceSavePhase.Failed) return false
        val staged = StagedConference(input, localId, products)
        retryInputs[input.categoryId] = staged
        start(staged)
        return true
    }

    /** Reenvio somente por toque; preserva a MESMA idempotencyKey da primeira tentativa. */
    fun retry(categoryId: String) {
        if (_statuses.value[categoryId]?.phase != ConferenceSavePhase.Failed) return
        val staged = retryInputs[categoryId] ?: return
        start(staged.copy(consumptionApproved = false, sameDayApproved = false))
    }

    /** Uma verificação posterior exige decisão humana; nunca salvar silenciosamente. */
    fun confirmReview(categoryId: String) {
        val phase = _statuses.value[categoryId]?.phase
        val staged = retryInputs[categoryId] ?: return
        when (phase) {
            ConferenceSavePhase.ReviewConsumption -> start(staged.copy(consumptionApproved = true))
            ConferenceSavePhase.ReviewSameDay -> start(staged.copy(sameDayApproved = true))
            else -> Unit
        }
    }

    /** Depois do alerta explícito, descarta o arquivo local, sem excluir nada do servidor. */
    fun discardFailed(categoryId: String) {
        if (_statuses.value[categoryId]?.phase !in setOf(
                ConferenceSavePhase.Failed, ConferenceSavePhase.ReviewConsumption,
                ConferenceSavePhase.ReviewSameDay,
            )) return
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
        retryInputs[categoryId] = staged
        val effectiveDate = conferenceLocalDate(staged.input.effectiveAt)
        _statuses.value = _statuses.value + (
            categoryId to ConferenceSaveStatus(ConferenceSavePhase.Checking, effectiveDate = effectiveDate)
        )
        jobs[categoryId] = viewModelScope.launch {
            try {
                if (!staged.consumptionApproved) {
                    val warnings = repository.reviewConsumption(
                        effectiveAt = staged.input.effectiveAt,
                        items = staged.input.items,
                    )
                    if (warnings.isNotEmpty()) {
                        _statuses.value = _statuses.value + (
                            categoryId to ConferenceSaveStatus(
                                ConferenceSavePhase.ReviewConsumption,
                                "Consumo fora do padrão. Confira antes de confirmar.",
                                effectiveDate, warnings = warnings, products = staged.products,
                            )
                        )
                        return@launch
                    }
                }
                if (!staged.sameDayApproved) {
                    val sameDay = repository.listSameDayCategoryConferences(
                        categoryId = categoryId, dateValue = effectiveDate,
                    )
                    if (sameDay.isNotEmpty()) {
                        _statuses.value = _statuses.value + (
                            categoryId to ConferenceSaveStatus(
                                ConferenceSavePhase.ReviewSameDay,
                                "Já existe conferência nesta data. Confirme se é outra contagem.",
                                effectiveDate, sameDay = sameDay,
                            )
                        )
                        return@launch
                    }
                }
                _statuses.value = _statuses.value + (
                    categoryId to ConferenceSaveStatus(ConferenceSavePhase.Saving, effectiveDate = effectiveDate)
                )
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
                    // A cópia permanece. Nenhuma nova tentativa ocorre por simples reconexão.
                    _statuses.value = _statuses.value + (
                        categoryId to ConferenceSaveStatus(
                            ConferenceSavePhase.Failed, conferenceModuleErrorMessage(error),
                            effectiveDate = effectiveDate,
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

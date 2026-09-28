package com.babycatbe.nevesestoque.feature.conferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ConferenceCategoriesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val categories: List<ConferenceCategorySummary> = emptyList(),
    val errorMessage: String? = null,
)

class ConferenceCategoriesViewModel : ViewModel() {
    private val repository = ConferenceModuleRepository()
    private val _uiState = MutableStateFlow(ConferenceCategoriesUiState())
    val uiState: StateFlow<ConferenceCategoriesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            val hasData = _uiState.value.categories.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            runCatching { repository.loadCategorySummaries() }
                .onSuccess {
                    _uiState.value = ConferenceCategoriesUiState(
                        loading = false,
                        categories = it,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar as Categorias de Conferência.",
                    )
                }
        }
    }
}

data class CategoryConferenceFormUiState(
    val loading: Boolean = true,
    val checking: Boolean = false,
    val saving: Boolean = false,
    val setup: CategoryConferenceSetup? = null,
    val fieldErrors: CategoryConferenceFormErrors = CategoryConferenceFormErrors(),
    val consumptionWarnings: List<ConferenceConsumptionWarning> = emptyList(),
    val sameDayConferences: List<ConferenceHistoryItem> = emptyList(),
    val errorMessage: String? = null,
    val savedConferenceId: String? = null,
    val savedPendingMessage: String? = null,
)

class CategoryConferenceFormViewModel(private val categoryId: String) : ViewModel() {
    private val repository = ConferenceModuleRepository()
    private val idempotencyKey = UUID.randomUUID().toString()
    private var pendingInput: CategoryConferenceWriteInput? = null
    private val _uiState = MutableStateFlow(CategoryConferenceFormUiState())
    val uiState: StateFlow<CategoryConferenceFormUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching { repository.loadCategorySetup(categoryId) }
                .onSuccess {
                    _uiState.value = CategoryConferenceFormUiState(
                        loading = false,
                        setup = it,
                    )
                }
                .onFailure {
                    _uiState.value = CategoryConferenceFormUiState(
                        loading = false,
                        errorMessage = "Não foi possível carregar esta Categoria.",
                    )
                }
        }
    }

    fun requestSave(
        date: String,
        responsible: String,
        observation: String,
        quantities: Map<String, String>,
    ) {
        if (_uiState.value.checking || _uiState.value.saving) return
        val setup = _uiState.value.setup ?: return
        val validation = validateCategoryConferenceDraft(
            products = setup.products,
            date = date,
            responsible = responsible,
            observation = observation,
            quantities = quantities,
        )
        if (
            validation.errors.hasErrors ||
            validation.effectiveAt == null ||
            validation.responsible == null
        ) {
            _uiState.value = _uiState.value.copy(
                fieldErrors = validation.errors,
                errorMessage = null,
            )
            return
        }

        val deviceId = DeviceIdentityStore.registeredDeviceId()
        if (deviceId == null) {
            _uiState.value = _uiState.value.copy(
                fieldErrors = CategoryConferenceFormErrors(),
                errorMessage = "Este dispositivo ainda não está pronto para registrar Conferências.",
            )
            return
        }

        val input = CategoryConferenceWriteInput(
            categoryId = setup.categoryId,
            effectiveAt = validation.effectiveAt,
            physicalResponsible = validation.responsible,
            deviceId = deviceId,
            idempotencyKey = idempotencyKey,
            observation = validation.observation,
            items = validation.items,
        )

        if (!com.babycatbe.nevesestoque.data.offline.ConnectivityMonitor.isOnline) {
            // Sem internet: guarda como pendência. A revisão de consumo e a checagem de mesma data
            // não são fingidas offline; acontecem na confirmação online (mesma regra da Web).
            _uiState.value = _uiState.value.copy(saving = true, errorMessage = null)
            viewModelScope.launch {
                val metadata = com.babycatbe.nevesestoque.feature.offline.currentPendingMetadata()
                val operation = metadata?.let {
                    com.babycatbe.nevesestoque.feature.offline.buildPendingCategoryConference(
                        input = input,
                        categoryLabel = setup.categoryName,
                        productLabel = { productId ->
                            val product = setup.products.firstOrNull { p -> p.id == productId }
                            (product?.name ?: "Produto") to (product?.unit ?: "")
                        },
                        metadata = it,
                    )
                }
                val result = operation?.let { com.babycatbe.nevesestoque.feature.offline.PendingStore.save(it) }
                _uiState.value = when (result) {
                    com.babycatbe.nevesestoque.feature.offline.PendingMutationResult.Success ->
                        _uiState.value.copy(
                            saving = false,
                            fieldErrors = CategoryConferenceFormErrors(),
                            savedPendingMessage = com.babycatbe.nevesestoque.feature.offline.PENDING_SAVED_MESSAGE,
                        )
                    is com.babycatbe.nevesestoque.feature.offline.PendingMutationResult.Failure ->
                        _uiState.value.copy(saving = false, errorMessage = result.userMessage)
                    null -> _uiState.value.copy(
                        saving = false,
                        errorMessage = "Este dispositivo ainda não está pronto para registrar Conferências.",
                    )
                }
            }
            return
        }

        _uiState.value = _uiState.value.copy(
            checking = true,
            fieldErrors = CategoryConferenceFormErrors(),
            consumptionWarnings = emptyList(),
            sameDayConferences = emptyList(),
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                val warnings = repository.reviewConsumption(
                    effectiveAt = input.effectiveAt,
                    items = input.items,
                )
                if (warnings.isNotEmpty()) {
                    pendingInput = input
                    _uiState.value = _uiState.value.copy(
                        checking = false,
                        consumptionWarnings = warnings,
                    )
                } else {
                    continueAfterConsumptionReview(input)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    checking = false,
                    errorMessage = conferenceModuleErrorMessage(error),
                )
            }
        }
    }

    fun confirmConsumptionWarnings() {
        if (_uiState.value.saving || _uiState.value.checking) return
        val input = pendingInput ?: return
        _uiState.value = _uiState.value.copy(
            checking = true,
            consumptionWarnings = emptyList(),
            errorMessage = null,
        )
        viewModelScope.launch {
            try {
                continueAfterConsumptionReview(input)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    checking = false,
                    errorMessage = conferenceModuleErrorMessage(error),
                )
            }
        }
    }

    fun dismissConsumptionWarnings() {
        if (_uiState.value.saving) return
        pendingInput = null
        _uiState.value = _uiState.value.copy(consumptionWarnings = emptyList())
    }

    fun confirmSameDay() {
        if (_uiState.value.saving || _uiState.value.checking) return
        val input = pendingInput ?: return
        _uiState.value = _uiState.value.copy(
            sameDayConferences = emptyList(),
            errorMessage = null,
        )
        viewModelScope.launch { persist(input) }
    }

    fun dismissSameDay() {
        if (_uiState.value.saving) return
        pendingInput = null
        _uiState.value = _uiState.value.copy(sameDayConferences = emptyList())
    }

    private suspend fun continueAfterConsumptionReview(input: CategoryConferenceWriteInput) {
        val sameDay = repository.listSameDayCategoryConferences(
            categoryId = input.categoryId,
            dateValue = conferenceLocalDate(input.effectiveAt),
        )
        if (sameDay.isNotEmpty()) {
            pendingInput = input
            _uiState.value = _uiState.value.copy(
                checking = false,
                sameDayConferences = sameDay,
            )
            return
        }
        persist(input)
    }

    private suspend fun persist(input: CategoryConferenceWriteInput) {
        _uiState.value = _uiState.value.copy(
            checking = false,
            saving = true,
            consumptionWarnings = emptyList(),
            sameDayConferences = emptyList(),
            errorMessage = null,
        )
        try {
            val conferenceId = repository.createCategoryConference(input)
            pendingInput = null
            _uiState.value = _uiState.value.copy(
                saving = false,
                savedConferenceId = conferenceId,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val reconciledId = if (shouldReconcileConferenceFailure(error)) {
                try {
                    repository.findConferenceIdByIdempotencyKey(idempotencyKey)
                } catch (reload: Throwable) {
                    if (reload is CancellationException) throw reload
                    null
                }
            } else {
                null
            }
            if (reconciledId != null) {
                pendingInput = null
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    savedConferenceId = reconciledId,
                    errorMessage = null,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    errorMessage = conferenceModuleErrorMessage(error),
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(private val categoryId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CategoryConferenceFormViewModel(categoryId) as T
    }
}

data class CategoryConferenceHistoryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val setup: CategoryConferenceSetup? = null,
    val conferences: List<ConferenceHistoryItem> = emptyList(),
    val errorMessage: String? = null,
)

class CategoryConferenceHistoryViewModel(private val categoryId: String) : ViewModel() {
    private val repository = ConferenceModuleRepository()
    private val _uiState = MutableStateFlow(CategoryConferenceHistoryUiState())
    val uiState: StateFlow<CategoryConferenceHistoryUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            val hasData = _uiState.value.setup != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            try {
                val setup = repository.loadCategorySetup(categoryId)
                val history = repository.loadCategoryHistory(categoryId)
                _uiState.value = CategoryConferenceHistoryUiState(
                    loading = false,
                    setup = setup,
                    conferences = history,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    refreshing = false,
                    errorMessage = "Não foi possível carregar o Histórico desta Categoria.",
                )
            }
        }
    }

    class Factory(private val categoryId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CategoryConferenceHistoryViewModel(categoryId) as T
    }
}

data class ConferenceDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val details: ConferenceDetails? = null,
    val errorMessage: String? = null,
    val actionError: String? = null,
)

class ConferenceDetailViewModel(private val conferenceId: String) : ViewModel() {
    private val repository = ConferenceModuleRepository()
    private val _uiState = MutableStateFlow(ConferenceDetailUiState())
    val uiState: StateFlow<ConferenceDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.refreshing || _uiState.value.deleting) return
        viewModelScope.launch {
            val hasData = _uiState.value.details != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
                actionError = null,
            )
            runCatching { repository.loadConferenceDetails(conferenceId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        details = it,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar esta Conferência.",
                    )
                }
        }
    }

    fun deleteConference() {
        if (_uiState.value.deleting || _uiState.value.deleted) return
        val deviceId = DeviceIdentityStore.registeredDeviceId()
        if (deviceId == null) {
            _uiState.value = _uiState.value.copy(
                actionError = "Este dispositivo ainda não está pronto para excluir Conferências.",
            )
            return
        }
        _uiState.value = _uiState.value.copy(deleting = true, actionError = null)
        viewModelScope.launch {
            try {
                repository.softDeleteConference(conferenceId, deviceId)
                _uiState.value = _uiState.value.copy(deleting = false, deleted = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val active = if (shouldReconcileConferenceFailure(error)) {
                    try {
                        repository.isConferenceActive(conferenceId)
                    } catch (reload: Throwable) {
                        if (reload is CancellationException) throw reload
                        null
                    }
                } else {
                    null
                }
                _uiState.value = if (active == false) {
                    _uiState.value.copy(deleting = false, deleted = true)
                } else {
                    _uiState.value.copy(
                        deleting = false,
                        actionError = conferenceModuleErrorMessage(error),
                    )
                }
            }
        }
    }

    class Factory(private val conferenceId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConferenceDetailViewModel(conferenceId) as T
    }
}

data class EditConferenceUiState(
    val loading: Boolean = true,
    val checking: Boolean = false,
    val saving: Boolean = false,
    val details: ConferenceDetails? = null,
    val fieldErrors: CategoryConferenceFormErrors = CategoryConferenceFormErrors(),
    val reviewOpen: Boolean = false,
    val consumptionWarnings: List<ConferenceConsumptionWarning> = emptyList(),
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

class EditConferenceViewModel(private val conferenceId: String) : ViewModel() {
    private val repository = ConferenceModuleRepository()
    private var pendingInput: CategoryConferenceUpdateInput? = null
    private val _uiState = MutableStateFlow(EditConferenceUiState())
    val uiState: StateFlow<EditConferenceUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching { repository.loadConferenceDetails(conferenceId) }
                .onSuccess {
                    _uiState.value = EditConferenceUiState(
                        loading = false,
                        details = it,
                    )
                }
                .onFailure {
                    _uiState.value = EditConferenceUiState(
                        loading = false,
                        errorMessage = "Não foi possível carregar esta Conferência.",
                    )
                }
        }
    }

    fun requestReview(
        date: String,
        responsible: String,
        observation: String,
        quantities: Map<String, String>,
    ) {
        if (_uiState.value.checking || _uiState.value.saving) return
        val details = _uiState.value.details ?: return
        val validation = validateCategoryConferenceEdit(
            details = details,
            date = date,
            responsible = responsible,
            observation = observation,
            quantities = quantities,
        )
        if (
            validation.errors.hasErrors ||
            validation.effectiveAt == null ||
            validation.responsible == null
        ) {
            _uiState.value = _uiState.value.copy(
                fieldErrors = validation.errors,
                errorMessage = null,
            )
            return
        }

        val deviceId = DeviceIdentityStore.registeredDeviceId()
        if (deviceId == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Este dispositivo ainda não está pronto para corrigir Conferências.",
            )
            return
        }

        val input = CategoryConferenceUpdateInput(
            conferenceId = details.id,
            effectiveAt = validation.effectiveAt,
            physicalResponsible = validation.responsible,
            deviceId = deviceId,
            observation = validation.observation,
            items = validation.items,
        )
        _uiState.value = _uiState.value.copy(
            checking = true,
            fieldErrors = CategoryConferenceFormErrors(),
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                val warnings = repository.reviewConsumption(
                    effectiveAt = input.effectiveAt,
                    items = input.items,
                    excludeConferenceId = details.id,
                )
                pendingInput = input
                _uiState.value = _uiState.value.copy(
                    checking = false,
                    reviewOpen = true,
                    consumptionWarnings = warnings,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    checking = false,
                    errorMessage = conferenceModuleErrorMessage(error),
                )
            }
        }
    }

    fun dismissReview() {
        if (_uiState.value.saving) return
        pendingInput = null
        _uiState.value = _uiState.value.copy(
            reviewOpen = false,
            consumptionWarnings = emptyList(),
        )
    }

    fun confirmSave() {
        if (_uiState.value.saving || _uiState.value.checking) return
        val input = pendingInput ?: return
        _uiState.value = _uiState.value.copy(
            reviewOpen = false,
            saving = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            try {
                repository.updateCategoryConference(input)
                pendingInput = null
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    consumptionWarnings = emptyList(),
                    savedMessage = "Correção da Conferência salva com sucesso.",
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val reconciled = if (shouldReconcileConferenceFailure(error)) {
                    try {
                        repository.isUpdateApplied(input)
                    } catch (reload: Throwable) {
                        if (reload is CancellationException) throw reload
                        false
                    }
                } else {
                    false
                }
                if (reconciled) {
                    pendingInput = null
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        consumptionWarnings = emptyList(),
                        savedMessage = "Correção da Conferência salva com sucesso.",
                        errorMessage = null,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        errorMessage = conferenceModuleErrorMessage(error),
                    )
                }
            }
        }
    }

    fun consumeSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }

    class Factory(private val conferenceId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditConferenceViewModel(conferenceId) as T
    }
}

data class ConferencePrintUiState(
    val loading: Boolean = true,
    val data: ConferencePrintData? = null,
    val errorMessage: String? = null,
)

class ConferencePrintViewModel : ViewModel() {
    private val repository = ConferenceModuleRepository()
    private val _uiState = MutableStateFlow(ConferencePrintUiState())
    val uiState: StateFlow<ConferencePrintUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.loadPrintData() }
                .onSuccess {
                    _uiState.value = ConferencePrintUiState(loading = false, data = it)
                }
                .onFailure {
                    _uiState.value = ConferencePrintUiState(
                        loading = false,
                        errorMessage = "Não foi possível gerar os Papéis de Conferência.",
                    )
                }
        }
    }
}

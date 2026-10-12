package com.babycatbe.nevesestoque.feature.conferences

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import com.babycatbe.nevesestoque.ui.load.LatestLoad
import com.babycatbe.nevesestoque.ui.load.loadCatching
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
    private val latestLoad = LatestLoad()

    init { refresh() }

    fun refresh() {
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.categories.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            loadCatching { repository.loadCategorySummaries() }
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
        onReadyToSave: (CategoryConferenceWriteInput, String, List<ConferenceProduct>) -> Boolean,
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

        // Campos já foram validados. Verificações de consumo/mesma data ocorrem
        // depois da cópia local e da navegação, no escopo da sessão autenticada.
        viewModelScope.launch { queueSave(input, onReadyToSave) }
    }
    private suspend fun queueSave(
        input: CategoryConferenceWriteInput,
        onReadyToSave: (CategoryConferenceWriteInput, String, List<ConferenceProduct>) -> Boolean,
    ) {
        val setup = _uiState.value.setup ?: return
        _uiState.value = _uiState.value.copy(
            checking = false,
            saving = true,
            consumptionWarnings = emptyList(),
            sameDayConferences = emptyList(),
            errorMessage = null,
        )
        try {
            // O arquivo local é gravado ANTES de navegar. Interrupções do Android não
            // perdem esta contagem: a cópia continua em Alertas → Pendências locais.
            // Pendências jamais são enviadas automaticamente ao reconectar.
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
            if (operation == null) {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    errorMessage = "Não foi possível proteger esta contagem no aparelho. Tente novamente.",
                )
                return
            }
            val stored = com.babycatbe.nevesestoque.feature.offline.PendingStore.save(operation)
            if (stored is com.babycatbe.nevesestoque.feature.offline.PendingMutationResult.Failure) {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    errorMessage = stored.userMessage,
                )
                return
            }
            if (!onReadyToSave(input, operation.localId, setup.products)) {
                com.babycatbe.nevesestoque.feature.offline.PendingStore.delete(operation.localId)
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    errorMessage = "Esta Categoria já possui um envio pendente. Verifique o status na lista.",
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _uiState.value = _uiState.value.copy(
                saving = false,
                errorMessage = "Não foi possível preparar o envio. Sua contagem não foi descartada.",
            )
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
    private val latestLoad = LatestLoad()

    init { refresh() }

    fun refresh() {
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.setup != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            try {
                val (setup, history) = coroutineScope {
                    val setupAsync = async { repository.loadCategorySetup(categoryId) }
                    val historyAsync = async { repository.loadCategoryHistory(categoryId) }
                    setupAsync.await() to historyAsync.await()
                }
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
    private val latestLoad = LatestLoad()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.deleting) return
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.details != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
                actionError = null,
            )
            loadCatching { repository.loadConferenceDetails(conferenceId) }
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

    /** Validação local apenas; nenhuma consulta de rede retém a tela do formulário. */
    fun requestReview(
        date: String, responsible: String, observation: String, quantities: Map<String, String>,
        onReadyToCorrect: (CategoryConferenceUpdateInput, String, String, List<ConferenceProduct>) -> Boolean,
    ) {
        if (_uiState.value.saving) return
        val details = _uiState.value.details ?: return
        val validation = validateCategoryConferenceEdit(details, date, responsible, observation, quantities)
        if (validation.errors.hasErrors || validation.effectiveAt == null || validation.responsible == null) {
            _uiState.value = _uiState.value.copy(fieldErrors = validation.errors, errorMessage = null)
            return
        }
        val deviceId = DeviceIdentityStore.registeredDeviceId()
        if (deviceId == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Dispositivo não autorizado para corrigir.")
            return
        }
        // Não sobrescrever uma correção que já está aguardando revisão ou envio.
        if (com.babycatbe.nevesestoque.feature.offline.PendingStore.pending.value.any { pending ->
                pending.kind == com.babycatbe.nevesestoque.feature.offline.PendingKind.CategoryConferenceCorrection &&
                    pending.categoryConference?.conferenceId == details.id
            }) {
            _uiState.value = _uiState.value.copy(errorMessage = "Esta conferência já tem correção pendente em Alertas → Pendências locais.")
            return
        }
        val input = CategoryConferenceUpdateInput(
            conferenceId = details.id, effectiveAt = validation.effectiveAt,
            physicalResponsible = validation.responsible, deviceId = deviceId,
            observation = validation.observation, items = validation.items,
        )
        _uiState.value = _uiState.value.copy(saving = true, fieldErrors = CategoryConferenceFormErrors(), errorMessage = null)
        viewModelScope.launch {
            try {
                val metadata = com.babycatbe.nevesestoque.feature.offline.currentPendingMetadata()
                val operation = metadata?.let {
                    com.babycatbe.nevesestoque.feature.offline.buildPendingCategoryConferenceCorrection(
                        input = input, categoryId = details.categoryId, categoryLabel = details.categoryName,
                        productLabel = { productId ->
                            val item = details.items.firstOrNull { it.productId == productId }
                            (item?.productName ?: "Produto") to (item?.unit ?: "")
                        }, metadata = it,
                    )
                }
                if (operation == null) {
                    _uiState.value = _uiState.value.copy(saving = false, errorMessage = "Não foi possível proteger a correção no aparelho.")
                    return@launch
                }
                when (val result = com.babycatbe.nevesestoque.feature.offline.PendingStore.save(operation)) {
                    com.babycatbe.nevesestoque.feature.offline.PendingMutationResult.Success -> {
                        val products = details.items.map { ConferenceProduct(it.productId, it.productName, it.unit, it.position) }
                        if (!onReadyToCorrect(input, operation.localId, details.categoryId, products)) {
                            _uiState.value = _uiState.value.copy(saving = false, errorMessage =
                                "Correção protegida em Alertas → Pendências locais. Envio não iniciado.")
                        }
                    }
                    is com.babycatbe.nevesestoque.feature.offline.PendingMutationResult.Failure ->
                        _uiState.value = _uiState.value.copy(saving = false, errorMessage = result.userMessage)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(saving = false, errorMessage =
                    "Não foi possível preparar a correção. Revise Alertas → Pendências locais.")
            }
        }
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

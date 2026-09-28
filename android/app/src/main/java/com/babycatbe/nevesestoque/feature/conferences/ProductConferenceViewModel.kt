package com.babycatbe.nevesestoque.feature.conferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.feature.products.ProductDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.UUID

data class ProductStockUpdateUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val product: ProductDetails? = null,
    val errorMessage: String? = null,
)

class ProductStockUpdateViewModel(
    private val productId: String,
) : ViewModel() {
    private val repository = ProductConferenceRepository()
    private val _uiState = MutableStateFlow(ProductStockUpdateUiState())
    val uiState: StateFlow<ProductStockUpdateUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            val hasData = _uiState.value.product != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            runCatching { repository.loadProduct(productId) }
                .onSuccess {
                    _uiState.value = ProductStockUpdateUiState(
                        loading = false,
                        product = it,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar este Produto.",
                    )
                }
        }
    }

    class Factory(private val productId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProductStockUpdateViewModel(productId) as T
    }
}

data class ProductConferenceUiState(
    val loading: Boolean = true,
    val checking: Boolean = false,
    val saving: Boolean = false,
    val product: ProductDetails? = null,
    val fieldErrors: ProductConferenceFormErrors = ProductConferenceFormErrors(),
    val errorMessage: String? = null,
    val warning: ConferenceConsumptionWarning? = null,
    val savedMessage: String? = null,
)

class ProductConferenceViewModel(
    private val productId: String,
) : ViewModel() {
    private val repository = ProductConferenceRepository()
    private val idempotencyKey = UUID.randomUUID().toString()
    private var pendingInput: ProductConferenceWriteInput? = null

    private val _uiState = MutableStateFlow(ProductConferenceUiState())
    val uiState: StateFlow<ProductConferenceUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching { repository.loadProduct(productId) }
                .onSuccess {
                    _uiState.value = ProductConferenceUiState(
                        loading = false,
                        product = it,
                    )
                }
                .onFailure {
                    _uiState.value = ProductConferenceUiState(
                        loading = false,
                        errorMessage = "Não foi possível carregar este Produto.",
                    )
                }
        }
    }

    fun requestSave(
        responsible: String,
        quantity: String,
        observation: String,
    ) {
        if (_uiState.value.checking || _uiState.value.saving) return

        val validation = validateProductConferenceDraft(
            responsible = responsible,
            quantity = quantity,
            observation = observation,
        )
        if (
            validation.errors.hasErrors ||
            validation.responsible == null ||
            validation.quantity == null
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
                fieldErrors = ProductConferenceFormErrors(),
                errorMessage = "Este dispositivo ainda não está pronto para registrar Conferências.",
            )
            return
        }

        val input = ProductConferenceWriteInput(
            productId = productId,
            effectiveAt = OffsetDateTime.now().toString(),
            physicalResponsible = validation.responsible,
            deviceId = deviceId,
            idempotencyKey = idempotencyKey,
            quantity = validation.quantity,
            observation = validation.observation,
        )

        _uiState.value = _uiState.value.copy(
            checking = true,
            fieldErrors = ProductConferenceFormErrors(),
            errorMessage = null,
            warning = null,
        )

        viewModelScope.launch {
            try {
                val warning = repository.reviewConsumption(
                    productId = productId,
                    candidateQuantity = input.quantity,
                    effectiveAt = input.effectiveAt,
                )
                if (warning != null) {
                    pendingInput = input
                    _uiState.value = _uiState.value.copy(
                        checking = false,
                        warning = warning,
                    )
                } else {
                    persist(input)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    checking = false,
                    errorMessage = conferenceErrorMessage(error),
                )
            }
        }
    }

    fun confirmWarning() {
        if (_uiState.value.saving || _uiState.value.checking) return
        val input = pendingInput ?: return
        pendingInput = null
        _uiState.value = _uiState.value.copy(
            warning = null,
            saving = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            persist(input, savingAlreadySet = true)
        }
    }

    fun dismissWarning() {
        if (_uiState.value.saving) return
        pendingInput = null
        _uiState.value = _uiState.value.copy(warning = null)
    }

    private suspend fun persist(
        input: ProductConferenceWriteInput,
        savingAlreadySet: Boolean = false,
    ) {
        if (!savingAlreadySet) {
            _uiState.value = _uiState.value.copy(
                checking = false,
                saving = true,
                warning = null,
                errorMessage = null,
            )
        }

        try {
            repository.createProductConference(input)
            _uiState.value = _uiState.value.copy(
                checking = false,
                saving = false,
                warning = null,
                savedMessage = "Conferência registrada com sucesso. O estoque atual foi recalculado.",
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _uiState.value = _uiState.value.copy(
                checking = false,
                saving = false,
                errorMessage = conferenceErrorMessage(error),
            )
        }
    }

    fun consumeSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }

    class Factory(private val productId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProductConferenceViewModel(productId) as T
    }
}

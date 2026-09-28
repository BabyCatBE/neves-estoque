package com.babycatbe.nevesestoque.feature.suppliers

import com.babycatbe.nevesestoque.ui.load.LatestLoad
import com.babycatbe.nevesestoque.ui.load.loadCatching
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SuppliersUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val suppliers: List<SupplierDetails> = emptyList(),
    val errorMessage: String? = null,
)

class SuppliersViewModel : ViewModel() {
    private val repository = SuppliersRepository()
    private val _uiState = MutableStateFlow(SuppliersUiState())
    val uiState: StateFlow<SuppliersUiState> = _uiState.asStateFlow()
    private val latestLoad = LatestLoad()

    init { refresh() }

    fun refresh() {
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.suppliers.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            loadCatching { repository.loadActiveSuppliers() }
                .onSuccess {
                    _uiState.value = SuppliersUiState(
                        loading = false,
                        suppliers = it,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar os Fornecedores.",
                    )
                }
        }
    }
}

data class SupplierDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val supplier: SupplierDetails? = null,
    val errorMessage: String? = null,
    val actionError: String? = null,
)

class SupplierDetailViewModel(
    private val supplierId: String,
) : ViewModel() {
    private val repository = SuppliersRepository()
    private val _uiState = MutableStateFlow(SupplierDetailUiState())
    val uiState: StateFlow<SupplierDetailUiState> = _uiState.asStateFlow()
    private val latestLoad = LatestLoad()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.deleting) return
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.supplier != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            loadCatching { repository.loadSupplier(supplierId) }
                .onSuccess {
                    _uiState.value = SupplierDetailUiState(
                        loading = false,
                        supplier = it,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar este Fornecedor.",
                    )
                }
        }
    }

    fun deleteSupplier() {
        val state = _uiState.value
        if (state.deleting || state.deleted) return
        _uiState.value = state.copy(deleting = true, actionError = null)

        viewModelScope.launch {
            try {
                repository.softDeleteSupplier(supplierId)
                _uiState.value = _uiState.value.copy(
                    deleting = false,
                    deleted = true,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val active = try {
                    repository.isSupplierActive(supplierId)
                } catch (reloadError: Throwable) {
                    if (reloadError is CancellationException) throw reloadError
                    null
                }

                if (active == false) {
                    _uiState.value = _uiState.value.copy(
                        deleting = false,
                        deleted = true,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        deleting = false,
                        actionError = supplierErrorMessage(error),
                    )
                }
            }
        }
    }

    class Factory(private val supplierId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SupplierDetailViewModel(supplierId) as T
    }
}

data class SupplierFormUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val supplier: SupplierDetails? = null,
    val fieldErrors: SupplierFormErrors = SupplierFormErrors(),
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

class SupplierFormViewModel(
    private val supplierId: String?,
) : ViewModel() {
    private val repository = SuppliersRepository()
    private val _uiState = MutableStateFlow(
        SupplierFormUiState(loading = supplierId != null)
    )
    val uiState: StateFlow<SupplierFormUiState> = _uiState.asStateFlow()

    init {
        if (supplierId != null) load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { repository.loadSupplier(supplierId!!) }
                .onSuccess {
                    _uiState.value = SupplierFormUiState(
                        loading = false,
                        supplier = it,
                    )
                }
                .onFailure {
                    _uiState.value = SupplierFormUiState(
                        loading = false,
                        errorMessage = "Não foi possível carregar este Fornecedor.",
                    )
                }
        }
    }

    fun save(
        name: String,
        company: String,
        phone: String,
        observation: String,
        purchaseFrequencyDays: String,
        preferredOrderWeekday: String,
        averageDeliveryDays: String,
        safetyMarginDays: String,
    ) {
        if (_uiState.value.saving) return

        val validated = validateSupplierDraft(
            name = name,
            company = company,
            phone = phone,
            observation = observation,
            purchaseFrequencyDays = purchaseFrequencyDays,
            preferredOrderWeekday = preferredOrderWeekday,
            averageDeliveryDays = averageDeliveryDays,
            safetyMarginDays = safetyMarginDays,
        )
        if (validated.input == null) {
            _uiState.value = _uiState.value.copy(
                fieldErrors = validated.errors,
                errorMessage = null,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            saving = true,
            fieldErrors = SupplierFormErrors(),
            errorMessage = null,
        )

        viewModelScope.launch {
            try {
                if (supplierId == null) {
                    repository.createSupplier(validated.input)
                } else {
                    repository.updateSupplier(supplierId, validated.input)
                }
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    savedMessage = if (supplierId == null) {
                        "Fornecedor criado com sucesso."
                    } else {
                        "Fornecedor atualizado com sucesso."
                    },
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error

                val updateReconciled = if (
                    supplierId != null &&
                    shouldReconcileSupplierUpdate(error)
                ) {
                    try {
                        repository.isSupplierUpdateApplied(supplierId, validated.input)
                    } catch (reloadError: Throwable) {
                        if (reloadError is CancellationException) throw reloadError
                        false
                    }
                } else {
                    false
                }

                _uiState.value = if (updateReconciled) {
                    _uiState.value.copy(
                        saving = false,
                        savedMessage = "Fornecedor atualizado com sucesso.",
                        errorMessage = null,
                    )
                } else {
                    _uiState.value.copy(
                        saving = false,
                        errorMessage = supplierErrorMessage(error),
                    )
                }
            }
        }
    }

    fun consumeSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }

    class Factory(private val supplierId: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SupplierFormViewModel(supplierId) as T
    }
}

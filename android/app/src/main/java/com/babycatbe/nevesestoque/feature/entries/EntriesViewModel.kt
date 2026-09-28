package com.babycatbe.nevesestoque.feature.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class EntriesHistoryUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val entries: List<EntryHistoryItem> = emptyList(),
    val errorMessage: String? = null,
)

class EntriesHistoryViewModel : ViewModel() {
    private val repository = EntriesRepository()
    private val _uiState = MutableStateFlow(EntriesHistoryUiState())
    val uiState: StateFlow<EntriesHistoryUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            val hasData = _uiState.value.entries.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            runCatching { repository.loadHistory() }
                .onSuccess { _uiState.value = EntriesHistoryUiState(loading = false, entries = it) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar o Histórico de Entradas.",
                    )
                }
        }
    }
}

data class EntryDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val entry: EntryDetails? = null,
    val errorMessage: String? = null,
    val actionError: String? = null,
)

class EntryDetailViewModel(private val entryId: String) : ViewModel() {
    private val repository = EntriesRepository()
    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState: StateFlow<EntryDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.refreshing || _uiState.value.deleting) return
        viewModelScope.launch {
            val hasData = _uiState.value.entry != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
                actionError = null,
            )
            runCatching { repository.loadEntryDetails(entryId) }
                .onSuccess {
                    _uiState.value = EntryDetailUiState(loading = false, entry = it)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar esta Entrada.",
                    )
                }
        }
    }

    fun deleteEntry() {
        if (_uiState.value.deleting || _uiState.value.deleted) return
        _uiState.value = _uiState.value.copy(deleting = true, actionError = null)
        viewModelScope.launch {
            try {
                repository.softDeleteEntry(entryId)
                _uiState.value = _uiState.value.copy(deleting = false, deleted = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val active = if (shouldReconcileEntryFailure(error)) {
                    try {
                        repository.isEntryActive(entryId)
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
                        actionError = entryErrorMessage(error),
                    )
                }
            }
        }
    }

    class Factory(private val entryId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EntryDetailViewModel(entryId) as T
    }
}

data class NewEntryUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val options: EntryFormOptions? = null,
    val errorMessage: String? = null,
    val savedEntryId: String? = null,
)

class NewEntryViewModel : ViewModel() {
    private val repository = EntriesRepository()
    private val idempotencyKey = UUID.randomUUID().toString()
    private val _uiState = MutableStateFlow(NewEntryUiState())
    val uiState: StateFlow<NewEntryUiState> = _uiState.asStateFlow()

    init { refreshOptions() }

    fun refreshOptions() {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            runCatching { repository.loadFormOptions() }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        options = it,
                        errorMessage = null,
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        errorMessage = "Não foi possível carregar os dados da Nova Entrada.",
                    )
                }
        }
    }

    fun save(input: EntryCreateInput) {
        if (_uiState.value.saving || _uiState.value.savedEntryId != null) return
        _uiState.value = _uiState.value.copy(saving = true, errorMessage = null)
        val finalInput = input.copy(idempotencyKey = idempotencyKey)
        viewModelScope.launch {
            try {
                val entryId = repository.createEntry(finalInput)
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    savedEntryId = entryId,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val reconciledId = if (shouldReconcileEntryFailure(error)) {
                    try {
                        repository.findEntryIdByIdempotencyKey(idempotencyKey)
                    } catch (reload: Throwable) {
                        if (reload is CancellationException) throw reload
                        null
                    }
                } else {
                    null
                }
                _uiState.value = if (reconciledId != null) {
                    _uiState.value.copy(
                        saving = false,
                        savedEntryId = reconciledId,
                        errorMessage = null,
                    )
                } else {
                    _uiState.value.copy(
                        saving = false,
                        errorMessage = entryErrorMessage(error),
                    )
                }
            }
        }
    }

    suspend fun completePendingProduct(
        productId: String,
        name: String,
        categoryId: String,
    ): EntryProductOption {
        val product = repository.completePendingProduct(productId, name, categoryId)
        val options = repository.loadFormOptions()
        _uiState.value = _uiState.value.copy(options = options, errorMessage = null)
        return options.products.firstOrNull { it.id == product.id } ?: product
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class EditEntryUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val data: EntryEditData? = null,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

class EditEntryViewModel(private val entryId: String) : ViewModel() {
    private val repository = EntriesRepository()
    private val _uiState = MutableStateFlow(EditEntryUiState())
    val uiState: StateFlow<EditEntryUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching { repository.loadEditData(entryId) }
                .onSuccess {
                    _uiState.value = EditEntryUiState(loading = false, data = it)
                }
                .onFailure {
                    _uiState.value = EditEntryUiState(
                        loading = false,
                        errorMessage = "Não foi possível carregar esta Entrada.",
                    )
                }
        }
    }

    fun save(input: EntryUpdateInput) {
        if (_uiState.value.saving) return
        _uiState.value = _uiState.value.copy(saving = true, errorMessage = null)
        viewModelScope.launch {
            try {
                repository.updateEntry(input)
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    savedMessage = "Alterações da Entrada salvas com sucesso.",
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val reconciled = if (shouldReconcileEntryFailure(error)) {
                    try {
                        repository.isUpdateApplied(input)
                    } catch (reload: Throwable) {
                        if (reload is CancellationException) throw reload
                        false
                    }
                } else {
                    false
                }
                _uiState.value = if (reconciled) {
                    _uiState.value.copy(
                        saving = false,
                        savedMessage = "Alterações da Entrada salvas com sucesso.",
                        errorMessage = null,
                    )
                } else {
                    _uiState.value.copy(
                        saving = false,
                        errorMessage = entryErrorMessage(error),
                    )
                }
            }
        }
    }

    fun consumeSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }

    class Factory(private val entryId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditEntryViewModel(entryId) as T
    }
}

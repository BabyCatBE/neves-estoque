package com.babycatbe.nevesestoque.feature.stock

import com.babycatbe.nevesestoque.ui.load.LatestLoad
import com.babycatbe.nevesestoque.ui.load.SharedSnapshotLoad
import com.babycatbe.nevesestoque.ui.load.loadCatching
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StockUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val data: CurrentStockData? = null,
    val errorMessage: String? = null,
)

/**
 * Estoque Atual compartilhado entre o pré-carregamento da abertura do app e a tela.
 * Mesma leitura do [StockRepository]; somente evita repetir a consulta e permite abrir na hora.
 */
object StockWarmLoad {
    private val repository = StockRepository()
    val shared = SharedSnapshotLoad(
        loadOfficial = { repository.loadCurrentStock() },
        loadSnapshot = { repository.loadCurrentStockSnapshot() },
    )
}

class StockViewModel : ViewModel() {
    private val shared = StockWarmLoad.shared
    private val _uiState = MutableStateFlow(
        shared.peek()?.let { StockUiState(loading = false, refreshing = true, data = it) } ?: StockUiState()
    )
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()
    private val latestLoad = LatestLoad()

    init {
        latestLoad.launch(viewModelScope) {
            if (_uiState.value.data == null) {
                shared.snapshotOrNull()?.let { snapshot ->
                    if (_uiState.value.data == null) {
                        _uiState.value = StockUiState(loading = false, refreshing = true, data = snapshot)
                    }
                }
            }
            load(reuseInFlight = true)
        }
    }

    fun refresh() {
        latestLoad.launch(viewModelScope) { load(reuseInFlight = false) }
    }

    private suspend fun load(reuseInFlight: Boolean) {
        val hasData = _uiState.value.data != null
        _uiState.value = _uiState.value.copy(
            loading = !hasData,
            refreshing = hasData,
            errorMessage = null,
        )

        loadCatching { shared.load(reuseInFlight) }
            .onSuccess { data ->
                _uiState.value = StockUiState(
                    loading = false,
                    refreshing = false,
                    data = data,
                )
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    refreshing = false,
                    errorMessage = "Não foi possível carregar o Estoque Atual.",
                )
            }
    }
}

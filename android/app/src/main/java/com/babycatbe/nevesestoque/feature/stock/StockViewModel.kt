package com.babycatbe.nevesestoque.feature.stock

import com.babycatbe.nevesestoque.ui.load.LatestLoad
import com.babycatbe.nevesestoque.ui.load.loadCatching
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StockUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val data: CurrentStockData? = null,
    val errorMessage: String? = null,
)

class StockViewModel : ViewModel() {
    private val repository = StockRepository()
    private val _uiState = MutableStateFlow(StockUiState())
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()
    private val latestLoad = LatestLoad()

    init {
        refresh()
    }

    fun refresh() {
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.data != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )

            loadCatching { repository.loadCurrentStock() }
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
}

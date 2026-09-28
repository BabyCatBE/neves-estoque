package com.babycatbe.nevesestoque.feature.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProductMergeUiState(
    val loading: Boolean = true,
    val selectingCandidate: Boolean = false,
    val merging: Boolean = false,
    val source: ProductDetails? = null,
    val candidate: ProductDetails? = null,
    val catalog: ProductCatalogData? = null,
    val errorMessage: String? = null,
    val actionError: String? = null,
    val mergedResult: ProductMergeResult? = null,
)

class ProductMergeViewModel(
    private val productId: String,
) : ViewModel() {
    private val repository = ProductsRepository()
    private val _uiState = MutableStateFlow(ProductMergeUiState())
    val uiState: StateFlow<ProductMergeUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            try {
                val source = repository.loadProductDetails(productId).first
                val catalog = repository.loadCatalog()
                _uiState.value = ProductMergeUiState(
                    loading = false,
                    source = source,
                    catalog = catalog,
                    errorMessage = if (source.categoryId == null) {
                        "Produtos com cadastro pendente não podem ser mesclados."
                    } else {
                        null
                    },
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = ProductMergeUiState(
                    loading = false,
                    errorMessage = "Não foi possível preparar a mescla.",
                )
            }
        }
    }

    fun selectCandidate(candidateId: String) {
        val state = _uiState.value
        if (state.selectingCandidate || state.merging || candidateId == productId) return

        _uiState.value = state.copy(
            selectingCandidate = true,
            actionError = null,
        )
        viewModelScope.launch {
            try {
                val candidate = repository.loadProductDetails(candidateId).first
                if (candidate.categoryId == null) {
                    _uiState.value = _uiState.value.copy(
                        selectingCandidate = false,
                        actionError = "Produtos com cadastro pendente não podem ser mesclados.",
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    selectingCandidate = false,
                    candidate = candidate,
                    actionError = null,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    selectingCandidate = false,
                    actionError = "Não foi possível carregar o Produto duplicado.",
                )
            }
        }
    }

    fun clearCandidate() {
        if (_uiState.value.merging) return
        _uiState.value = _uiState.value.copy(
            candidate = null,
            actionError = null,
        )
    }

    fun merge(input: ProductMergeDraft) {
        val state = _uiState.value
        val source = state.source ?: return
        val candidate = state.candidate ?: return
        if (state.merging) return

        val pair = determineMergePair(source, candidate)
        _uiState.value = state.copy(
            merging = true,
            actionError = null,
        )

        viewModelScope.launch {
            try {
                val result = repository.mergeProducts(input)
                _uiState.value = _uiState.value.copy(
                    merging = false,
                    mergedResult = result,
                    actionError = null,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error

                val reconciled = try {
                    repository.reconcileProductMerge(input, pair)
                } catch (reloadError: Throwable) {
                    if (reloadError is CancellationException) throw reloadError
                    null
                }

                if (reconciled != null) {
                    _uiState.value = _uiState.value.copy(
                        merging = false,
                        mergedResult = reconciled,
                        actionError = null,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        merging = false,
                        actionError = productMergeErrorMessage(error),
                    )
                }
            }
        }
    }

    class Factory(private val productId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProductMergeViewModel(productId) as T
    }
}

package com.babycatbe.nevesestoque.feature.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProductsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val data: ProductCatalogData? = null,
    val errorMessage: String? = null,
)

class ProductsViewModel : ViewModel() {
    private val repository = ProductsRepository()
    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            val hasData = _uiState.value.data != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            runCatching { repository.loadCatalog() }
                .onSuccess { _uiState.value = ProductsUiState(loading = false, data = it) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar os Produtos.",
                    )
                }
        }
    }
}

data class ProductDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val product: ProductDetails? = null,
    val categories: List<ProductCategoryRow> = emptyList(),
    val errorMessage: String? = null,
)

class ProductDetailViewModel(
    private val productId: String,
) : ViewModel() {
    private val repository = ProductsRepository()
    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

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
            runCatching { repository.loadProductDetails(productId) }
                .onSuccess { (product, categories) ->
                    _uiState.value = ProductDetailUiState(
                        loading = false,
                        product = product,
                        categories = categories,
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
            ProductDetailViewModel(productId) as T
    }
}

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

data class ProductFormUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val product: ProductDetails? = null,
    val categories: List<ProductCategoryRow> = emptyList(),
    val fieldErrors: ProductFormErrors = ProductFormErrors(),
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

class ProductFormViewModel(
    private val productId: String?,
) : ViewModel() {
    private val repository = ProductsRepository()
    private val _uiState = MutableStateFlow(ProductFormUiState())
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching {
                if (productId == null) {
                    null to repository.loadCatalog().categories
                } else {
                    repository.loadProductDetails(productId)
                }
            }.onSuccess { (product, categories) ->
                _uiState.value = ProductFormUiState(
                    loading = false,
                    product = product,
                    categories = categories.sortedWith(
                        compareBy<ProductCategoryRow> { it.sortOrder ?: Int.MAX_VALUE }
                            .thenBy { it.name.lowercase() }
                    ),
                )
            }.onFailure {
                _uiState.value = ProductFormUiState(
                    loading = false,
                    errorMessage = if (productId == null) {
                        "Não foi possível carregar as Categorias."
                    } else {
                        "Não foi possível carregar este Produto."
                    },
                )
            }
        }
    }

    fun save(
        name: String,
        categoryId: String,
        unit: String,
        initialStock: String,
        initialPrice: String,
    ) {
        if (_uiState.value.saving) return

        val currentProduct = _uiState.value.product
        val validation = validateProductDraft(
            name = name,
            categoryId = categoryId,
            unit = if (productId == null) unit else currentProduct?.unit.orEmpty(),
            initialStock = if (productId == null) initialStock else "",
            initialPrice = if (productId == null) initialPrice else "",
        )
        if (validation.errors.hasErrors || validation.input == null) {
            _uiState.value = _uiState.value.copy(
                fieldErrors = validation.errors,
                errorMessage = null,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            saving = true,
            fieldErrors = ProductFormErrors(),
            errorMessage = null,
        )
        viewModelScope.launch {
            runCatching {
                if (productId == null) {
                    repository.createProduct(validation.input)
                } else {
                    repository.updateProduct(productId, validation.input.name, validation.input.categoryId)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    savedMessage = if (productId == null) {
                        "Produto criado com sucesso."
                    } else {
                        "Produto atualizado com sucesso."
                    },
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    errorMessage = productErrorMessage(error),
                )
            }
        }
    }

    fun consumeSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }

    class Factory(private val productId: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ProductFormViewModel(productId) as T
    }
}

data class CategoryFormUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val category: CategoryListItem? = null,
    val nextSortOrder: Int = 1,
    val nameError: String? = null,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
)

class CategoryFormViewModel(
    private val categoryId: String?,
) : ViewModel() {
    private val repository = ProductsRepository()
    private val _uiState = MutableStateFlow(CategoryFormUiState())
    val uiState: StateFlow<CategoryFormUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching { repository.loadCategories() }
                .onSuccess { categories ->
                    val category = categoryId?.let { id -> categories.firstOrNull { it.id == id } }
                    if (categoryId != null && category == null) {
                        _uiState.value = CategoryFormUiState(
                            loading = false,
                            errorMessage = "Categoria não encontrada.",
                        )
                    } else {
                        _uiState.value = CategoryFormUiState(
                            loading = false,
                            category = category,
                            nextSortOrder = (categories.maxOfOrNull { it.sortOrder ?: 0 } ?: 0) + 1,
                        )
                    }
                }
                .onFailure {
                    _uiState.value = CategoryFormUiState(
                        loading = false,
                        errorMessage = "Não foi possível carregar as Categorias.",
                    )
                }
        }
    }

    fun save(name: String) {
        if (_uiState.value.saving) return
        val nameError = validateCategoryName(name)
        if (nameError != null) {
            _uiState.value = _uiState.value.copy(nameError = nameError, errorMessage = null)
            return
        }

        val normalizedName = normalizeCategoryName(name)
        _uiState.value = _uiState.value.copy(saving = true, nameError = null, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                if (categoryId == null) {
                    repository.createCategory(normalizedName, _uiState.value.nextSortOrder)
                } else {
                    repository.updateCategory(categoryId, normalizedName)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    savedMessage = if (categoryId == null) {
                        "Categoria criada com sucesso."
                    } else {
                        "Categoria atualizada com sucesso."
                    },
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    errorMessage = categoryErrorMessage(error),
                )
            }
        }
    }

    fun consumeSavedMessage() {
        _uiState.value = _uiState.value.copy(savedMessage = null)
    }

    class Factory(private val categoryId: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CategoryFormViewModel(categoryId) as T
    }
}

package com.babycatbe.nevesestoque.feature.products

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
import java.util.UUID

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
    private val latestLoad = LatestLoad()

    init { refresh() }

    fun refresh() {
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.data != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            loadCatching { repository.loadCatalog() }
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

    suspend fun saveProductOrder(changes: List<ProductOrderChange>): Boolean {
        if (changes.isEmpty()) return true

        _uiState.value = _uiState.value.copy(
            loading = false,
            refreshing = true,
            errorMessage = null,
        )

        return try {
            repository.reorderProducts(changes)
            val official = repository.loadCatalog()
            _uiState.value = ProductsUiState(loading = false, data = official)
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error

            val official = try {
                repository.loadCatalog()
            } catch (reloadError: Throwable) {
                if (reloadError is CancellationException) throw reloadError
                null
            }

            _uiState.value = _uiState.value.copy(
                loading = false,
                refreshing = false,
                data = official ?: _uiState.value.data,
                errorMessage = if (official == null) {
                    "Não foi possível recarregar os Produtos após a falha ao salvar a ordem."
                } else {
                    null
                },
            )
            false
        }
    }
}

data class ProductDetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val convertingUnit: Boolean = false,
    val product: ProductDetails? = null,
    val categories: List<ProductCategoryRow> = emptyList(),
    val errorMessage: String? = null,
    val actionError: String? = null,
    val conversionError: String? = null,
    val conversionNotice: String? = null,
)

class ProductDetailViewModel(
    private val productId: String,
) : ViewModel() {
    private val repository = ProductsRepository()
    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()
    private val latestLoad = LatestLoad()

    init { refresh() }

    fun refresh() {
        if (_uiState.value.convertingUnit) return
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.product != null
            _uiState.value = _uiState.value.copy(
                loading = !hasData,
                refreshing = hasData,
                errorMessage = null,
            )
            loadCatching { repository.loadProductDetails(productId) }
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

    fun deleteProduct() {
        if (_uiState.value.deleting || _uiState.value.deleted || _uiState.value.convertingUnit) return
        _uiState.value = _uiState.value.copy(deleting = true, actionError = null)

        viewModelScope.launch {
            runCatching { repository.softDeleteProduct(productId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        deleting = false,
                        deleted = true,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        deleting = false,
                        actionError = catalogTrashErrorMessage(error),
                    )
                }
        }
    }

    fun convertUnit(input: ProductUnitConversionDraft) {
        val state = _uiState.value
        if (state.convertingUnit || state.deleting || state.deleted) return

        _uiState.value = state.copy(
            convertingUnit = true,
            conversionError = null,
            conversionNotice = null,
        )

        viewModelScope.launch {
            try {
                repository.convertProductUnit(input)
                val refreshed = try {
                    repository.loadProductDetails(productId)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    null
                }

                _uiState.value = if (refreshed != null) {
                    _uiState.value.copy(
                        convertingUnit = false,
                        product = refreshed.first,
                        categories = refreshed.second,
                        conversionError = null,
                        conversionNotice = "Unidade alterada com sucesso. Histórico e valores foram convertidos.",
                    )
                } else {
                    _uiState.value.copy(
                        convertingUnit = false,
                        conversionError = null,
                        conversionNotice = "Unidade alterada com sucesso. Use Atualizar para recarregar os valores convertidos.",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error

                val refreshed = try {
                    repository.loadProductDetails(productId)
                } catch (reloadError: Throwable) {
                    if (reloadError is CancellationException) throw reloadError
                    null
                }

                if (refreshed?.first?.unit == input.newUnit) {
                    _uiState.value = _uiState.value.copy(
                        convertingUnit = false,
                        product = refreshed.first,
                        categories = refreshed.second,
                        conversionError = null,
                        conversionNotice = "Unidade alterada com sucesso. Histórico e valores foram convertidos.",
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        convertingUnit = false,
                        product = refreshed?.first ?: _uiState.value.product,
                        categories = refreshed?.second ?: _uiState.value.categories,
                        conversionError = productUnitConversionErrorMessage(error),
                    )
                }
            }
        }
    }

    fun clearConversionError() {
        if (!_uiState.value.convertingUnit) {
            _uiState.value = _uiState.value.copy(conversionError = null)
        }
    }

    fun consumeConversionNotice() {
        _uiState.value = _uiState.value.copy(conversionNotice = null)
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

    fun retryLoad() {
        if (_uiState.value.saving || _uiState.value.loading) return
        _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
        load()
    }

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
    val illustrationError: String? = null,
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

    fun save(name: String, illustration: CategoryIllustrationDraft) {
        if (_uiState.value.saving) return

        val nameError = validateCategoryName(name)
        val illustrationError = validateCategoryIllustrationDraft(illustration)
        if (nameError != null || illustrationError != null) {
            _uiState.value = _uiState.value.copy(
                nameError = nameError,
                illustrationError = illustrationError,
                errorMessage = null,
            )
            return
        }

        val normalizedName = normalizeCategoryName(name)
        val currentCategory = _uiState.value.category
        val targetCategoryId = categoryId ?: UUID.randomUUID().toString()
        val oldUploadPath = currentCategory
            ?.takeIf { it.illustrationSource == "upload" }
            ?.illustrationKey

        _uiState.value = _uiState.value.copy(
            saving = true,
            nameError = null,
            illustrationError = null,
            errorMessage = null,
        )

        viewModelScope.launch {
            var uploadedPath: String? = null
            try {
                if (
                    illustration.source == "upload" &&
                    illustration.key == null &&
                    illustration.imageBytes != null &&
                    illustration.mimeType != null
                ) {
                    uploadedPath = repository.uploadCategoryIllustration(
                        categoryId = targetCategoryId,
                        bytes = illustration.imageBytes,
                        mimeType = illustration.mimeType,
                    )
                }

                val metadata = resolveCategoryIllustrationMetadata(
                    draft = illustration,
                    uploadedKey = uploadedPath,
                )
                val input = CategoryMutationInput(
                    id = targetCategoryId,
                    name = normalizedName,
                    sortOrder = currentCategory?.sortOrder ?: _uiState.value.nextSortOrder,
                    illustrationSource = metadata.source,
                    illustrationKey = metadata.key,
                    illustrationPositionX = metadata.positionX,
                    illustrationPositionY = metadata.positionY,
                )

                if (categoryId == null) {
                    repository.createCategory(input)
                } else {
                    repository.updateCategory(input)
                }

                if (
                    shouldRemovePreviousCategoryUpload(
                        oldSource = currentCategory?.illustrationSource,
                        oldKey = oldUploadPath,
                        newKey = metadata.key,
                    )
                ) {
                    runCatching { repository.removeCategoryIllustration(oldUploadPath!!) }
                }

                _uiState.value = _uiState.value.copy(
                    saving = false,
                    savedMessage = if (categoryId == null) {
                        "Categoria criada com sucesso."
                    } else {
                        "Categoria atualizada com sucesso."
                    },
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error

                if (uploadedPath != null) {
                    runCatching { repository.removeCategoryIllustration(uploadedPath) }
                }

                _uiState.value = _uiState.value.copy(
                    saving = false,
                    errorMessage = categoryErrorMessage(error),
                )
            }
        }
    }

    fun save(name: String) {
        save(
            name = name,
            illustration = _uiState.value.category?.toIllustrationDraft()
                ?: emptyCategoryIllustrationDraft(),
        )
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

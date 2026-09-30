package com.babycatbe.nevesestoque.feature.products

import androidx.compose.ui.focus.FocusRequester
import com.babycatbe.nevesestoque.ui.input.NevesNumericField
import com.babycatbe.nevesestoque.ui.input.NevesNumericKeypad
import com.babycatbe.nevesestoque.ui.input.rememberNumericKeypadState
import com.babycatbe.nevesestoque.ui.input.requestFocusSafely
import com.babycatbe.nevesestoque.ui.components.NevesContentCard
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProductFormRoute(
    productId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onMerge: () -> Unit = {},
    onDeleted: (String) -> Unit = {},
    onProductChanged: () -> Unit = {},
) {
    val vm: ProductFormViewModel = viewModel(
        key = "product-form-${productId ?: "new"}",
        factory = ProductFormViewModel.Factory(productId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    // The existing operation ViewModel is owned by this edit entry, including its dialogs.
    val maintenanceVm: ProductDetailViewModel? = if (productId != null) viewModel(
        key = "product-maintenance-$productId",
        factory = ProductDetailViewModel.Factory(productId),
    ) else null
    val maintenanceState = maintenanceVm?.uiState?.collectAsStateWithLifecycle()?.value
    val maintenanceBusy = maintenanceState?.let { it.deleting || it.deleted || it.convertingUnit } == true
    val busy = state.saving || maintenanceBusy
    BackHandler { if (!busy) onBack() }

    LaunchedEffect(maintenanceState?.deleted) {
        if (maintenanceState?.deleted == true) {
            onDeleted("Produto enviado para a Lixeira. Ele pode ser restaurado por 7 dias.")
        }
    }
    LaunchedEffect(maintenanceState?.conversionNotice) {
        if (maintenanceState?.conversionNotice != null) onProductChanged()
    }
    ProductFormScreen(
        productId = productId,
        state = state,
        onBack = onBack,
        onSave = vm::save,
        onRetry = vm::retryLoad,
        busy = busy,
        maintenanceContent = {
            if (maintenanceVm != null && maintenanceState != null) {
                ProductMaintenanceSection(
                    state = maintenanceState,
                    enabled = !state.saving,
                    onMerge = onMerge,
                    onDelete = maintenanceVm::deleteProduct,
                    onConvertUnit = maintenanceVm::convertUnit,
                    onClearConversionError = maintenanceVm::clearConversionError,
                    onConsumeConversionNotice = maintenanceVm::consumeConversionNotice,
                    onRefresh = maintenanceVm::refresh,
                )
            }
        },
    )

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { message ->
            vm.consumeSavedMessage()
            onSaved(message)
        }
    }
}

@Composable
private fun ProductFormScreen(
    productId: String?,
    state: ProductFormUiState,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
    onRetry: () -> Unit,
    busy: Boolean,
    maintenanceContent: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keypad = rememberNumericKeypadState()
    val priceFocus = remember { FocusRequester() }
    val editing = productId != null
    var initialized by rememberSaveable(productId) { mutableStateOf(false) }
    var name by rememberSaveable(productId) { mutableStateOf("") }
    var categoryId by rememberSaveable(productId) { mutableStateOf("") }
    var unit by rememberSaveable(productId) { mutableStateOf("") }
    var initialStock by rememberSaveable(productId) { mutableStateOf("") }
    var initialPrice by rememberSaveable(productId) { mutableStateOf("") }

    LaunchedEffect(state.loading, state.product?.id) {
        if (!state.loading && !initialized && (state.product != null || !editing && state.errorMessage == null)) {
            state.product?.let { product ->
                name = product.name
                categoryId = product.categoryId.orEmpty()
                unit = product.unit
            }
            initialized = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            NevesTopBarSurface {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, enabled = !busy) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        if (editing) "Editar Produto" else "Novo Produto",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        bottomBar = { NevesNumericKeypad(keypad) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            if (state.loading) {
                NevesContentCard { Text("Carregando cadastro…", modifier = Modifier.padding(18.dp)) }
                return@Column
            }

            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                if (state.product == null && (editing || state.categories.isEmpty())) {
                    TextButton(onClick = onRetry, enabled = !busy) { Text("Tentar novamente") }
                }
            }

            if (state.categories.isNotEmpty() && (state.product != null || !editing)) {
                ProductImagePlaceholder()
                Text("Informações básicas", style = MaterialTheme.typography.titleMedium)
                Text("Nome", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    supportingText = state.fieldErrors.name?.let { { Text(it) } },
                    isError = state.fieldErrors.name != null,
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nome" },
                )

                Column {
                    Text("Categoria", style = MaterialTheme.typography.labelMedium)
                    ProductChoiceField(
                        value = state.categories.firstOrNull { it.id == categoryId }?.name ?: "Escolher Categoria",
                        options = state.categories.map { it.id to it.name },
                        enabled = !busy,
                        onSelect = { categoryId = it },
                    )
                    state.fieldErrors.category?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                if (!editing) {
                    Column {
                        Text("Unidade", style = MaterialTheme.typography.labelMedium)
                        ProductChoiceField(
                            value = unit.ifBlank { "Escolher Unidade" },
                            options = PRODUCT_UNITS.map { it to it },
                            enabled = !busy,
                            onSelect = { unit = it },
                        )
                        state.fieldErrors.unit?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    NevesNumericField(
                        value = initialStock,
                        onValueChange = { initialStock = it },
                        keypad = keypad,
                        label = "Estoque inicial (opcional)",
                        supportingMessage = "Vazio significa não informado.",
                        errorMessage = state.fieldErrors.initialStock,
                        onConfirm = { priceFocus.requestFocusSafely() },
                        enabled = !busy,
                    )

                    NevesNumericField(
                        value = initialPrice,
                        onValueChange = { initialPrice = it },
                        keypad = keypad,
                        label = "Preço inicial (opcional)",
                        supportingMessage = "Vazio significa não informado; zero é permitido.",
                        errorMessage = state.fieldErrors.initialPrice,
                        focusRequester = priceFocus,
                        onConfirm = { focusManager.clearFocus() },
                        confirmLabel = "Concluir",
                        enabled = !busy,
                    )
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSave(name, categoryId, unit, initialStock, initialPrice)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.saving) "Salvando…" else "Salvar Produto") }
                if (editing) maintenanceContent()
            } else if (state.errorMessage == null) {
                NevesContentCard {
                    Text(
                        "Cadastre ao menos uma Categoria antes de criar Produtos.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryFormRoute(
    categoryId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm: CategoryFormViewModel = viewModel(
        key = "category-form-${categoryId ?: "new"}",
        factory = CategoryFormViewModel.Factory(categoryId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(categoryId) { mutableStateOf("") }
    var illustration by remember(categoryId) { mutableStateOf(emptyCategoryIllustrationDraft()) }
    var fileError by remember(categoryId) { mutableStateOf<String?>(null) }
    var initialized by remember(categoryId) { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            fileError = null
            try {
                val selected = readCategoryIllustrationSelection(context, uri)
                illustration = CategoryIllustrationDraft(
                    source = "upload",
                    key = null,
                    imageBytes = selected.bytes,
                    mimeType = selected.mimeType,
                    positionX = 50,
                    positionY = 50,
                )
            } catch (error: Throwable) {
                fileError = error.message ?: "Não foi possível ler a imagem selecionada."
            }
        }
    }

    LaunchedEffect(state.loading, state.category?.id) {
        if (!state.loading && !initialized) {
            name = state.category?.name.orEmpty()
            illustration = state.category?.toIllustrationDraft()
                ?: emptyCategoryIllustrationDraft()
            initialized = true
        }
    }
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { message ->
            vm.consumeSavedMessage()
            onSaved(message)
        }
    }

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack, enabled = !state.saving) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        if (categoryId == null) "Nova Categoria" else "Editar Categoria",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                }
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading) {
                NevesContentCard { Text("Carregando cadastro…", modifier = Modifier.padding(18.dp)) }
                return@Column
            }

            state.errorMessage?.let { message ->
                NevesContentCard {
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }

            if (state.errorMessage == null) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    supportingText = state.nameError?.let { { Text(it) } },
                    isError = state.nameError != null,
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )

                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        CategoryIllustrationEditor(
                            value = illustration,
                            categoryName = name,
                            fileError = fileError ?: state.illustrationError,
                            enabled = !state.saving,
                            onChange = {
                                illustration = it
                                fileError = null
                            },
                            onPickUpload = { imagePicker.launch("image/*") },
                        )
                    }
                }

                Button(
                    onClick = { vm.save(name, illustration) },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.saving) "Salvando…" else "Salvar Categoria") }
            }
        }
    }
}


private data class SelectedCategoryIllustration(
    val bytes: ByteArray,
    val mimeType: String,
)

private suspend fun readCategoryIllustrationSelection(
    context: Context,
    uri: Uri,
): SelectedCategoryIllustration = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri)?.lowercase()
    val declaredSize = resolver.query(
        uri,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
            cursor.getLong(index)
        } else {
            null
        }
    }

    if (declaredSize != null && declaredSize > MAX_CATEGORY_ILLUSTRATION_SIZE) {
        error("A imagem pode ter no máximo 5 MB.")
    }

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Não foi possível ler a imagem selecionada.")

    validateCategoryIllustrationFile(mimeType, bytes.size)?.let(::error)
    SelectedCategoryIllustration(bytes = bytes, mimeType = mimeType!!)
}


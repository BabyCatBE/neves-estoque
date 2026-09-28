package com.babycatbe.nevesestoque.feature.products

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
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
) {
    val vm: ProductFormViewModel = viewModel(
        key = "product-form-${productId ?: "new"}",
        factory = ProductFormViewModel.Factory(productId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    ProductFormScreen(productId, state, onBack, vm::save)

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
) {
    val editing = productId != null
    var initialized by rememberSaveable(productId) { mutableStateOf(false) }
    var name by rememberSaveable(productId) { mutableStateOf("") }
    var categoryId by rememberSaveable(productId) { mutableStateOf("") }
    var unit by rememberSaveable(productId) { mutableStateOf("") }
    var initialStock by rememberSaveable(productId) { mutableStateOf("") }
    var initialPrice by rememberSaveable(productId) { mutableStateOf("") }
    var categoryMenu by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading, state.product?.id) {
        if (!state.loading && !initialized) {
            state.product?.let { product ->
                name = product.name
                categoryId = product.categoryId.orEmpty()
                unit = product.unit
            }
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack, enabled = !state.saving) { Text("Voltar") }
                    Text(
                        if (editing) "Editar Produto" else "Novo Produto",
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
                Card { Text("Carregando cadastro…", modifier = Modifier.padding(18.dp)) }
                return@Column
            }

            state.errorMessage?.let { message ->
                Card {
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }

            if (state.categories.isNotEmpty() && (state.product != null || !editing)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    supportingText = state.fieldErrors.name?.let { { Text(it) } },
                    isError = state.fieldErrors.name != null,
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column {
                    Text("Categoria", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(
                        onClick = { categoryMenu = true },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    ) {
                        Text(state.categories.firstOrNull { it.id == categoryId }?.name ?: "Escolher Categoria")
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        state.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    categoryMenu = false
                                },
                            )
                        }
                    }
                    state.fieldErrors.category?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                if (editing) {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Unidade", style = MaterialTheme.typography.labelSmall)
                            Text(unit, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                            Text(
                                "A Unidade não é alterada nesta etapa.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    Column {
                        Text("Unidade", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(
                            onClick = { unitMenu = true },
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        ) { Text(unit.ifBlank { "Escolher Unidade" }) }
                        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                            PRODUCT_UNITS.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = { unit = option; unitMenu = false },
                                )
                            }
                        }
                        state.fieldErrors.unit?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedTextField(
                        value = initialStock,
                        onValueChange = { initialStock = it },
                        label = { Text("Estoque inicial (opcional)") },
                        supportingText = {
                            Text(state.fieldErrors.initialStock ?: "Vazio significa não informado.")
                        },
                        isError = state.fieldErrors.initialStock != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = initialPrice,
                        onValueChange = { initialPrice = it },
                        label = { Text("Preço inicial (opcional)") },
                        supportingText = {
                            Text(state.fieldErrors.initialPrice ?: "Vazio significa não informado; zero é permitido.")
                        },
                        isError = state.fieldErrors.initialPrice != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Button(
                    onClick = { onSave(name, categoryId, unit, initialStock, initialPrice) },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.saving) "Salvando…" else "Salvar Produto") }
            } else if (state.errorMessage == null) {
                Card {
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
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack, enabled = !state.saving) { Text("Voltar") }
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
                Card { Text("Carregando cadastro…", modifier = Modifier.padding(18.dp)) }
                return@Column
            }

            state.errorMessage?.let { message ->
                Card {
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

                Card {
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

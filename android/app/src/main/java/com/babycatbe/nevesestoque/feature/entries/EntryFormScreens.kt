package com.babycatbe.nevesestoque.feature.entries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.UUID

private data class EntryLineUi(
    val localId: String,
    val product: EntryProductOption,
    val draftProduct: EntryDraftProduct? = null,
    val quantity: String = "",
    val unitPrice: String = "",
)

private data class EntryItemFieldErrors(
    val quantity: String? = null,
    val unitPrice: String? = null,
)

private data class EditEntryLineUi(
    val localId: String,
    val productId: String,
    val productName: String,
    val unit: String,
    val quantity: String,
    val unitPrice: String,
)

@Composable
fun NewEntryRoute(
    onBack: () -> Unit,
    onSaved: (String, String) -> Unit,
) {
    val vm: NewEntryViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state.savedEntryId) {
        state.savedEntryId?.let { onSaved(it, "Entrada salva com sucesso.") }
    }

    NewEntryScreen(
        state = state,
        onBack = onBack,
        onSave = vm::save,
        onRefresh = vm::refreshOptions,
        onCompletePendingProduct = vm::completePendingProduct,
        onClearError = vm::clearError,
    )
}

@Composable
private fun NewEntryScreen(
    state: NewEntryUiState,
    onBack: () -> Unit,
    onSave: (EntryCreateInput) -> Unit,
    onRefresh: () -> Unit,
    onCompletePendingProduct: suspend (String, String, String) -> EntryProductOption,
    onClearError: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val options = state.options

    var supplierId by rememberSaveable { mutableStateOf("") }
    var draftSupplierName by rememberSaveable { mutableStateOf<String?>(null) }
    var supplierSearch by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayEntryDate()) }
    var observation by rememberSaveable { mutableStateOf("") }
    var productSearch by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<EntryLineUi>>(emptyList()) }
    var itemErrors by remember { mutableStateOf<Map<String, EntryItemFieldErrors>>(emptyMap()) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }

    var quickSupplierOpen by rememberSaveable { mutableStateOf(false) }
    var quickSupplierName by rememberSaveable { mutableStateOf("") }
    var quickSupplierError by rememberSaveable { mutableStateOf<String?>(null) }

    var quickProductOpen by rememberSaveable { mutableStateOf(false) }
    var quickProductName by rememberSaveable { mutableStateOf("") }
    var quickProductUnit by rememberSaveable { mutableStateOf("UN") }
    var quickProductCategoryId by rememberSaveable { mutableStateOf("") }
    var quickProductError by rememberSaveable { mutableStateOf<String?>(null) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var quickCategoryMenuOpen by remember { mutableStateOf(false) }

    var pendingProduct by remember { mutableStateOf<EntryProductOption?>(null) }
    var pendingCategoryId by rememberSaveable { mutableStateOf("") }
    var pendingCategoryMenuOpen by remember { mutableStateOf(false) }
    var pendingBusy by rememberSaveable { mutableStateOf(false) }
    var pendingError by rememberSaveable { mutableStateOf<String?>(null) }

    var duplicateProduct by remember { mutableStateOf<Pair<EntryProductOption, EntryDraftProduct?>?>(null) }
    var missingPriceOpen by rememberSaveable { mutableStateOf(false) }
    var leaveOpen by rememberSaveable { mutableStateOf(false) }

    val dirty = supplierId.isNotBlank() || draftSupplierName != null || supplierSearch.isNotBlank() ||
        observation.isNotBlank() || productSearch.isNotBlank() || items.isNotEmpty() || date != todayEntryDate()

    fun clearLocalError() {
        actionError = null
        onClearError()
    }

    fun addProduct(
        product: EntryProductOption,
        draft: EntryDraftProduct? = null,
        forceDuplicate: Boolean = false,
    ) {
        if (!forceDuplicate && items.any { it.product.id == product.id }) {
            duplicateProduct = product to draft
            return
        }
        items = items + EntryLineUi(
            localId = UUID.randomUUID().toString(),
            product = product,
            draftProduct = draft ?: items.firstOrNull { it.product.id == product.id }?.draftProduct,
        )
        productSearch = ""
        duplicateProduct = null
        clearLocalError()
    }

    fun chooseProduct(product: EntryProductOption, draft: EntryDraftProduct? = null) {
        if (draft == null && product.categoryId == null && !product.id.startsWith("draft:")) {
            pendingProduct = product
            pendingCategoryId = ""
            pendingError = null
        } else {
            addProduct(product, draft)
        }
    }

    fun buildInput(): EntryCreateInput? {
        clearLocalError()
        itemErrors = emptyMap()

        if (supplierId.isBlank() && draftSupplierName == null) {
            actionError = "Selecione um Fornecedor."
            return null
        }
        entryDateError(date)?.let {
            actionError = it
            return null
        }
        validateEntryObservation(observation)?.let {
            actionError = it
            return null
        }
        if (items.isEmpty()) {
            actionError = "Adicione pelo menos um Produto."
            return null
        }

        val errors = mutableMapOf<String, EntryItemFieldErrors>()
        val parsed = items.map { line ->
            val quantity = parseEntryPositiveDecimal(line.quantity, "Quantidade")
            val price = parseEntryOptionalPrice(line.unitPrice)
            if (quantity.error != null || price.error != null) {
                errors[line.localId] = EntryItemFieldErrors(quantity.error, price.error)
            }
            EntryCreateItem(
                productId = if (line.draftProduct == null) line.product.id else null,
                newProduct = line.draftProduct,
                quantity = quantity.value ?: 0.0,
                unitPrice = price.value,
            )
        }

        if (errors.isNotEmpty()) {
            itemErrors = errors
            actionError = "Revise os campos destacados antes de salvar a Entrada."
            return null
        }

        return EntryCreateInput(
            supplierId = supplierId.takeIf { draftSupplierName == null && it.isNotBlank() },
            newSupplier = draftSupplierName?.let(::EntryDraftSupplier),
            effectiveAt = buildEntryEffectiveAt(date),
            idempotencyKey = "",
            observation = observation.trim().ifBlank { null },
            items = parsed,
        )
    }

    fun requestSave() {
        val input = buildInput() ?: return
        if (input.items.any { it.unitPrice == null }) {
            missingPriceOpen = true
        } else {
            onSave(input)
        }
    }

    BackHandler(enabled = state.saving) {}
    BackHandler(enabled = !state.saving && dirty) { leaveOpen = true }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(
                        onClick = { if (dirty) leaveOpen = true else onBack() },
                        enabled = !state.saving,
                    ) { Text("Voltar") }
                    Text(
                        "Nova Entrada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(onClick = onRefresh, enabled = !state.saving) { Text("Atualizar") }
                }
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Mercadoria recebida", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Registre somente mercadoria que realmente chegou à Panificadora.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.loading) {
                Card { Text("Carregando dados…", modifier = Modifier.padding(18.dp)) }
            }

            state.errorMessage?.let { error ->
                Card {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }

            if (!state.loading && options != null) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Fornecedor *", style = MaterialTheme.typography.labelMedium)
                        val selected = options.suppliers.firstOrNull { it.id == supplierId }
                        if (selected != null || draftSupplierName != null) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(draftSupplierName ?: selected?.name.orEmpty(), fontWeight = FontWeight.Bold)
                                    Text(
                                        if (draftSupplierName != null) "Novo nesta Entrada"
                                        else selected?.company ?: "Cadastro pendente",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = {
                                    supplierId = ""
                                    draftSupplierName = null
                                    supplierSearch = ""
                                }) { Text("Trocar") }
                            }
                        } else {
                            OutlinedTextField(
                                value = supplierSearch,
                                onValueChange = { supplierSearch = it },
                                label = { Text("Buscar Fornecedor") },
                                placeholder = { Text("Contato, empresa ou telefone") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                            if (supplierSearch.isNotBlank()) {
                                options.suppliers
                                    .filter { entrySupplierMatches(it, supplierSearch) }
                                    .take(8)
                                    .forEach { supplier ->
                                        TextButton(
                                            onClick = {
                                                supplierId = supplier.id
                                                draftSupplierName = null
                                                supplierSearch = ""
                                                clearLocalError()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                supplier.name + (supplier.company?.let { " — $it" } ?: " — pendente"),
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                TextButton(
                                    onClick = {
                                        quickSupplierName = supplierSearch
                                        quickSupplierError = null
                                        quickSupplierOpen = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("+ Cadastrar novo Fornecedor", modifier = Modifier.fillMaxWidth()) }
                            }
                        }

                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Data *") },
                            placeholder = { Text("AAAA-MM-DD") },
                            supportingText = { entryDateError(date)?.let { Text(it) } },
                            isError = entryDateError(date) != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )

                        OutlinedTextField(
                            value = observation,
                            onValueChange = { if (it.length <= 2000) observation = it },
                            label = { Text("Observação") },
                            placeholder = { Text("Observação única para esta Entrada.") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }

                Text("Produtos recebidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = productSearch,
                    onValueChange = { productSearch = it },
                    label = { Text("Adicionar Produto") },
                    placeholder = { Text("Buscar por nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (productSearch.isNotBlank()) {
                    val localDrafts = items.filter { it.draftProduct != null }
                        .associateBy { it.product.id }
                        .values
                        .map { it.product to it.draftProduct }
                    val existing = options.products
                        .filter { entryProductMatches(it, productSearch) }
                        .take(10)
                        .map { it to null }
                    (existing + localDrafts.filter { entryProductMatches(it.first, productSearch) })
                        .distinctBy { it.first.id }
                        .take(10)
                        .forEach { (product, draft) ->
                            TextButton(
                                onClick = { chooseProduct(product, draft) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    product.name + " · " + product.unit +
                                        if (product.categoryId == null && draft == null) " · cadastro pendente" else "",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    TextButton(
                        onClick = {
                            quickProductName = productSearch
                            quickProductError = null
                            quickProductOpen = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("+ Cadastrar novo Produto", modifier = Modifier.fillMaxWidth()) }
                }

                if (items.isEmpty()) {
                    Card { Text("Adicione ao menos um Produto recebido.", modifier = Modifier.padding(18.dp)) }
                }

                items.forEachIndexed { index, line ->
                    val quantity = parseEntryPositiveDecimal(line.quantity, "Quantidade").value
                    val price = parseEntryOptionalPrice(line.unitPrice).value
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("ITEM \${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(line.product.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                                    Text("Unidade: \${line.product.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    items = items.filterNot { it.localId == line.localId }
                                    itemErrors = itemErrors - line.localId
                                }) { Text("Remover") }
                            }
                            OutlinedTextField(
                                value = line.quantity,
                                onValueChange = { value ->
                                    items = items.map {
                                        if (it.localId == line.localId) it.copy(quantity = value) else it
                                    }
                                    itemErrors = itemErrors - line.localId
                                },
                                label = { Text("Quantidade *") },
                                supportingText = itemErrors[line.localId]?.quantity?.let { { Text(it) } },
                                isError = itemErrors[line.localId]?.quantity != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            OutlinedTextField(
                                value = line.unitPrice,
                                onValueChange = { value ->
                                    items = items.map {
                                        if (it.localId == line.localId) it.copy(unitPrice = value) else it
                                    }
                                    itemErrors = itemErrors - line.localId
                                },
                                label = { Text("Preço unitário") },
                                placeholder = { Text("Vazio = não informado") },
                                supportingText = itemErrors[line.localId]?.unitPrice?.let { { Text(it) } },
                                isError = itemErrors[line.localId]?.unitPrice != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            val totalLabel = when {
                                quantity != null && price == 0.0 -> "Bonificação"
                                quantity != null && price != null -> formatEntryMoney(quantity * price)
                                else -> "—"
                            }
                            EntryInfoLine("Total", totalLabel)
                        }
                    }
                }

                val totalKnown = items.sumOf { line ->
                    val q = parseEntryPositiveDecimal(line.quantity, "Quantidade").value
                    val p = parseEntryOptionalPrice(line.unitPrice).value
                    if (q != null && p != null) q * p else 0.0
                }
                val missingPrices = items.count {
                    it.quantity.isNotBlank() && parseEntryOptionalPrice(it.unitPrice).error == null &&
                        parseEntryOptionalPrice(it.unitPrice).value == null
                }

                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        EntryInfoLine("Total conhecido", formatEntryMoney(totalKnown) + if (missingPrices > 0) " *" else "")
                        if (missingPrices > 0) {
                            Text(
                                "$missingPrices item(ns) sem preço.",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                        Button(
                            onClick = ::requestSave,
                            enabled = !state.saving && !pendingBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) { Text(if (state.saving) "Salvando…" else "Salvar Entrada") }
                    }
                }

                actionError?.let {
                    Card { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }

    if (quickSupplierOpen) {
        AlertDialog(
            onDismissRequest = { quickSupplierOpen = false },
            title = { Text("Novo Fornecedor") },
            text = {
                Column {
                    Text("Informe somente o nome. O cadastro será criado junto com a Entrada.")
                    OutlinedTextField(
                        value = quickSupplierName,
                        onValueChange = { quickSupplierName = it; quickSupplierError = null },
                        label = { Text("Nome *") },
                        supportingText = quickSupplierError?.let { { Text(it) } },
                        isError = quickSupplierError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val error = validateQuickSupplierName(quickSupplierName)
                    if (error != null) {
                        quickSupplierError = error
                    } else {
                        draftSupplierName = normalizeQuickSupplierName(quickSupplierName)
                        supplierId = ""
                        supplierSearch = ""
                        quickSupplierOpen = false
                        clearLocalError()
                    }
                }) { Text("Cadastrar e selecionar") }
            },
            dismissButton = { TextButton(onClick = { quickSupplierOpen = false }) { Text("Cancelar") } },
        )
    }

    if (quickProductOpen && options != null) {
        AlertDialog(
            onDismissRequest = { quickProductOpen = false },
            title = { Text("Novo Produto") },
            text = {
                Column {
                    Text("Nome e Unidade são obrigatórios. Categoria é opcional; sem Categoria o cadastro ficará pendente.")
                    OutlinedTextField(
                        value = quickProductName,
                        onValueChange = { quickProductName = it; quickProductError = null },
                        label = { Text("Nome *") },
                        supportingText = quickProductError?.let { { Text(it) } },
                        isError = quickProductError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Text("Unidade *", modifier = Modifier.padding(top = 10.dp))
                    Box {
                        OutlinedButton(onClick = { unitMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(quickProductUnit)
                        }
                        DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                            ENTRY_PRODUCT_UNITS.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit) },
                                    onClick = { quickProductUnit = unit; unitMenuOpen = false },
                                )
                            }
                        }
                    }
                    Text("Categoria", modifier = Modifier.padding(top = 10.dp))
                    Box {
                        OutlinedButton(onClick = { quickCategoryMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(options.categories.firstOrNull { it.id == quickProductCategoryId }?.name ?: "Sem categoria — pendente")
                        }
                        DropdownMenu(expanded = quickCategoryMenuOpen, onDismissRequest = { quickCategoryMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Sem categoria — pendente") },
                                onClick = { quickProductCategoryId = ""; quickCategoryMenuOpen = false },
                            )
                            options.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = { quickProductCategoryId = category.id; quickCategoryMenuOpen = false },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clientId = UUID.randomUUID().toString()
                    val validation = validateQuickProduct(
                        clientId,
                        quickProductName,
                        quickProductUnit,
                        quickProductCategoryId.ifBlank { null },
                    )
                    if (validation.error != null || validation.product == null) {
                        quickProductError = validation.error ?: "Produto inválido."
                    } else {
                        val draft = validation.product
                        val option = EntryProductOption(
                            id = "draft:\${draft.clientId}",
                            name = draft.name,
                            categoryId = draft.categoryId,
                            unit = draft.unit,
                            sortOrder = null,
                            createdAt = OffsetDateTime.now().toString(),
                        )
                        quickProductOpen = false
                        quickProductName = ""
                        quickProductUnit = "UN"
                        quickProductCategoryId = ""
                        addProduct(option, draft)
                    }
                }) { Text("Adicionar à Entrada") }
            },
            dismissButton = { TextButton(onClick = { quickProductOpen = false }) { Text("Cancelar") } },
        )
    }

    if (pendingProduct != null && options != null) {
        val product = pendingProduct!!
        AlertDialog(
            onDismissRequest = { if (!pendingBusy) pendingProduct = null },
            title = { Text("Cadastro pendente") },
            text = {
                Column {
                    Text("“\${product.name}” está sem Categoria. Escolha uma Categoria para concluir o cadastro e adicioná-lo à Entrada.")
                    Text("Categoria *", modifier = Modifier.padding(top = 10.dp))
                    Box {
                        OutlinedButton(onClick = { pendingCategoryMenuOpen = true }, enabled = !pendingBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(options.categories.firstOrNull { it.id == pendingCategoryId }?.name ?: "Selecionar Categoria")
                        }
                        DropdownMenu(expanded = pendingCategoryMenuOpen, onDismissRequest = { pendingCategoryMenuOpen = false }) {
                            options.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = { pendingCategoryId = category.id; pendingCategoryMenuOpen = false; pendingError = null },
                                )
                            }
                        }
                    }
                    pendingError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pendingCategoryId.isBlank()) {
                            pendingError = "Escolha uma Categoria para continuar."
                        } else {
                            pendingBusy = true
                            pendingError = null
                            scope.launch {
                                try {
                                    val completed = onCompletePendingProduct(product.id, product.name, pendingCategoryId)
                                    pendingBusy = false
                                    pendingProduct = null
                                    pendingCategoryId = ""
                                    addProduct(completed)
                                } catch (error: Throwable) {
                                    pendingBusy = false
                                    pendingError = entryErrorMessage(error)
                                }
                            }
                        }
                    },
                    enabled = !pendingBusy,
                ) { Text(if (pendingBusy) "Salvando…" else "Concluir e adicionar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingProduct = null }, enabled = !pendingBusy) { Text("Cancelar") }
            },
        )
    }

    duplicateProduct?.let { (product, draft) ->
        AlertDialog(
            onDismissRequest = { duplicateProduct = null },
            title = { Text("Produto já adicionado") },
            text = { Text("“\${product.name}” já está nesta Entrada. Deseja adicionar uma nova linha do mesmo Produto?") },
            confirmButton = {
                Button(onClick = { addProduct(product, draft, forceDuplicate = true) }) { Text("Adicionar novamente") }
            },
            dismissButton = { TextButton(onClick = { duplicateProduct = null }) { Text("Usar linha existente") } },
        )
    }

    if (missingPriceOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.saving) missingPriceOpen = false },
            title = { Text("Salvar com preço não informado?") },
            text = { Text("Há item(ns) sem preço. O total ficará parcial e somará apenas os itens com preço conhecido.") },
            confirmButton = {
                Button(
                    onClick = {
                        missingPriceOpen = false
                        buildInput()?.let(onSave)
                    },
                    enabled = !state.saving,
                ) { Text("Salvar mesmo assim") }
            },
            dismissButton = { TextButton(onClick = { missingPriceOpen = false }) { Text("Revisar") } },
        )
    }

    if (leaveOpen) {
        AlertDialog(
            onDismissRequest = { leaveOpen = false },
            title = { Text("Sair da Nova Entrada?") },
            text = { Text("Há dados preenchidos que ainda não foram salvos.") },
            confirmButton = { TextButton(onClick = onBack) { Text("Descartar e sair") } },
            dismissButton = { TextButton(onClick = { leaveOpen = false }) { Text("Continuar") } },
        )
    }
}

@Composable
fun EditEntryRoute(
    entryId: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm: EditEntryViewModel = viewModel(
        key = "edit-entry-\${entryId}",
        factory = EditEntryViewModel.Factory(entryId),
    )
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            vm.consumeSavedMessage()
            onSaved(it)
        }
    }

    EditEntryScreen(
        entryId = entryId,
        state = state,
        onBack = onBack,
        onSave = vm::save,
    )
}

@Composable
private fun EditEntryScreen(
    entryId: String,
    state: EditEntryUiState,
    onBack: () -> Unit,
    onSave: (EntryUpdateInput) -> Unit,
) {
    val data = state.data
    var initialized by rememberSaveable(entryId) { mutableStateOf(false) }
    var supplierId by rememberSaveable(entryId) { mutableStateOf("") }
    var date by rememberSaveable(entryId) { mutableStateOf("") }
    var observation by rememberSaveable(entryId) { mutableStateOf("") }
    var items by remember { mutableStateOf<List<EditEntryLineUi>>(emptyList()) }
    var initialSignature by rememberSaveable(entryId) { mutableStateOf("") }
    var productSearch by rememberSaveable(entryId) { mutableStateOf("") }
    var supplierMenuOpen by remember { mutableStateOf(false) }
    var duplicateProduct by remember { mutableStateOf<EntryProductOption?>(null) }
    var itemErrors by remember { mutableStateOf<Map<String, EntryItemFieldErrors>>(emptyMap()) }
    var actionError by rememberSaveable { mutableStateOf<String?>(null) }
    var missingPriceOpen by rememberSaveable { mutableStateOf(false) }
    var saveReviewOpen by rememberSaveable { mutableStateOf(false) }
    var leaveOpen by rememberSaveable { mutableStateOf(false) }

    fun signature(): String = buildString {
        append(supplierId).append('|').append(date).append('|').append(observation.trim())
        items.forEach { append('|').append(it.productId).append(':').append(it.quantity.trim()).append(':').append(it.unitPrice.trim()) }
    }

    LaunchedEffect(data?.entry?.id) {
        if (data != null && !initialized) {
            supplierId = data.entry.supplierId
            date = entryLocalDate(data.entry.effectiveAt)
            observation = data.entry.observation.orEmpty()
            items = data.entry.items.map {
                EditEntryLineUi(
                    localId = it.id,
                    productId = it.productId,
                    productName = it.productName,
                    unit = it.unit,
                    quantity = formatEntryNumber(it.quantity),
                    unitPrice = it.unitPrice?.let(::formatEntryNumber).orEmpty(),
                )
            }
            initialSignature = signature()
            initialized = true
        }
    }

    val dirty = initialized && signature() != initialSignature

    fun addProduct(product: EntryProductOption, forceDuplicate: Boolean = false) {
        if (product.categoryId == null) {
            actionError = "Este Produto está com cadastro pendente. Conclua a Categoria antes de adicioná-lo à edição."
            return
        }
        if (!forceDuplicate && items.any { it.productId == product.id }) {
            duplicateProduct = product
            return
        }
        items = items + EditEntryLineUi(
            localId = UUID.randomUUID().toString(),
            productId = product.id,
            productName = product.name,
            unit = product.unit,
            quantity = "",
            unitPrice = "",
        )
        productSearch = ""
        duplicateProduct = null
        actionError = null
    }

    fun buildInput(): EntryUpdateInput? {
        val entry = data?.entry ?: return null
        actionError = null
        itemErrors = emptyMap()
        if (supplierId.isBlank()) {
            actionError = "Selecione um Fornecedor."
            return null
        }
        entryDateError(date)?.let { actionError = it; return null }
        validateEntryObservation(observation)?.let { actionError = it; return null }
        if (items.isEmpty()) {
            actionError = "A Entrada precisa de pelo menos um item."
            return null
        }

        val errors = mutableMapOf<String, EntryItemFieldErrors>()
        val parsed = items.map { line ->
            val quantity = parseEntryPositiveDecimal(line.quantity, "Quantidade")
            val price = parseEntryOptionalPrice(line.unitPrice)
            if (quantity.error != null || price.error != null) {
                errors[line.localId] = EntryItemFieldErrors(quantity.error, price.error)
            }
            EntryUpdateItem(
                productId = line.productId,
                quantity = quantity.value ?: 0.0,
                unitPrice = price.value,
            )
        }
        if (errors.isNotEmpty()) {
            itemErrors = errors
            actionError = "Revise os campos destacados antes de salvar a Entrada."
            return null
        }

        return EntryUpdateInput(
            entryId = entryId,
            supplierId = supplierId,
            effectiveAt = buildEntryEffectiveAt(date, entryInstant(entry.effectiveAt)),
            observation = observation.trim().ifBlank { null },
            items = parsed,
        )
    }

    fun requestSave() {
        val input = buildInput() ?: return
        if (input.items.any { it.unitPrice == null }) missingPriceOpen = true
        else saveReviewOpen = true
    }

    BackHandler(enabled = state.saving) {}
    BackHandler(enabled = !state.saving && dirty) { leaveOpen = true }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(
                        onClick = { if (dirty) leaveOpen = true else onBack() },
                        enabled = !state.saving,
                    ) { Text("Voltar") }
                    Text(
                        "Editar Entrada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                }
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            if (state.loading) {
                Card { Text("Carregando Entrada…", modifier = Modifier.padding(18.dp)) }
            }
            state.errorMessage?.let {
                Card { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
            if (!state.loading && data != null) {
                Text("Edição histórica", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Ao salvar, estoque e preços derivados serão recalculados automaticamente.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Fornecedor *", style = MaterialTheme.typography.labelMedium)
                        Box {
                            OutlinedButton(
                                onClick = { supplierMenuOpen = true },
                                enabled = !state.saving,
                                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            ) {
                                Text(
                                    data.options.suppliers.firstOrNull { it.id == supplierId }?.let {
                                        it.name + (it.company?.let { company -> " — $company" } ?: " — pendente")
                                    } ?: "Selecionar Fornecedor"
                                )
                            }
                            DropdownMenu(expanded = supplierMenuOpen, onDismissRequest = { supplierMenuOpen = false }) {
                                data.options.suppliers.forEach { supplier ->
                                    DropdownMenuItem(
                                        text = { Text(supplier.name + (supplier.company?.let { " — $it" } ?: " — pendente")) },
                                        onClick = { supplierId = supplier.id; supplierMenuOpen = false; actionError = null },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Data *") },
                            placeholder = { Text("AAAA-MM-DD") },
                            supportingText = { entryDateError(date)?.let { Text(it) } },
                            isError = entryDateError(date) != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        OutlinedTextField(
                            value = observation,
                            onValueChange = { if (it.length <= 2000) observation = it },
                            label = { Text("Observação") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = productSearch,
                    onValueChange = { productSearch = it },
                    label = { Text("Adicionar Produto") },
                    placeholder = { Text("Buscar por nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (productSearch.isNotBlank()) {
                    data.options.products
                        .filter { entryProductMatches(it, productSearch) }
                        .take(10)
                        .forEach { product ->
                            TextButton(onClick = { addProduct(product) }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    product.name + " · " + product.unit +
                                        if (product.categoryId == null) " · cadastro pendente" else "",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                }

                items.forEachIndexed { index, line ->
                    val quantity = parseEntryPositiveDecimal(line.quantity, "Quantidade").value
                    val price = parseEntryOptionalPrice(line.unitPrice).value
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("ITEM \${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(line.productName, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                                    Text("Unidade: \${line.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    items = items.filterNot { it.localId == line.localId }
                                    itemErrors = itemErrors - line.localId
                                }) { Text("Remover") }
                            }
                            OutlinedTextField(
                                value = line.quantity,
                                onValueChange = { value ->
                                    items = items.map { if (it.localId == line.localId) it.copy(quantity = value) else it }
                                    itemErrors = itemErrors - line.localId
                                },
                                label = { Text("Quantidade *") },
                                supportingText = itemErrors[line.localId]?.quantity?.let { { Text(it) } },
                                isError = itemErrors[line.localId]?.quantity != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            OutlinedTextField(
                                value = line.unitPrice,
                                onValueChange = { value ->
                                    items = items.map { if (it.localId == line.localId) it.copy(unitPrice = value) else it }
                                    itemErrors = itemErrors - line.localId
                                },
                                label = { Text("Preço unitário") },
                                placeholder = { Text("Vazio = não informado") },
                                supportingText = itemErrors[line.localId]?.unitPrice?.let { { Text(it) } },
                                isError = itemErrors[line.localId]?.unitPrice != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            EntryInfoLine(
                                "Total",
                                when {
                                    quantity != null && price == 0.0 -> "Bonificação"
                                    quantity != null && price != null -> formatEntryMoney(quantity * price)
                                    else -> "—"
                                },
                            )
                        }
                    }
                }

                val totalKnown = items.sumOf { line ->
                    val q = parseEntryPositiveDecimal(line.quantity, "Quantidade").value
                    val p = parseEntryOptionalPrice(line.unitPrice).value
                    if (q != null && p != null) q * p else 0.0
                }
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        EntryInfoLine("Total conhecido", formatEntryMoney(totalKnown))
                        Button(
                            onClick = ::requestSave,
                            enabled = dirty && !state.saving,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) { Text(if (state.saving) "Salvando…" else "Salvar alterações") }
                    }
                }
                actionError?.let {
                    Card { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }

    duplicateProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { duplicateProduct = null },
            title = { Text("Produto já adicionado") },
            text = { Text("“\${product.name}” já está nesta Entrada. Deseja adicionar uma nova linha?") },
            confirmButton = { Button(onClick = { addProduct(product, true) }) { Text("Adicionar novamente") } },
            dismissButton = { TextButton(onClick = { duplicateProduct = null }) { Text("Usar linha existente") } },
        )
    }

    if (missingPriceOpen) {
        AlertDialog(
            onDismissRequest = { missingPriceOpen = false },
            title = { Text("Continuar com preço não informado?") },
            text = { Text("Há item(ns) sem preço. O total continuará parcial. Deseja revisar as alterações mesmo assim?") },
            confirmButton = {
                Button(onClick = { missingPriceOpen = false; saveReviewOpen = true }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { missingPriceOpen = false }) { Text("Revisar") } },
        )
    }

    if (saveReviewOpen && data != null) {
        val changes = buildEditChangeSummary(
            original = data.entry,
            supplierId = supplierId,
            date = date,
            observation = observation,
            items = items,
        )
        AlertDialog(
            onDismissRequest = { if (!state.saving) saveReviewOpen = false },
            title = { Text("Salvar edição histórica?") },
            text = {
                Text(
                    "Esta alteração recalculará estoque, preços e dependências posteriores.\n\n" +
                        changes.joinToString("\n") { "• $it" }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        buildInput()?.let(onSave)
                    },
                    enabled = !state.saving,
                ) { Text(if (state.saving) "Salvando…" else "Salvar alterações") }
            },
            dismissButton = {
                TextButton(onClick = { saveReviewOpen = false }, enabled = !state.saving) { Text("Cancelar") }
            },
        )
    }

    if (leaveOpen) {
        AlertDialog(
            onDismissRequest = { leaveOpen = false },
            title = { Text("Descartar alterações?") },
            text = { Text("Existem alterações não salvas nesta Entrada.") },
            confirmButton = { TextButton(onClick = onBack) { Text("Descartar e sair") } },
            dismissButton = { TextButton(onClick = { leaveOpen = false }) { Text("Continuar editando") } },
        )
    }
}

private fun buildEditChangeSummary(
    original: EntryDetails,
    supplierId: String,
    date: String,
    observation: String,
    items: List<EditEntryLineUi>,
): List<String> {
    val changes = mutableListOf<String>()
    if (original.supplierId != supplierId) changes += "Fornecedor alterado."
    if (entryLocalDate(original.effectiveAt) != date) changes += "Data alterada."
    if (original.observation.orEmpty().trim() != observation.trim()) changes += "Observação alterada."
    val oldItems = original.items.map {
        "\${it.productId}|\${formatEntryNumber(it.quantity)}|\${it.unitPrice?.let(::formatEntryNumber).orEmpty()}"
    }
    val newItems = items.map { "\${it.productId}|\${it.quantity.trim()}|\${it.unitPrice.trim()}" }
    if (oldItems != newItems) changes += "Itens recebidos alterados (\${original.items.size} → \${items.size} linhas)."
    return changes.ifEmpty { listOf("Nenhuma alteração detectada.") }
}

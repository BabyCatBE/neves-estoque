package com.babycatbe.nevesestoque.feature.purchases

import com.babycatbe.nevesestoque.ui.theme.NevesColors
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babycatbe.nevesestoque.ui.load.LatestLoad
import com.babycatbe.nevesestoque.ui.load.loadCatching
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------- Estado ----------

data class PurchasesUiState(
    val loading: Boolean = true,
    val data: PurchaseData? = null,
    val errorMessage: String? = null,
)

class PurchasesViewModel : ViewModel() {
    private val repository = PurchasesRepository()
    private val _uiState = MutableStateFlow(PurchasesUiState())
    val uiState: StateFlow<PurchasesUiState> = _uiState.asStateFlow()
    private val latestLoad = LatestLoad()

    init {
        refresh()
    }

    fun refresh() {
        latestLoad.launch(viewModelScope) {
            _uiState.value = _uiState.value.copy(loading = _uiState.value.data == null, errorMessage = null)
            loadCatching { repository.loadPurchaseData() }
                .onSuccess { _uiState.value = PurchasesUiState(loading = false, data = it) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        errorMessage = "Não foi possível calcular as projeções de compra.",
                    )
                }
        }
    }
}

enum class PurchaseMode { Supplier, Stock, Category }

// ---------- Hub e seleções ----------

@Composable
fun PurchasesHubScreen(
    onBack: () -> Unit,
    onSupplier: () -> Unit,
    onStock: () -> Unit,
    onCategory: () -> Unit,
) {
    PurchaseScaffold(title = "Compras", onBack = onBack) {
        item {
            Header(
                eyebrow = "SIMULAÇÃO TEMPORÁRIA",
                title = "Preparar compras",
                text = "A lista de Compras não altera o estoque e não cria pedido salvo no V1.",
            )
        }
        item {
            HubCard(
                NevesIcons.Suppliers, "INTELIGÊNCIA + HISTÓRICO", "Por fornecedor",
                "Escolha um fornecedor. Quando houver histórico e configuração suficientes, os Produtos recomendados aparecem primeiro com quantidade sugerida.",
                onSupplier,
            )
        }
        item {
            HubCard(
                NevesIcons.Inventory, "NECESSIDADE DE COMPRA", "Por estoque",
                "Veja primeiro os Produtos com recomendação automática e, quando quiser, abra também o restante do estoque para inclusão manual.",
                onStock,
            )
        }
        item {
            HubCard(
                NevesIcons.Products, "CATEGORIA + SUGESTÃO", "Por categoria",
                "Preserve a ordem manual da categoria e veja sugestão e risco quando houver histórico e configuração suficientes.",
                onCategory,
            )
        }
        item {
            WarningBox(
                "A recomendação automática só é gerada com histórico confiável suficiente: pelo menos 28 dias e " +
                    "3 intervalos válidos entre Conferências, além da configuração necessária do fornecedor. Quando isso " +
                    "não existir, a quantidade continua podendo ser informada manualmente."
            )
        }
    }
}

@Composable
fun PurchaseSupplierSelectRoute(onBack: () -> Unit, onSelect: (String) -> Unit, vm: PurchasesViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val suppliers = state.data?.suppliers.orEmpty().filter {
        matchesPurchaseSearch(listOf(it.name, it.company, it.phone), search)
    }

    PurchaseScaffold(title = "Compras · Por fornecedor", onBack = onBack) {
        item {
            Header(
                eyebrow = "POR FORNECEDOR",
                title = "Escolha o fornecedor",
                text = "A relação Produto–Fornecedor nasce das Entradas reais. Escolher um fornecedor aqui não altera nenhum cadastro.",
            )
        }
        item { SearchField(search, { search = it }, "Fornecedor, empresa ou telefone") }
        loadingAndError(state, vm::refresh)
        if (state.data != null && suppliers.isEmpty()) {
            item { InfoCard(if (search.isBlank()) "Nenhum fornecedor ativo cadastrado." else "Nenhum fornecedor encontrado.") }
        }
        items(suppliers, key = { it.id }) { supplier ->
            Card(onClick = { onSelect(supplier.id) }) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(supplier.name, fontWeight = FontWeight.Bold)
                    val detail = listOfNotNull(supplier.company, supplier.phone).joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!supplier.configurationReady) {
                        Text(
                            "Configuração de compra incompleta",
                            color = AMBER_TEXT,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseCategorySelectRoute(onBack: () -> Unit, onSelect: (String) -> Unit, vm: PurchasesViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val counts = state.data?.products.orEmpty().groupingBy { it.categoryId }.eachCount()

    PurchaseScaffold(title = "Compras · Por categoria", onBack = onBack) {
        item { Header(eyebrow = "POR CATEGORIA", title = "Escolha a categoria", text = null) }
        loadingAndError(state, vm::refresh)
        val categories = state.data?.categories.orEmpty()
        if (state.data != null && categories.isEmpty()) {
            item { InfoCard("Nenhuma categoria ativa cadastrada.") }
        }
        items(categories, key = { it.id }) { category ->
            Card(onClick = { onSelect(category.id) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(category.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val count = counts[category.id] ?: 0
                    Text(
                        "$count ${if (count == 1) "Produto" else "Produtos"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

// ---------- Lista de compras ----------

@Composable
fun PurchaseListRoute(
    mode: PurchaseMode,
    targetId: String?,
    onBack: () -> Unit,
    vm: PurchasesViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val data = state.data
    val context = LocalContext.current

    var selected by remember { mutableStateOf(setOf<String>()) }
    var quantities by remember { mutableStateOf(mapOf<String, String>()) }
    var invalid by remember { mutableStateOf(setOf<String>()) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var showRest by rememberSaveable { mutableStateOf(false) }
    var manualIds by remember { mutableStateOf(listOf<String>()) }
    var addOpen by remember { mutableStateOf(false) }

    val dirty = selected.isNotEmpty() || quantities.values.any { it.isNotBlank() }
    fun requestExit() {
        if (dirty) confirmExit = true else onBack()
    }
    BackHandler(enabled = dirty) { confirmExit = true }

    val supplier = if (mode == PurchaseMode.Supplier) data?.suppliers?.firstOrNull { it.id == targetId } else null
    val category = if (mode == PurchaseMode.Category) data?.categories?.firstOrNull { it.id == targetId } else null

    val supplierProducts = remember(data, targetId, mode) {
        if (mode == PurchaseMode.Supplier && data != null && targetId != null) {
            supplierPurchaseProducts(data.products, targetId)
        } else {
            emptyList()
        }
    }
    val stockGroups = remember(data, mode) {
        if (mode == PurchaseMode.Stock && data != null) stockPurchaseGroups(data.products) else emptyList<PurchaseListItem>() to emptyList<PurchaseListItem>()
    }

    // Recalcula só quando os dados ou a composição da lista mudam (não a cada tecla
    // digitada nas quantidades).
    val items: List<PurchaseListItem> = remember(mode, data, targetId, showRest, manualIds, supplierProducts, stockGroups) {
        when (mode) {
            PurchaseMode.Supplier -> {
                val manual = data?.products.orEmpty()
                    .filter { it.productId in manualIds }
                    .map { PurchaseListItem(it.productId, it.productName, it.unit, it.currentQuantity, null, isManualAddition = true) }
                sortSupplierItems(supplierProducts.map { it.toListItem() } + manual)
            }
            PurchaseMode.Stock -> if (showRest) stockGroups.first + stockGroups.second else stockGroups.first
            PurchaseMode.Category -> if (data != null && targetId != null) categoryPurchaseItems(data.products, targetId) else emptyList()
        }
    }

    val title = when (mode) {
        PurchaseMode.Supplier -> "Compras · Por fornecedor"
        PurchaseMode.Stock -> "Compras · Por estoque"
        PurchaseMode.Category -> "Compras · Por categoria"
    }
    val orderTitle = when (mode) {
        PurchaseMode.Supplier -> "Lista de compras — ${supplier?.name.orEmpty()}"
        PurchaseMode.Stock -> "Lista de compras"
        PurchaseMode.Category -> "Lista de compras — ${category?.name.orEmpty()}"
    }

    fun select(productId: String) {
        selected = selected + productId
        val suggestion = purchaseSuggestionToFill(items.firstOrNull { it.productId == productId })
        if (suggestion != null && quantities[productId].isNullOrBlank()) {
            quantities = quantities + (productId to suggestion)
        }
        actionError = null
    }

    fun toggle(productId: String) {
        if (productId in selected) {
            selected = selected - productId
            invalid = invalid - productId
            actionError = null
        } else {
            select(productId)
        }
    }

    fun updateQuantity(productId: String, value: String) {
        quantities = quantities + (productId to value)
        invalid = invalid - productId
        actionError = null
    }

    fun orderText(): String? {
        notice = null
        return when (val result = buildPurchaseOrderText(orderTitle, items, selected, quantities)) {
            is PurchaseOrderResult.Ready -> result.text
            is PurchaseOrderResult.Invalid -> {
                invalid = result.invalidProductIds
                actionError = result.message
                null
            }
        }
    }

    PurchaseScaffold(title = title, onBack = ::requestExit, onRefresh = vm::refresh) {
        loadingAndError(state, vm::refresh)

        if (data != null) {
            when (mode) {
                PurchaseMode.Supplier -> {
                    if (supplier == null) {
                        item { InfoCard("Fornecedor não encontrado ou excluído.") }
                    } else {
                        item { Header(eyebrow = "POR FORNECEDOR", title = supplier.name, text = null) }
                        if (!supplier.configurationReady) {
                            item {
                                WarningBox(
                                    "Complete frequência de compra, prazo de entrega e margem de segurança no cadastro " +
                                        "deste fornecedor para liberar recomendações automáticas."
                                )
                            }
                        }
                        item {
                            OutlinedButton(onClick = { addOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Adicionar outro produto")
                            }
                        }
                    }
                }
                PurchaseMode.Stock -> {
                    item { Header(eyebrow = "POR ESTOQUE", title = "Necessidade de compra", text = null) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val count = stockGroups.first.size
                            Text(
                                "$count ${if (count == 1) "produto recomendado" else "produtos recomendados"}",
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (stockGroups.second.isNotEmpty()) {
                                TextButton(onClick = { showRest = !showRest }) {
                                    Text(if (showRest) "Ocultar restante" else "Ver restante do estoque")
                                }
                            }
                        }
                    }
                    if (stockGroups.first.isEmpty()) {
                        item {
                            WarningBox(
                                "Produtos sem histórico mínimo, sem fornecedor/configuração ou com estoque suficiente não são " +
                                    "tratados como necessidade automática. Use “Ver restante do estoque” para incluí-los manualmente."
                            )
                        }
                    }
                }
                PurchaseMode.Category -> {
                    if (category == null) {
                        item { InfoCard("Categoria não encontrada ou excluída.") }
                    } else {
                        item { Header(eyebrow = "POR CATEGORIA", title = category.name, text = null) }
                    }
                }
            }

            item {
                Text(
                    when (mode) {
                        PurchaseMode.Supplier -> "Produtos com histórico deste fornecedor. Recomendados aparecem primeiro."
                        PurchaseMode.Stock -> "A sugestão usa consumo ponderado, estoque atual, ciclo de compra, prazo de entrega e margem de segurança. Você continua podendo alterar qualquer quantidade."
                        PurchaseMode.Category -> "A ordem manual da categoria é preservada. Quando houver histórico e configuração suficientes, a quantidade sugerida aparece automaticamente."
                    } + " Todos começam desmarcados; ao marcar uma recomendação, a quantidade sugerida é preenchida e continua editável.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (items.isEmpty() && (mode != PurchaseMode.Supplier || supplier != null) && (mode != PurchaseMode.Category || category != null)) {
                item {
                    InfoCard(
                        when (mode) {
                            PurchaseMode.Supplier -> "Nenhum Produto com histórico deste fornecedor. Use “Adicionar outro produto”."
                            PurchaseMode.Stock -> "Nenhum Produto possui recomendação automática de compra agora."
                            PurchaseMode.Category -> "Esta categoria não possui Produtos ativos."
                        }
                    )
                }
            }

            items(items, key = { it.productId }) { item ->
                PurchaseItemCard(
                    item = item,
                    checked = item.productId in selected,
                    quantity = quantities[item.productId].orEmpty(),
                    invalid = item.productId in invalid,
                    onToggle = { toggle(item.productId) },
                    onQuantity = { updateQuantity(item.productId, it) },
                    onStep = { direction -> updateQuantity(item.productId, stepPurchaseQuantity(quantities[item.productId].orEmpty(), direction)) },
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${selected.size} ${if (selected.size == 1) "item selecionado" else "itens selecionados"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                orderText()?.let { text ->
                                    notice = if (copyPurchaseText(context, orderTitle, text)) {
                                        "Texto copiado."
                                    } else {
                                        actionError = "Não foi possível copiar o texto."
                                        null
                                    }
                                }
                            },
                            enabled = items.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Copiar texto") }
                        OutlinedButton(
                            onClick = {
                                orderText()?.let { text ->
                                    if (sharePurchaseText(context, orderTitle, text)) {
                                        notice = null
                                    } else if (copyPurchaseText(context, orderTitle, text)) {
                                        notice = "Compartilhamento indisponível · texto copiado."
                                    } else {
                                        actionError = "Não foi possível compartilhar nem copiar o texto."
                                    }
                                }
                            },
                            enabled = items.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Compartilhar texto") }
                    }
                    Text(
                        "Nada é salvo: o estoque não muda e nenhuma relação Produto–Fornecedor é criada.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (addOpen && data != null && targetId != null) {
        val historical = supplierProducts.map { it.productId }.toSet()
        AddProductDialog(
            products = data.products.filter { it.productId !in historical && it.productId !in manualIds },
            onDismiss = { addOpen = false },
            onAdd = { productId ->
                manualIds = manualIds + productId
                selected = selected + productId
                addOpen = false
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Sair desta lista?") },
            text = { Text("Esta lista não será salva. Deseja sair mesmo assim?") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onBack() }) { Text("Sair mesmo assim") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Continuar na lista") } },
        )
    }
}

@Composable
private fun PurchaseItemCard(
    item: PurchaseListItem,
    checked: Boolean,
    quantity: String,
    invalid: Boolean,
    onToggle: () -> Unit,
    onQuantity: (String) -> Unit,
    onStep: (Int) -> Unit,
) {
    val recommended = item.projection?.status == PurchaseProjectionStatus.Recommended
    Card(
        colors = if (checked) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { onToggle() })
                Column(Modifier.weight(1f)) {
                    Text(item.productName, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                        if (recommended) Badge("Recomendado", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                        if (item.isManualAddition) Badge("Nesta simulação", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        item.projection?.risk?.let { risk ->
                            purchaseRiskLabel(risk)?.let { label ->
                                val (bg, fg) = when (risk) {
                                    PurchaseRisk.Risk -> RED_BG to RED_TEXT
                                    PurchaseRisk.Vulnerable -> AMBER_BG to AMBER_TEXT
                                    else -> GREEN_BG to GREEN_TEXT
                                }
                                Badge(label, bg, fg)
                            }
                        }
                    }
                }
            }
            val message = purchaseProjectionMessage(item)
            if (message.isNotBlank()) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("ESTOQUE ATUAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPurchaseStock(item.currentQuantity, item.unit), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = { onStep(-1) }, enabled = checked, modifier = Modifier.width(48.dp)) { NevesIcon(NevesIcons.Remove, "Diminuir quantidade") }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = onQuantity,
                    enabled = checked,
                    singleLine = true,
                    isError = invalid,
                    placeholder = { Text(if (checked) "Qtd." else "—") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(96.dp).padding(horizontal = 6.dp),
                )
                OutlinedButton(onClick = { onStep(1) }, enabled = checked, modifier = Modifier.width(48.dp)) { NevesIcon(NevesIcons.Add, "Aumentar quantidade") }
            }
            if (invalid) {
                Text(
                    "Informe uma quantidade maior que zero.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AddProductDialog(products: List<PurchaseProduct>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val available = products
        .filter { matchesPurchaseSearch(listOf(it.productName), search) }
        .sortedWith { a, b -> comparePurchaseNames(a.productName, b.productName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar produto à simulação") },
        text = {
            Column {
                Text(
                    "Pesquise ou role a lista. Esta inclusão vale somente para a simulação atual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SearchField(search, { search = it }, "Digite qualquer trecho do nome")
                Text(
                    "${available.size} ${if (available.size == 1) "Produto disponível" else "Produtos disponíveis"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 6.dp)) {
                    items(available, key = { it.productId }) { product ->
                        TextButton(onClick = { onAdd(product.productId) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(product.productName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${product.unit} · Estoque atual: ${formatPurchaseStock(product.currentQuantity, product.unit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

// ---------- Copiar / compartilhar ----------

internal fun copyPurchaseText(context: Context, title: String, text: String): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(title, text))
}.isSuccess

internal fun sharePurchaseText(context: Context, title: String, text: String): Boolean = runCatching {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar lista de compras"))
}.isSuccess

// ---------- Componentes ----------

@Composable
private fun PurchaseScaffold(
    title: String,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (onRefresh != null) TextButton(onClick = onRefresh) { NevesIcon(NevesIcons.Refresh, "Atualizar") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            content()
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.loadingAndError(state: PurchasesUiState, onRetry: () -> Unit) {
    if (state.loading) item { InfoCard("Calculando projeções…") }
    state.errorMessage?.let { message ->
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Tentar novamente") }
                }
            }
        }
    }
}

@Composable
private fun Header(eyebrow: String, title: String, text: String?) {
    Column {
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        text?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun HubCard(icon: Int, eyebrow: String, title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            NevesIcon(icon, modifier = Modifier.padding(bottom = 10.dp), tint = MaterialTheme.colorScheme.primary)
            Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Pesquisar") },
                        leadingIcon = { NevesIcon(NevesIcons.Search) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = { if (value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("Limpar") } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun InfoCard(text: String) {
    Card {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(18.dp))
    }
}

@Composable
private fun WarningBox(text: String) {
    Surface(color = AMBER_BG, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = AMBER_TEXT, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = MaterialTheme.shapes.extraLarge) {
        Text(
            text,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private val AMBER_BG = NevesColors.WarningContainer
private val AMBER_TEXT = NevesColors.OnWarning
private val GREEN_BG = NevesColors.SuccessContainer
private val GREEN_TEXT = NevesColors.OnSuccess
private val RED_BG = NevesColors.ErrorContainer
private val RED_TEXT = NevesColors.OnError


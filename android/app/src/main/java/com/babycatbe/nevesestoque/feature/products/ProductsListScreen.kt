package com.babycatbe.nevesestoque.feature.products

import com.babycatbe.nevesestoque.ui.components.NevesContentCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import com.babycatbe.nevesestoque.ui.components.NevesRefreshIcon
import com.babycatbe.nevesestoque.ui.components.NevesActionLabel
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babycatbe.nevesestoque.ui.load.RefreshOnKeyChange
import com.babycatbe.nevesestoque.ui.motion.NevesExpandedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ProductsListRoute(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    onCreateProduct: () -> Unit,
    categoryFilter: String? = null,
    pendingOnly: Boolean = false,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
    productsViewModel: ProductsViewModel = viewModel(),
) {
    val state by productsViewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnKeyChange(refreshKey) { productsViewModel.refresh() }
    ProductsListScreen(
        state = state,
        categoryFilter = categoryFilter,
        pendingOnly = pendingOnly,
        onBack = onBack,
        onRefresh = productsViewModel::refresh,
        onSaveProductOrder = productsViewModel::saveProductOrder,
        onProductClick = onProductClick,
        onCreateProduct = onCreateProduct,
        noticeMessage = noticeMessage,
        onDismissNotice = onDismissNotice,
    )
}

@Composable
private fun ProductsListScreen(
    state: ProductsUiState,
    categoryFilter: String?,
    pendingOnly: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveProductOrder: suspend (List<ProductOrderChange>) -> Boolean,
    onProductClick: (String) -> Unit,
    onCreateProduct: () -> Unit,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("alphabetical") }
    var reordering by rememberSaveable { mutableStateOf(false) }
    var reorderSaving by remember { mutableStateOf(false) }
    var originalOrder by remember { mutableStateOf<List<ProductOrderGroup>>(emptyList()) }
    var draftOrder by remember { mutableStateOf<List<ProductOrderGroup>>(emptyList()) }
    var reorderNotice by remember { mutableStateOf<String?>(null) }
    var reorderError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val data = state.data
    val officialOrder = remember(data) {
        buildProductOrderGroups(data?.categories.orEmpty(), data?.products.orEmpty())
    }
    val canReorder = categoryFilter == null && !pendingOnly && officialOrder.any { it.items.size >= 2 }
    val filtered = remember(data, search, categoryFilter, pendingOnly) {
        filterAndSortProducts(data?.products.orEmpty(), search, categoryFilter)
            .filter { !pendingOnly || it.categoryId == null }
    }
    val groups = remember(data, search) {
        buildProductGroups(data?.categories.orEmpty(), data?.products.orEmpty(), search)
    }
    val categoryName = if (pendingOnly) {
        "Produtos pendentes"
    } else {
        data?.categories?.firstOrNull { it.id == categoryFilter }?.name
    }

    fun cancelReordering() {
        if (reorderSaving) return
        reordering = false
        originalOrder = emptyList()
        draftOrder = emptyList()
        reorderError = null
    }

    fun beginReordering() {
        if (!canReorder) return
        search = ""
        mode = "category"
        reorderNotice = null
        reorderError = null
        originalOrder = officialOrder
        draftOrder = officialOrder
        reordering = true
    }

    fun saveReordering() {
        if (reorderSaving) return

        val changes = changedProductOrders(originalOrder, draftOrder)
        if (changes.isEmpty()) {
            reordering = false
            originalOrder = emptyList()
            draftOrder = emptyList()
            reorderNotice = "A ordem dos Produtos não foi alterada."
            reorderError = null
            return
        }

        reorderSaving = true
        reorderNotice = null
        reorderError = null
        scope.launch {
            val saved = onSaveProductOrder(changes)
            reorderSaving = false
            reordering = false
            originalOrder = emptyList()
            draftOrder = emptyList()

            if (saved) {
                reorderNotice = "Ordem dos Produtos atualizada com sucesso."
            } else {
                reorderError =
                    "Não foi possível salvar a nova ordem. A ordem oficial do backend foi recarregada."
            }
        }
    }

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(
                        onClick = { if (reordering) cancelReordering() else onBack() },
                        enabled = !reorderSaving,
                    ) {
                        Text(if (reordering) "Cancelar" else "Voltar")
                    }
                    Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        Text(
                            if (reordering) "Reordenar Produtos" else categoryName ?: "Produtos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!reordering && categoryName != null) {
                            Text(
                                if (pendingOnly) "Sem Categoria — cadastro a concluir" else "Produtos da Categoria",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (reordering) {
                        TextButton(onClick = { saveReordering() }, enabled = !reorderSaving) {
                            Text(if (reorderSaving) "Salvando…" else "Salvar")
                        }
                    } else {
                        TextButton(onClick = onCreateProduct) { Text("Novo") }
                        TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                            NevesRefreshIcon(state.refreshing)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            noticeMessage?.let { message ->
                item {
                    NevesContentCard {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(top = 8.dp),
                            )
                            TextButton(onClick = onDismissNotice) { Text("Fechar") }
                        }
                    }
                }
            }

            reorderNotice?.let { message ->
                item {
                    NevesContentCard {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(top = 8.dp),
                            )
                            TextButton(onClick = { reorderNotice = null }) { Text("Fechar") }
                        }
                    }
                }
            }

            reorderError?.let { message ->
                item {
                    NevesContentCard {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }

            if (!reordering) {
                item {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Pesquisar") },
                        leadingIcon = { NevesIcon(NevesIcons.Search) },
                        placeholder = { Text("Digite qualquer trecho do nome") },
                        trailingIcon = {
                            if (search.isNotEmpty()) {
                                TextButton(onClick = { search = "" }) { Text("Limpar") }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                if (categoryFilter == null && !pendingOnly) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = mode == "alphabetical",
                                    onClick = { mode = "alphabetical" },
                                    label = { Text("A–Z") },
                                )
                                FilterChip(
                                    selected = mode == "category",
                                    onClick = { mode = "category" },
                                    label = { Text("Categoria") },
                                )
                            }
                            TextButton(
                                onClick = { beginReordering() },
                                enabled = canReorder && !state.refreshing,
                            ) {
                                Text("Reordenar Produtos")
                            }
                        }
                    }
                }
            } else {
                item {
                    NevesContentCard {
                        Text(
                            "Use Subir/Descer somente dentro da própria Categoria. " +
                                "A nova ordem só vira oficial depois de Salvar.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }

            if (state.loading) {
                item { StatusCard("Carregando Produtos…") }
            }

            state.errorMessage?.let { error ->
                item {
                    NevesContentCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                        }
                    }
                }
            }

            if (!state.loading && state.errorMessage == null && data != null) {
                if (reordering) {
                    if (draftOrder.isEmpty()) {
                        item { StatusCard("Não há Produtos categorizados para reordenar.") }
                    } else {
                        items(draftOrder, key = { "reorder-${it.categoryId}" }) { group ->
                            ProductOrderGroupCard(
                                group = group,
                                saving = reorderSaving,
                                onMove = { productId, direction ->
                                    draftOrder = moveProductOrder(
                                        groups = draftOrder,
                                        categoryId = group.categoryId,
                                        productId = productId,
                                        direction = direction,
                                    )
                                },
                            )
                        }
                    }
                } else if (categoryFilter != null || pendingOnly || mode == "alphabetical") {
                    if (filtered.isEmpty()) {
                        item {
                            StatusCard(
                                when {
                                    pendingOnly && search.isBlank() -> "Nenhum Produto pendente."
                                    search.isBlank() -> "Nenhum Produto para exibir."
                                    else -> "Nenhum Produto encontrado."
                                }
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { product ->
                            ProductCard(product, data.categories, onProductClick)
                        }
                    }
                } else {
                    if (groups.isEmpty()) {
                        item {
                            StatusCard(
                                if (search.isBlank()) "Nenhum Produto para exibir."
                                else "Nenhum Produto encontrado."
                            )
                        }
                    } else {
                        items(groups, key = { it.id }) { group ->
                            ProductGroupCard(
                                group,
                                data.categories,
                                search.isNotBlank(),
                                onProductClick,
                            )
                        }
                    }
                }
            }

            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp)) }
        }
    }
}

@Composable
private fun ProductOrderGroupCard(
    group: ProductOrderGroup,
    saving: Boolean,
    onMove: (String, Int) -> Unit,
) {
    NevesContentCard {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(group.categoryName, fontWeight = FontWeight.Bold)
                Text(
                    if (group.items.size == 1) "1 Produto" else "${group.items.size} Produtos",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            group.items.forEachIndexed { index, product ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Posição ${index + 1}", style = MaterialTheme.typography.labelSmall)
                    Text(product.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        product.unit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        TextButton(
                            onClick = { onMove(product.id, -1) },
                            enabled = index > 0 && !saving,
                        ) {
                            NevesActionLabel("Subir", NevesIcons.Up)
                        }
                        TextButton(
                            onClick = { onMove(product.id, 1) },
                            enabled = index < group.items.lastIndex && !saving,
                        ) {
                            NevesActionLabel("Descer", NevesIcons.Down)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductGroupCard(
    group: ProductGroup,
    categories: List<ProductCategoryRow>,
    forceOpen: Boolean,
    onProductClick: (String) -> Unit,
) {
    // Saveable: o grupo aberto não fecha ao sair da área visível da lista nem ao voltar de um Produto.
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    val open = forceOpen || expanded

    NevesContentCard {
        Column {
            Column(
                modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (open) "Expandido" else "Recolhido" }.clickable(role = Role.Button) { expanded = !expanded }.padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    NevesIcon(if (open) NevesIcons.Collapse else NevesIcons.Expand)
                }
                Text(
                    "${group.items.size} produto(s)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            NevesExpandedContent(open) {
                Column {
                    group.items.forEach { ProductRowCard(it, categories, onProductClick) }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductListItem,
    categories: List<ProductCategoryRow>,
    onProductClick: (String) -> Unit,
) {
    NevesContentCard(onClick = { onProductClick(product.id) }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            ProductSummary(product, categories)
        }
    }
}

@Composable
private fun ProductRowCard(
    product: ProductListItem,
    categories: List<ProductCategoryRow>,
    onProductClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onProductClick(product.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ProductSummary(product, categories)
    }
}

@Composable
private fun ProductSummary(product: ProductListItem, categories: List<ProductCategoryRow>) {
    val category = categories.firstOrNull { it.id == product.categoryId }?.name ?: "Cadastro pendente"
    Text(product.name, fontWeight = FontWeight.Bold)
    Text(
        "$category · ${product.unit}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 2.dp),
    )
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        MetricSmall(
            "Estoque",
            if (product.stockRequiresConference) "Conferência necessária"
            else formatQuantity(product.currentQuantity, product.unit),
        )
        MetricSmall("Preço", product.currentPrice?.let(::formatMoney) ?: "Sem preço")
    }
}

@Composable
private fun MetricSmall(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusCard(text: String) {
    NevesContentCard {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(18.dp),
        )
    }
}

private fun formatQuantity(value: Double?, unit: String): String {
    if (value == null) return "Sem dados"
    val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply {
        maximumFractionDigits = 2
    }.format(value)
    return "$number $unit"
}

private fun formatMoney(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)


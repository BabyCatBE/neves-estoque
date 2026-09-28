package com.babycatbe.nevesestoque.feature.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductsListRoute(
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    onCreateProduct: () -> Unit,
    categoryFilter: String? = null,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
    productsViewModel: ProductsViewModel = viewModel(),
) {
    val state by productsViewModel.uiState.collectAsState()
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0L) productsViewModel.refresh()
    }
    ProductsListScreen(
        state = state,
        categoryFilter = categoryFilter,
        onBack = onBack,
        onRefresh = productsViewModel::refresh,
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
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onProductClick: (String) -> Unit,
    onCreateProduct: () -> Unit,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("alphabetical") }
    val data = state.data
    val filtered = remember(data, search, categoryFilter) {
        filterAndSortProducts(data?.products.orEmpty(), search, categoryFilter)
    }
    val groups = remember(data, search) {
        buildProductGroups(data?.categories.orEmpty(), data?.products.orEmpty(), search)
    }
    val categoryName = data?.categories?.firstOrNull { it.id == categoryFilter }?.name

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                    Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                        Text(
                            categoryName ?: "Produtos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (categoryName != null) {
                            Text(
                                "Produtos da Categoria",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    TextButton(onClick = onCreateProduct) { Text("Novo") }
                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                        Text(if (state.refreshing) "Atualizando…" else "Atualizar")
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
                    Card {
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
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Pesquisar") },
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

            if (categoryFilter == null) {
                item {
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
                }
            }

            if (state.loading) {
                item { StatusCard("Carregando Produtos…") }
            }

            state.errorMessage?.let { error ->
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                        }
                    }
                }
            }

            if (!state.loading && state.errorMessage == null && data != null) {
                if (categoryFilter != null || mode == "alphabetical") {
                    if (filtered.isEmpty()) {
                        item { StatusCard(if (search.isBlank()) "Nenhum Produto para exibir." else "Nenhum Produto encontrado.") }
                    } else {
                        items(filtered, key = { it.id }) { product ->
                            ProductCard(product, data.categories, onProductClick)
                        }
                    }
                } else {
                    if (groups.isEmpty()) {
                        item { StatusCard(if (search.isBlank()) "Nenhum Produto para exibir." else "Nenhum Produto encontrado.") }
                    } else {
                        items(groups, key = { it.id }) { group ->
                            ProductGroupCard(group, data.categories, search.isNotBlank(), onProductClick)
                        }
                    }
                }
            }

            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp)) }
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
    var expanded by remember(group.id) { mutableStateOf(false) }
    val open = forceOpen || expanded

    Card {
        Column {
            Column(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)
            ) {
                Text(group.name, fontWeight = FontWeight.Bold)
                Text(
                    "${group.items.size} produto(s)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            AnimatedVisibility(open) {
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
    Card(onClick = { onProductClick(product.id) }) {
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusCard(text: String) {
    Card { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(18.dp)) }
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

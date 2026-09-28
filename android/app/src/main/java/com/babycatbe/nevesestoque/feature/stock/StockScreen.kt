package com.babycatbe.nevesestoque.feature.stock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StockRoute(
    onBack: () -> Unit,
    stockViewModel: StockViewModel = viewModel(),
) {
    val state by stockViewModel.uiState.collectAsState()
    StockScreen(
        state = state,
        onBack = onBack,
        onRefresh = stockViewModel::refresh,
    )
}

@Composable
private fun StockScreen(
    state: StockUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var viewMode by rememberSaveable { mutableStateOf(StockViewMode.Category) }
    var showValues by rememberSaveable { mutableStateOf(false) }

    val data = state.data
    val categoryGroups = remember(data, search) {
        buildCategoryStockGroups(data?.categories.orEmpty(), data?.items.orEmpty(), search)
    }
    val supplierGroups = remember(data, search) {
        buildSupplierStockGroups(data?.suppliers.orEmpty(), data?.items.orEmpty(), search)
    }
    val alphabetical = remember(data, search) {
        buildAlphabeticalStockView(data?.items.orEmpty(), search)
    }
    val summary = remember(data) { calculateStockValueSummary(data?.items.orEmpty()) }
    val pendingConference = remember(data) {
        data?.items.orEmpty().count { it.stockRequiresConference }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                    Text(
                        text = "Estoque Atual",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                        Text(if (state.refreshing) "Atualizando…" else "Atualizar")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        "POSIÇÃO ATUAL",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Estoque atual",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Text(
                        "Calculado pela última Conferência física válida + Entradas posteriores.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showValues = !showValues }) {
                        Text(if (showValues) "Ocultar valores" else "Mostrar valores")
                    }
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text("Relatórios")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Pesquisar") },
                    placeholder = { Text("Buscar produto por nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            TextButton(onClick = { search = "" }) { Text("Limpar") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column {
                    Text(
                        "ORGANIZAR POR",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        FilterChip(
                            selected = viewMode == StockViewMode.Category,
                            onClick = { viewMode = StockViewMode.Category },
                            label = { Text("Categoria") },
                        )
                        FilterChip(
                            selected = viewMode == StockViewMode.Supplier,
                            onClick = { viewMode = StockViewMode.Supplier },
                            label = { Text("Fornecedor") },
                        )
                        FilterChip(
                            selected = viewMode == StockViewMode.Alphabetical,
                            onClick = { viewMode = StockViewMode.Alphabetical },
                            label = { Text("A–Z") },
                        )
                    }
                }
            }

            if (showValues && data != null) {
                item {
                    Card {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "VALOR TOTAL DO ESTOQUE",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                formatMoney(summary.totalKnown) + if (summary.hasMissingPrice) " *" else "",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            if (summary.hasMissingPrice) {
                                Text(
                                    "* Existem produtos com estoque positivo e sem preço informado.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            if (pendingConference > 0) {
                                Text(
                                    "$pendingConference produto(s) aguardando Conferência física após mescla não entram no valor conhecido.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (state.loading) {
                item {
                    Card {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Carregando estoque atual…")
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                        }
                    }
                }
            }

            if (!state.loading && state.errorMessage == null && data != null) {
                when (viewMode) {
                    StockViewMode.Category -> {
                        if (categoryGroups.isEmpty()) {
                            item { EmptyStock(search) }
                        } else {
                            items(categoryGroups, key = { it.id }) { group ->
                                StockGroupCard(group, showValues, search.isNotBlank())
                            }
                        }
                    }

                    StockViewMode.Supplier -> {
                        if (supplierGroups.isEmpty()) {
                            item { EmptyStock(search) }
                        } else {
                            items(supplierGroups, key = { it.id }) { group ->
                                StockGroupCard(group, showValues, search.isNotBlank())
                            }
                        }
                    }

                    StockViewMode.Alphabetical -> {
                        if (alphabetical.isEmpty()) {
                            item { EmptyStock(search) }
                        } else {
                            item {
                                Card {
                                    Column {
                                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                            Text("Ordem alfabética", fontWeight = FontWeight.Bold)
                                            Text(
                                                "${alphabetical.size} produto(s)",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        alphabetical.forEach { StockItemRow(it, showValues) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StockGroupCard(
    group: StockGroup,
    showValues: Boolean,
    forceOpen: Boolean,
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = forceOpen || expandedState[group.id] == true

    Card {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedState[group.id] = !expanded }
                    .padding(vertical = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(4.dp)
                        .background(
                            when (group.accent) {
                                StockGroupAccent.Primary -> MaterialTheme.colorScheme.primary
                                StockGroupAccent.Warning -> MaterialTheme.colorScheme.tertiary
                                StockGroupAccent.Neutral -> MaterialTheme.colorScheme.outline
                            }
                        )
                )
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(group.name, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            group.subtitle,
                            "${group.items.size} produto(s)",
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(if (expanded) "▲" else "▼", modifier = Modifier.padding(end = 16.dp))
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    group.items.forEach { StockItemRow(it, showValues) }
                }
            }
        }
    }
}

@Composable
private fun StockItemRow(item: CurrentStockRow, showValues: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.productName,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (!showValues) {
                Text(
                    if (item.stockRequiresConference) "Conferência necessária"
                    else formatQuantity(item.currentQuantity, item.unit),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (showValues) {
            Spacer(Modifier.height(8.dp))
            Metric("Quantidade", if (item.stockRequiresConference) "Conferência necessária" else formatQuantity(item.currentQuantity, item.unit))
            Metric("Preço unitário", item.currentPrice?.let(::formatMoney) ?: "Sem preço")
            Metric("Valor em estoque", formatItemValue(item))
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyStock(search: String) {
    Card {
        Text(
            if (search.isBlank()) "Ainda não há produtos para exibir no Estoque Atual."
            else "Nenhum produto encontrado para esta pesquisa.",
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

private fun formatItemValue(item: CurrentStockRow): String {
    if (item.stockRequiresConference) return "Conferência necessária"
    if (item.currentQuantity == null) return "Sem dados"
    if (item.currentQuantity == 0.0) return formatMoney(0.0)
    return item.currentValue?.let(::formatMoney) ?: "Sem preço"
}

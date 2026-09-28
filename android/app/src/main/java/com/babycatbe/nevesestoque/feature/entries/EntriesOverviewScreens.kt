package com.babycatbe.nevesestoque.feature.entries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

@Composable
fun EntriesHubScreen(
    onBack: () -> Unit,
    onNewEntry: () -> Unit,
    onHistory: () -> Unit,
    onTrash: () -> Unit = {},
) {
    Scaffold(topBar = { EntryTopBar("Entradas", onBack) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Text(
                "Entradas de mercadoria",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Entrada representa mercadoria realmente recebida. Pedidos futuros não devem ser registrados aqui.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(onClick = onNewEntry) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("OPERAÇÃO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    Text("Nova Entrada", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                    Text(
                        "Registre Fornecedor, data, Produtos recebidos, quantidades e preços conhecidos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Card(onClick = onHistory) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("CONSULTA", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text("Histórico de Entradas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                    Text(
                        "Consulte Entradas salvas e pesquise por Fornecedor ou Produto.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Card(onClick = onTrash) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("RECUPERAÇÃO", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text("Lixeira", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                    Text(
                        "Restaure ou exclua definitivamente Entradas excluídas nos últimos 7 dias.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun EntriesHistoryRoute(
    onBack: () -> Unit,
    onNewEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val vm: EntriesHistoryViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0L) vm.refresh()
    }
    EntriesHistoryScreen(
        state = state,
        onBack = onBack,
        onNewEntry = onNewEntry,
        onEntryClick = onEntryClick,
        onRefresh = vm::refresh,
        noticeMessage = noticeMessage,
        onDismissNotice = onDismissNotice,
    )
}

@Composable
private fun EntriesHistoryScreen(
    state: EntriesHistoryUiState,
    onBack: () -> Unit,
    onNewEntry: () -> Unit,
    onEntryClick: (String) -> Unit,
    onRefresh: () -> Unit,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(search, state.entries) {
        val term = normalizeEntrySearchText(search)
        state.entries.filter { entry ->
            term.isBlank() || listOf(entry.supplierName, *entry.productNames.toTypedArray())
                .any { normalizeEntrySearchText(it).contains(term) }
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                    Text(
                        "Histórico de Entradas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                        Text(if (state.refreshing) "Atualizando…" else "Atualizar")
                    }
                    TextButton(onClick = onNewEntry) { Text("Nova") }
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
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Histórico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Pesquise por Fornecedor ou Produto. O V1 não usa filtro por período.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Pesquisar") },
                        placeholder = { Text("Fornecedor ou Produto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
            if (state.loading) {
                item { Card { Text("Carregando Histórico…", modifier = Modifier.padding(18.dp)) } }
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
            if (!state.loading && state.errorMessage == null && filtered.isEmpty()) {
                item {
                    Card {
                        Text(
                            if (search.isBlank()) "Nenhuma Entrada registrada." else "Nenhuma Entrada encontrada.",
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                        )
                    }
                }
            }
            items(filtered, key = { it.id }) { entry ->
                Card(onClick = { onEntryClick(entry.id) }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("DATA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatEntryDateShort(entry.effectiveAt), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("TOTAL CONHECIDO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    formatEntryMoney(entry.totalKnown) + if (entry.hasMissingPrice) " *" else "",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        Text(entry.supplierName, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                        Text(
                            entry.productNames.joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        if (entry.hasMissingPrice) {
                            Text(
                                "Há item sem preço",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EntryDetailRoute(
    entryId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: (String) -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val vm: EntryDetailViewModel = viewModel(
        key = "entry-detail-${entryId}",
        factory = EntryDetailViewModel.Factory(entryId),
    )
    val state by vm.uiState.collectAsState()
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0L) vm.refresh()
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            onDeleted("Entrada enviada para a Lixeira. Seus efeitos deixaram de compor o estoque e o preço atuais.")
        }
    }
    EntryDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = vm::deleteEntry,
        onRefresh = vm::refresh,
        noticeMessage = noticeMessage,
        onDismissNotice = onDismissNotice,
    )
}

@Composable
private fun EntryDetailScreen(
    state: EntryDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
) {
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    val entry = state.entry

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack, enabled = !state.deleting) { Text("Voltar") }
                    Text(
                        "Entrada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(onClick = onEdit, enabled = entry != null && !state.deleting) { Text("Editar") }
                    TextButton(onClick = onRefresh, enabled = !state.refreshing && !state.deleting) {
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
            if (state.loading) {
                item { Card { Text("Carregando Entrada…", modifier = Modifier.padding(18.dp)) } }
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
            state.actionError?.let { error ->
                item { Card { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            }
            if (!state.loading && state.errorMessage == null && entry != null) {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("ENTRADA SALVA", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(entry.supplierName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            (entry.supplierCompany ?: "Empresa não informada") + " · " + formatEntryDateLong(entry.effectiveAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EntryMetricCard("Fornecedor", entry.supplierName, Modifier.weight(1f))
                        EntryMetricCard("Data", formatEntryDateShort(entry.effectiveAt), Modifier.weight(1f))
                    }
                }
                item {
                    EntryMetricCard(
                        "Total conhecido",
                        formatEntryMoney(entry.totalKnown) + if (entry.hasMissingPrice) " *" else "",
                        Modifier.fillMaxWidth(),
                    )
                }
                if (entry.hasMissingPrice) {
                    item {
                        Card {
                            Text(
                                "* O total soma apenas itens com preço conhecido. Há pelo menos um item com preço não informado.",
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                entry.observation?.let { observation ->
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("OBSERVAÇÃO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(observation, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                item { Text("Itens recebidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(entry.items, key = { it.id }) { item ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(item.productName, fontWeight = FontWeight.Bold)
                            Text(item.unit, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            EntryInfoLine("Quantidade", formatEntryNumber(item.quantity))
                            EntryInfoLine(
                                "Unitário",
                                when {
                                    item.unitPrice == null -> "Preço não informado"
                                    item.unitPrice == 0.0 -> "Bonificação"
                                    else -> formatEntryMoney(item.unitPrice)
                                },
                            )
                            EntryInfoLine(
                                "Total",
                                item.unitPrice?.let { formatEntryMoney(item.quantity * it) } ?: "—",
                            )
                        }
                    }
                }
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Excluir Entrada", fontWeight = FontWeight.Bold)
                            Text(
                                "A Entrada sairá do Histórico ativo e seus efeitos deixarão de compor os cálculos atuais. A restauração continua disponível por 7 dias na Lixeira do sistema.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            TextButton(
                                onClick = { deleteOpen = true },
                                enabled = !state.deleting,
                                modifier = Modifier.padding(top = 6.dp),
                            ) { Text(if (state.deleting) "Excluindo…" else "Excluir Entrada") }
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = state.deleting) {}

    if (deleteOpen && entry != null) {
        AlertDialog(
            onDismissRequest = { if (!state.deleting) deleteOpen = false },
            title = { Text("Excluir Entrada?") },
            text = {
                Text(
                    "Excluir esta Entrada de “${entry.supplierName}”? Ela sairá imediatamente do Histórico ativo e poderá ser restaurada por 7 dias."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { deleteOpen = false; onDelete() },
                    enabled = !state.deleting,
                ) { Text("Excluir Entrada") }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }, enabled = !state.deleting) { Text("Cancelar") }
            },
        )
    }
}

@Composable
fun EntryTopBar(title: String, onBack: () -> Unit) {
    Surface(shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = onBack) { Text("Voltar") }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(top = 10.dp),
            )
        }
    }
}

@Composable
fun EntryMetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
fun EntryInfoLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

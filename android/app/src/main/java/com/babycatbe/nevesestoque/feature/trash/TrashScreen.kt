package com.babycatbe.nevesestoque.feature.trash

import androidx.compose.material3.Card
import com.babycatbe.nevesestoque.ui.components.NevesContentCard
import com.babycatbe.nevesestoque.ui.components.NevesRefreshIcon
import com.babycatbe.nevesestoque.ui.components.NevesActionLabel
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.babycatbe.nevesestoque.feature.products.normalizeProductSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val ALL_FILTER = "all"

/**
 * Lixeira Universal Android: Produtos, Categorias, Fornecedores, Entradas e Conferências.
 * Restaurar, Excluir definitivamente (digitando EXCLUIR) e Esvaziar lixeira (digitando ESVAZIAR),
 * com as mesmas regras e mensagens da Web.
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    initialFilter: String? = null,
    onDataChanged: () -> Unit = {},
) {
    val repository = remember { TrashRepository() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var trashItems by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<TrashItem?>(null) }
    var pendingPermanentDelete by remember { mutableStateOf<TrashItem?>(null) }
    var emptyTrashOpen by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable {
        mutableStateOf(TrashItemType.fromFilter(initialFilter)?.rpcValue ?: ALL_FILTER)
    }

    suspend fun load() {
        val hasData = trashItems.isNotEmpty()
        loading = !hasData
        refreshing = hasData
        loadError = null
        try {
            trashItems = repository.loadRestorableTrash()
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            loadError = "Não foi possível carregar a Lixeira."
        }
        loading = false
        refreshing = false
    }

    /** Executa a ação, recarrega a lista e informa o resultado. A lista é recarregada mesmo em erro. */
    fun runAction(action: suspend () -> String, onFinished: () -> Unit) {
        if (working) return
        working = true
        notice = null
        actionError = null
        scope.launch {
            try {
                val message = action()
                onFinished()
                notice = message
                onDataChanged()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                actionError = trashErrorMessage(failure)
            }
            try {
                trashItems = repository.loadRestorableTrash()
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                loadError = "Não foi possível atualizar a Lixeira."
            }
            working = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val selectedType = TrashItemType.fromFilter(filter)
    val visibleItems = remember(trashItems, search, filter) {
        filterTrashItems(trashItems, selectedType, search, ::normalizeProductSearch)
    }

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack, enabled = !working) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        "Lixeira",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(
                        onClick = { scope.launch { load() } },
                        enabled = !loading && !refreshing && !working,
                    ) {
                        NevesRefreshIcon(refreshing)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            item {
                Column(Modifier.padding(top = 10.dp)) {
                    Text("Itens excluídos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Produtos, Categorias, Fornecedores, Entradas e Conferências permanecem restauráveis " +
                            "por 7 dias. Depois desse prazo, deixam de aparecer aqui.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Text(
                        "Restaurar recoloca o item na área ativa. Excluir definitivamente ou Esvaziar lixeira " +
                            "torna o item irrecuperável no aplicativo. A identidade histórica necessária pode " +
                            "permanecer internamente para não quebrar Entradas, Conferências, preços e relatórios antigos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            if (trashItems.isNotEmpty()) {
                item {
                    Button(
                        onClick = {
                            notice = null
                            actionError = null
                            emptyTrashOpen = true
                        },
                        enabled = !working,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Esvaziar lixeira (${trashItems.size})")
                    }
                }
            }

            notice?.let { message ->
                item {
                    NevesContentCard {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(top = 8.dp),
                            )
                            TextButton(onClick = { notice = null }) { Text("Fechar") }
                        }
                    }
                }
            }

            actionError?.let { message ->
                item {
                    NevesContentCard {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f).padding(top = 8.dp),
                            )
                            TextButton(onClick = { actionError = null }) { Text("Fechar") }
                        }
                    }
                }
            }

            loadError?.let { message ->
                item {
                    NevesContentCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { scope.launch { load() } }, enabled = !working) {
                                Text("Tentar novamente")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Pesquisar") },
                        leadingIcon = { NevesIcon(NevesIcons.Search) },
                    placeholder = { Text("Nome, detalhe ou tipo") },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            TextButton(onClick = { search = "" }) { Text("Limpar") }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter == ALL_FILTER,
                            onClick = { filter = ALL_FILTER },
                            label = { Text("Todos") },
                        )
                    }
                    items(TrashItemType.entries.toList(), key = { it.rpcValue }) { type ->
                        FilterChip(
                            selected = filter == type.rpcValue,
                            onClick = { filter = type.rpcValue },
                            label = { Text(type.filterLabel) },
                        )
                    }
                }
            }

            if (loading) {
                item { NevesContentCard { Text("Carregando Lixeira…", modifier = Modifier.padding(18.dp)) } }
            }

            if (!loading && loadError == null && visibleItems.isEmpty()) {
                item {
                    NevesContentCard {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text(
                                if (search.isBlank()) "Nenhum item neste filtro" else "Nenhum item encontrado",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (search.isBlank()) "Itens excluídos dentro da janela de 7 dias aparecerão aqui."
                                else "Tente outro termo ou altere o filtro.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            if (!loading) {
                items(visibleItems, key = { "${it.type.rpcValue}-${it.id}" }) { item ->
                    NevesContentCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                item.type.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                            item.detail?.let {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Text(
                                "Restaurável até ${formatTrashDateTime(item.restoreUntil)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        notice = null
                                        actionError = null
                                        pendingRestore = item
                                    },
                                    enabled = !working,
                                ) { NevesActionLabel("Restaurar", NevesIcons.Restore) }
                                Button(
                                    onClick = {
                                        notice = null
                                        actionError = null
                                        pendingPermanentDelete = item
                                    },
                                    enabled = !working,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                ) { NevesActionLabel("Excluir definitivamente", NevesIcons.DeleteForever) }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.padding(bottom = 8.dp)) }
        }
    }

    pendingRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!working) pendingRestore = null },
            title = { Text("Restaurar ${item.type.label.lowercase()}?") },
            text = { Text(trashRestoreDescription(item)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        runAction(
                            action = {
                                repository.restore(item)
                                trashRestoreSuccessMessage(item)
                            },
                            onFinished = { pendingRestore = null },
                        )
                    },
                    enabled = !working,
                ) { Text(if (working) "Restaurando…" else "Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }, enabled = !working) { Text("Cancelar") }
            },
        )
    }

    pendingPermanentDelete?.let { item ->
        TrashTypedConfirmDialog(
            title = "Excluir definitivamente ${item.type.label.lowercase()}?",
            description = trashPermanentDeleteDescription(item),
            phrase = PERMANENT_DELETE_PHRASE,
            confirmLabel = "Excluir definitivamente",
            pendingLabel = "Excluindo…",
            pending = working,
            onCancel = { pendingPermanentDelete = null },
            onConfirm = {
                runAction(
                    action = {
                        repository.permanentlyDelete(item)
                        trashPermanentDeleteSuccessMessage(item)
                    },
                    onFinished = { pendingPermanentDelete = null },
                )
            },
        )
    }

    if (emptyTrashOpen) {
        TrashTypedConfirmDialog(
            title = "Esvaziar lixeira?",
            description = trashEmptyDescription(trashItems.size),
            phrase = EMPTY_TRASH_PHRASE,
            confirmLabel = "Esvaziar lixeira",
            pendingLabel = "Esvaziando…",
            pending = working,
            onCancel = { emptyTrashOpen = false },
            onConfirm = {
                runAction(
                    action = { emptyTrashSuccessMessage(repository.emptyTrash()) },
                    onFinished = { emptyTrashOpen = false },
                )
            },
        )
    }
}

/** Confirmação reforçada: o botão destrutivo só habilita após digitar a palavra exigida. */
@Composable
private fun TrashTypedConfirmDialog(
    title: String,
    description: String,
    phrase: String,
    confirmLabel: String,
    pendingLabel: String,
    pending: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var typed by rememberSaveable(title) { mutableStateOf("") }
    val valid = isTrashConfirmationValid(typed, phrase)

    AlertDialog(
        onDismissRequest = { if (!pending) onCancel() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description)
                Text("Para confirmar, digite $phrase.", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    enabled = !pending,
                    label = { Text("Confirmação") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = valid && !pending) {
                Text(
                    if (pending) pendingLabel else confirmLabel,
                    color = if (valid && !pending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !pending) { Text("Cancelar") }
        },
    )
}


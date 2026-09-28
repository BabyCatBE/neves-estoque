package com.babycatbe.nevesestoque.feature.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CatalogTrashScreen(onBack: () -> Unit) {
    val repository = remember { ProductsRepository() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var trashItems by remember { mutableStateOf<List<CatalogTrashItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<CatalogTrashItem?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }

    suspend fun load() {
        val hasData = trashItems.isNotEmpty()
        loading = !hasData
        refreshing = hasData
        error = null
        try {
            trashItems = repository.loadCatalogTrash()
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = "Não foi possível carregar a Lixeira."
        }
        loading = false
        refreshing = false
    }

    fun restore() {
        val item = pendingRestore ?: return
        if (restoring) return
        restoring = true
        notice = null
        error = null

        scope.launch {
            try {
                repository.restoreCatalogTrashItem(item)
                trashItems = repository.loadCatalogTrash()
                pendingRestore = null
                notice = "${trashTypeLabel(item.type)} “${item.name}” restaurado com sucesso."
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                error = catalogTrashErrorMessage(failure)
            } finally {
                restoring = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val visibleItems = remember(trashItems, search, filter) {
        val term = normalizeProductSearch(search)
        trashItems.filter { item ->
            val typeMatches = filter == "all" ||
                (filter == "product" && item.type == CatalogTrashType.Product) ||
                (filter == "category" && item.type == CatalogTrashType.Category)
            val searchMatches = term.isBlank() ||
                normalizeProductSearch(
                    listOfNotNull(item.name, item.detail, trashTypeLabel(item.type)).joinToString(" ")
                ).contains(term)
            typeMatches && searchMatches
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                    Text(
                        "Lixeira",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(
                        onClick = { scope.launch { load() } },
                        enabled = !refreshing && !restoring,
                    ) {
                        Text(if (refreshing) "Atualizando…" else "Atualizar")
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
                Text(
                    "Produtos e Categorias excluídos permanecem restauráveis por 7 dias. " +
                        "Exclusão definitiva não está disponível neste bloco.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            notice?.let { message ->
                item {
                    Card {
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

            error?.let { message ->
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            if (!restoring) {
                                TextButton(onClick = { scope.launch { load() } }) {
                                    Text("Tentar novamente")
                                }
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
                    placeholder = { Text("Nome, unidade ou tipo") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("Todos") })
                    FilterChip(selected = filter == "product", onClick = { filter = "product" }, label = { Text("Produtos") })
                    FilterChip(selected = filter == "category", onClick = { filter = "category" }, label = { Text("Categorias") })
                }
            }

            if (loading) {
                item { Card { Text("Carregando Lixeira…", modifier = Modifier.padding(18.dp)) } }
            }

            if (!loading && error == null && visibleItems.isEmpty()) {
                item {
                    Card {
                        Text(
                            if (search.isBlank()) "Nenhum item restaurável neste filtro."
                            else "Nenhum item encontrado para esta pesquisa.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                        )
                    }
                }
            }

            if (!loading) {
                items(visibleItems, key = { "${it.type}-${it.id}" }) { item ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                trashTypeLabel(item.type),
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
                            TextButton(
                                onClick = {
                                    notice = null
                                    error = null
                                    pendingRestore = item
                                },
                                enabled = !restoring,
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                Text("Restaurar")
                            }
                        }
                    }
                }
            }

            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp)) }
        }
    }

    pendingRestore?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!restoring) pendingRestore = null },
            title = { Text("Restaurar ${trashTypeLabel(item.type)}?") },
            text = {
                Text(
                    when (item.type) {
                        CatalogTrashType.Product ->
                            "Restaurar o Produto “${item.name}”? Ele voltará para a posição manual anterior na Categoria."
                        CatalogTrashType.Category ->
                            "Restaurar a Categoria “${item.name}”? Ela voltará para a posição manual anterior."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { restore() }, enabled = !restoring) {
                    Text(if (restoring) "Restaurando…" else "Restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }, enabled = !restoring) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private fun trashTypeLabel(type: CatalogTrashType): String =
    when (type) {
        CatalogTrashType.Product -> "Produto"
        CatalogTrashType.Category -> "Categoria"
    }

private fun formatTrashDateTime(value: String): String =
    runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(value)

package com.babycatbe.nevesestoque.feature.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val repository = remember { ProductsRepository() }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<CategoryListItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<CategoryListItem?>(null) }
    var deletingCategory by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    var reorderSaving by remember { mutableStateOf(false) }
    var originalOrder by remember { mutableStateOf<List<CategoryListItem>>(emptyList()) }
    var draftOrder by remember { mutableStateOf<List<CategoryListItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        val hasData = categories.isNotEmpty()
        loading = !hasData
        refreshing = hasData
        error = null
        try {
            categories = orderedCategories(repository.loadCategories())
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = "Não foi possível carregar as Categorias."
        }
        loading = false
        refreshing = false
    }

    fun cancelReordering() {
        if (reorderSaving) return
        reordering = false
        originalOrder = emptyList()
        draftOrder = emptyList()
        actionError = null
    }

    fun requestDelete(category: CategoryListItem) {
        actionNotice = null
        actionError = null
        if (!canSoftDeleteCategory(category.productCount)) {
            actionError = "A Categoria só pode ser excluída quando estiver vazia."
            return
        }
        pendingDelete = category
    }

    fun confirmDelete() {
        val category = pendingDelete ?: return
        if (deletingCategory) return

        deletingCategory = true
        actionNotice = null
        actionError = null
        scope.launch {
            try {
                repository.softDeleteCategory(category.id)
                categories = orderedCategories(repository.loadCategories())
                pendingDelete = null
                actionNotice = "Categoria enviada para a Lixeira. Ela pode ser restaurada por 7 dias."
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                actionError = catalogTrashErrorMessage(failure)
            } finally {
                deletingCategory = false
            }
        }
    }

    fun beginReordering() {
        if (categories.size < 2) return
        actionNotice = null
        actionError = null
        originalOrder = orderedCategories(categories)
        draftOrder = originalOrder
        reordering = true
    }

    fun saveReordering() {
        if (reorderSaving) return

        if (!categoryOrderChanged(originalOrder, draftOrder)) {
            reordering = false
            originalOrder = emptyList()
            draftOrder = emptyList()
            actionNotice = "A ordem das Categorias não foi alterada."
            actionError = null
            return
        }

        reorderSaving = true
        actionNotice = null
        actionError = null
        scope.launch {
            var reloaded = true
            try {
                repository.reorderCategories(draftOrder.map { it.id })
                categories = orderedCategories(repository.loadCategories())
                actionNotice = "Ordem das Categorias atualizada com sucesso."
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure

                reloaded = try {
                    categories = orderedCategories(repository.loadCategories())
                    true
                } catch (reloadFailure: Throwable) {
                    if (reloadFailure is CancellationException) throw reloadFailure
                    false
                }

                actionError = if (reloaded) {
                    "Não foi possível salvar a nova ordem. A ordem oficial do backend foi recarregada."
                } else {
                    "Não foi possível salvar a nova ordem nem recarregar agora. Use Atualizar para buscar a ordem oficial."
                }
            } finally {
                reorderSaving = false
                reordering = false
                originalOrder = emptyList()
                draftOrder = emptyList()
            }
        }
    }

    LaunchedEffect(refreshKey) { load() }

    val visibleCategories = if (reordering) draftOrder else categories

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(
                        onClick = { if (reordering) cancelReordering() else onBack() },
                        enabled = !reorderSaving,
                    ) {
                        Text(if (reordering) "Cancelar" else "Voltar")
                    }
                    Text(
                        if (reordering) "Reordenar Categorias" else "Categorias",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    if (reordering) {
                        TextButton(onClick = { saveReordering() }, enabled = !reorderSaving) {
                            Text(if (reorderSaving) "Salvando…" else "Salvar")
                        }
                    } else {
                        TextButton(onClick = onCreateCategory) { Text("Nova") }
                        TextButton(
                            onClick = { scope.launch { load() } },
                            enabled = !loading && !refreshing,
                        ) {
                            Text(if (refreshing) "Atualizando…" else "Atualizar")
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

            actionNotice?.let { message ->
                item {
                    Card {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).padding(top = 8.dp),
                            )
                            TextButton(onClick = { actionNotice = null }) { Text("Fechar") }
                        }
                    }
                }
            }

            actionError?.let { message ->
                item {
                    Card {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        if (reordering) {
                            "Use Subir/Descer para montar o rascunho. A nova ordem só vira oficial ao Salvar."
                        } else {
                            "A ordem abaixo é a base usada no Estoque e nas Conferências."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!reordering) {
                        TextButton(
                            onClick = { beginReordering() },
                            enabled = categories.size >= 2 && !refreshing,
                        ) {
                            Text("Reordenar Categorias")
                        }
                    }
                }
            }

            if (loading) {
                item { Card { Text("Carregando Categorias…", modifier = Modifier.padding(18.dp)) } }
            }

            error?.let {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(it, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { scope.launch { load() } }) {
                                Text("Tentar novamente")
                            }
                        }
                    }
                }
            }

            if (!loading && error == null && visibleCategories.isEmpty()) {
                item { Card { Text("Nenhuma Categoria cadastrada.", modifier = Modifier.padding(18.dp)) } }
            }

            items(visibleCategories, key = { it.id }) { category ->
                val index = visibleCategories.indexOfFirst { it.id == category.id }
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            if (!reordering) {
                                CategoryIllustrationVisual(
                                    source = category.illustrationSource,
                                    key = category.illustrationKey,
                                    imageBytes = category.illustrationBytes,
                                    positionX = category.illustrationPositionX,
                                    positionY = category.illustrationPositionY,
                                    modifier = Modifier.size(54.dp),
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = if (reordering) 0.dp else 12.dp)
                            ) {
                                if (reordering) {
                                    Text(
                                        "Posição ${index + 1}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Text(category.name, fontWeight = FontWeight.Bold)
                                Text(
                                    if (category.productCount == 1) {
                                        "1 Produto"
                                    } else {
                                        "${category.productCount} Produtos"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                                if (!reordering && category.illustrationSource != null) {
                                    Text(
                                        if (category.illustrationSource == "library") {
                                            "Ilustração da biblioteca"
                                        } else {
                                            "Imagem própria"
                                        },
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                            if (!reordering) {
                                Text(
                                    "Pos. ${category.sortOrder ?: "—"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        ) {
                            if (reordering) {
                                TextButton(
                                    onClick = {
                                        draftOrder = moveCategoryOrder(
                                            draftOrder,
                                            category.id,
                                            -1,
                                        )
                                    },
                                    enabled = index > 0 && !reorderSaving,
                                ) {
                                    Text("↑ Subir")
                                }
                                TextButton(
                                    onClick = {
                                        draftOrder = moveCategoryOrder(
                                            draftOrder,
                                            category.id,
                                            1,
                                        )
                                    },
                                    enabled = index < visibleCategories.lastIndex && !reorderSaving,
                                ) {
                                    Text("↓ Descer")
                                }
                            } else {
                                TextButton(onClick = { onCategoryClick(category.id) }) {
                                    Text("Ver Produtos")
                                }
                                TextButton(onClick = { onEditCategory(category.id) }) {
                                    Text("Editar")
                                }
                                TextButton(
                                    onClick = { requestDelete(category) },
                                    enabled = !deletingCategory && category.productCount == 0,
                                ) {
                                    Text("Excluir")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { if (!deletingCategory) pendingDelete = null },
            title = { Text("Excluir Categoria?") },
            text = {
                Text(
                    "Excluir a Categoria “${category.name}”? Ela irá para a Lixeira e poderá ser restaurada por 7 dias."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete() },
                    enabled = !deletingCategory,
                ) {
                    Text(if (deletingCategory) "Excluindo…" else "Excluir Categoria")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    enabled = !deletingCategory,
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

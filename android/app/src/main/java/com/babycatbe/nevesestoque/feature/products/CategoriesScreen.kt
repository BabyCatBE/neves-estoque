package com.babycatbe.nevesestoque.feature.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<CategoryListItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val hasData = categories.isNotEmpty()
        loading = !hasData
        refreshing = hasData
        error = null
        runCatching { ProductsRepository().loadCategories() }
            .onSuccess {
                categories = it.sortedWith(
                    compareBy<CategoryListItem> { category -> category.sortOrder ?: Int.MAX_VALUE }
                        .thenBy { category -> category.name.lowercase() }
                )
            }
            .onFailure { error = "Não foi possível carregar as Categorias." }
        loading = false
        refreshing = false
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                    Text(
                        "Categorias",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(
                        onClick = { refreshing = true },
                        enabled = !refreshing,
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
                    "A ordem abaixo é a base usada no Estoque e nas Conferências.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (loading) item { Card { Text("Carregando Categorias…", modifier = Modifier.padding(18.dp)) } }

            error?.let {
                item {
                    Card {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp))
                    }
                }
            }

            if (!loading && error == null && categories.isEmpty()) {
                item { Card { Text("Nenhuma Categoria cadastrada.", modifier = Modifier.padding(18.dp)) } }
            }

            items(categories, key = { it.id }) { category ->
                Card(onClick = { onCategoryClick(category.id) }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, fontWeight = FontWeight.Bold)
                            Text(
                                if (category.productCount == 1) "1 Produto" else "${category.productCount} Produtos",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            if (category.illustrationSource != null) {
                                Text(
                                    "Ilustração configurada",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        Text(
                            "Pos. ${category.sortOrder ?: "—"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

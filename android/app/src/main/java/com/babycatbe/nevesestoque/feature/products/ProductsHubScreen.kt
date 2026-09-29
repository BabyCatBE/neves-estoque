package com.babycatbe.nevesestoque.feature.products

import com.babycatbe.nevesestoque.ui.components.NevesContentCard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProductsHubScreen(
    onBack: () -> Unit,
    onProducts: () -> Unit,
    onCategories: () -> Unit,
    onTrash: () -> Unit,
) {
    Scaffold(
        topBar = {
            NevesTopBarSurface {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    TextButton(onClick = onBack) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        "Produtos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                    )
                }
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                "Escolha o cadastro que deseja administrar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HubCard(
                icon = NevesIcons.Products,
                title = "Produtos",
                description = "Consultar o catálogo e abrir o detalhe de cada Produto.",
                onClick = onProducts,
            )
            HubCard(
                icon = NevesIcons.Products,
                title = "Categorias",
                description = "Consultar a organização, a ordem e quantos Produtos existem em cada Categoria.",
                onClick = onCategories,
            )
            HubCard(
                icon = NevesIcons.Trash,
                title = "Lixeira",
                description = "Lixeira Universal: restaure ou exclua definitivamente Produtos, Categorias, Fornecedores, Entradas e Conferências dos últimos 7 dias.",
                onClick = onTrash,
            )
        }
    }
}

@Composable
private fun HubCard(icon: Int, title: String, description: String, onClick: () -> Unit) {
    NevesContentCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            NevesIcon(icon, modifier = Modifier.padding(bottom = 10.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}


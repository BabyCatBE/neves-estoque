package com.babycatbe.nevesestoque.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.babycatbe.nevesestoque.R
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.theme.NevesColors
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babycatbe.nevesestoque.BuildConfig

@Composable
fun HomeScreen(
    displayName: String?,
    roleName: String?,
    onSignOut: () -> Unit,
    onModuleClick: (HomeModule) -> Unit,
    alertCount: Int? = null,
    onAlerts: () -> Unit = {},
) {
    val context = LocalContext.current
    var confirmExit by remember { mutableStateOf(false) }
    // Regra aprovada: na Home, o Voltar do Android pergunta antes de sair do aplicativo.
    BackHandler { confirmExit = true }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Deseja sair do aplicativo?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    (context as? Activity)?.finish()
                }) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NevesHeader(displayName, roleName, onSignOut, alertCount, onAlerts) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(homeModules, key = { it.route }) { module ->
                    ModuleCard(module) { onModuleClick(module) }
                }
            }
            Text(
                text = "Android ${BuildConfig.ANDROID_VERSION} • Sistema ${BuildConfig.SYSTEM_VERSION}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun NevesHeader(
    displayName: String?,
    roleName: String?,
    onSignOut: () -> Unit,
    alertCount: Int?,
    onAlerts: () -> Unit,
) {
    Surface(
        color = NevesColors.Header,
        contentColor = NevesColors.HeaderText,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.neves_brand_icon),
                contentDescription = "Panificadora Neves",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Controle de Estoque",
                    color = NevesColors.HeaderText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!displayName.isNullOrBlank()) {
                    Text(
                        listOfNotNull(displayName, roleName?.takeIf { it.isNotBlank() }).joinToString(" • "),
                        color = NevesColors.HeaderMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            val hasAlerts = (alertCount ?: 0) > 0
            IconButton(onClick = onAlerts) {
                BadgedBox(badge = {
                    if (hasAlerts) Badge(containerColor = NevesColors.Alert, contentColor = NevesColors.Header) {
                        Text(if (alertCount!! > 99) "99+" else alertCount.toString())
                    }
                }) {
                    NevesIcon(
                        NevesIcons.Alerts,
                        if (hasAlerts) "Alertas: $alertCount" else "Alertas",
                        tint = if (hasAlerts) NevesColors.Alert else NevesColors.HeaderText,
                    )
                }
            }
            IconButton(onClick = onSignOut) {
                NevesIcon(NevesIcons.Logout, "Sair da conta", tint = NevesColors.HeaderMuted)
            }
        }
    }
}

@Composable
private fun ModuleCard(module: HomeModule, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    NevesIcon(moduleIcon(module.route), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                module.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

private fun moduleIcon(route: String): Int = when (route) {
    "estoque" -> NevesIcons.Inventory
    "conferencias" -> NevesIcons.Checklist
    "entradas" -> NevesIcons.Entry
    "compras" -> NevesIcons.Cart
    "produtos" -> NevesIcons.Products
    "fornecedores" -> NevesIcons.Suppliers
    else -> NevesIcons.Inventory
}

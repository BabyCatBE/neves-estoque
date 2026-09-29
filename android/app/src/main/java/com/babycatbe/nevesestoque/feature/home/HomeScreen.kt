package com.babycatbe.nevesestoque.feature.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
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
                items(homeModules) { module ->
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
        color = NEVES_HEADER_BACKGROUND,
        contentColor = NEVES_HEADER_TEXT,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).shadow(1.dp, CircleShape),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("N", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Controle de Estoque",
                    color = NEVES_HEADER_TEXT,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!displayName.isNullOrBlank()) {
                    Text(
                        listOfNotNull(displayName, roleName?.takeIf { it.isNotBlank() }).joinToString(" • "),
                        color = NEVES_HEADER_MUTED,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            val hasAlerts = (alertCount ?: 0) > 0
            TextButton(onClick = onAlerts) {
                Text(
                    if (hasAlerts) "Alertas ($alertCount)" else "Alertas",
                    color = if (hasAlerts) ALERT_AMBER else NEVES_HEADER_ACTION,
                    fontWeight = if (hasAlerts) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onSignOut) {
                Text(
                    "Sair",
                    color = NEVES_HEADER_ACTION,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(module: HomeModule, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(1.08f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(module.mark, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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

private val NEVES_HEADER_BACKGROUND = Color(0xFF09090B)
private val NEVES_HEADER_TEXT = Color.White
private val NEVES_HEADER_MUTED = Color(0xFFA1A1AA)
private val NEVES_HEADER_ACTION = Color(0xFFEF4444)
private val ALERT_AMBER = Color(0xFFF59E0B)

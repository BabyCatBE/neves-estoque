package com.babycatbe.nevesestoque.feature.settings

import com.babycatbe.nevesestoque.ui.components.NevesContentCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.babycatbe.nevesestoque.ui.theme.NevesColors
import com.babycatbe.nevesestoque.BuildConfig
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val updater = remember { AndroidUpdater(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    LaunchedEffect(Unit) { state = updater.restore() }
    LaunchedEffect(state) {
        while (state is UpdateState.Downloading) { delay(900); state = updater.poll() }
    }
    Scaffold(topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = NevesColors.Header, titleContentColor = Color.White, navigationIconContentColor = Color.White),
            title = { Text("Configurações", fontWeight = FontWeight.SemiBold) }, navigationIcon = {
            IconButton(onClick = onBack) { NevesIcon(NevesIcons.Back, "Voltar") }
        })
    }) { padding ->
        Column(verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { NevesIcon(NevesIcons.Refresh, "Atualizações", tint = Color.White) }
                    }
                    Column {
                        Text("Atualização do aplicativo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Mantenha o Neves Estoque seguro e atualizado.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            NevesContentCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Versão instalada: ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})")
                    when (val current = state) {
                        UpdateState.Idle -> Text("Verifique se existe uma versão mais recente.")
                        UpdateState.Checking -> Text("Verificando atualização…")
                        UpdateState.UpToDate -> Text("Aplicativo atualizado.")
                        is UpdateState.Available -> ReleaseInfo(current.release, "Atualização disponível.")
                        is UpdateState.Downloading -> {
                            ReleaseInfo(current.release, "Baixando atualização…")
                            if (current.progress != null) {
                                LinearProgressIndicator(progress = { current.progress / 100f }, modifier = Modifier.fillMaxWidth())
                                Text("${current.progress}%")
                            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is UpdateState.Ready -> ReleaseInfo(current.release, "Download concluído e SHA-256 validado.")
                        is UpdateState.Error -> {
                            Text(current.message, color = MaterialTheme.colorScheme.error)
                            current.release?.let { ReleaseInfo(it, null) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading, onClick = {
                            state = UpdateState.Checking
                            scope.launch { state = updater.check() }
                        }) { Text("Verificar atualização") }
                        val release = when (val current = state) {
                            is UpdateState.Available -> current.release
                            is UpdateState.Error -> current.release
                            else -> null
                        }
                        if (release != null && release.versionCode > BuildConfig.VERSION_CODE) {
                            Button(onClick = { state = updater.startDownload(release) }) { Text("Baixar atualização") }
                        }
                    }
                    if (state is UpdateState.Ready) {
                        val ready = state as UpdateState.Ready
                        Button(onClick = {
                            if (updater.canInstallPackages()) {
                                runCatching { updater.install(ready.file) }.onFailure {
                                    state = UpdateState.Error("Não foi possível abrir o instalador Android.", ready.release)
                                }
                            } else updater.openInstallPermission()
                        }) { Text("Instalar") }
                        if (!updater.canInstallPackages()) Text("Ao tocar em Instalar, autorize “Instalar apps desconhecidos” para o Neves Estoque e retorne a esta tela.")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), shape = RoundedCornerShape(12.dp)) {
                Text("Proteção de atualização • A instalação só é liberada após validar o SHA-256 publicado junto à Release oficial.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun ReleaseInfo(release: UpdateRelease, status: String?) {
    if (status != null) Text(status, fontWeight = FontWeight.SemiBold)
    Text("Versão disponível: ${release.versionName} (versionCode ${release.versionCode})")
}

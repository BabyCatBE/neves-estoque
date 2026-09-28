package com.babycatbe.nevesestoque.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.feature.suppliers.SuppliersRepository
import com.babycatbe.nevesestoque.feature.trash.TrashRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Contagens exibidas na Home e em Alertas, com as mesmas regras da Web. */
data class AlertCounts(
    val trash: Int,
    val pendingSuppliers: Int,
    val pendingProducts: Int,
) {
    val pendingTotal: Int get() = pendingSuppliers + pendingProducts
    val total: Int get() = trash + pendingTotal
}

fun alertPendingLabel(count: Int): String = "$count pendência${if (count == 1) "" else "s"}"

@Serializable
private data class PendingProductRow(
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
)

class AlertsRepository {
    private val trashRepository = TrashRepository()
    private val suppliersRepository = SuppliersRepository()

    private suspend fun pendingProducts(): Int {
        val client = SupabaseProvider.client ?: error("Supabase não está configurado nesta build.")
        return client.from("products")
            .select(Columns.list("id", "category_id")) {
                attachRegisteredDevice()
                filter { exact("deleted_at", null) }
            }
            .decodeList<PendingProductRow>()
            .count { it.categoryId == null }
    }

    suspend fun loadCounts(): AlertCounts = coroutineScope {
        val trash = async { trashRepository.loadRestorableTrash().size }
        val suppliers = async { suppliersRepository.loadActiveSuppliers().count { it.isPending } }
        val products = async { pendingProducts() }
        AlertCounts(trash = trash.await(), pendingSuppliers = suppliers.await(), pendingProducts = products.await())
    }
}

data class AlertsUiState(
    val loading: Boolean = true,
    val counts: AlertCounts? = null,
    val errorMessage: String? = null,
)

class AlertsViewModel : ViewModel() {
    private val repository = AlertsRepository()
    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = _uiState.value.counts == null, errorMessage = null)
            runCatching { repository.loadCounts() }
                .onSuccess { _uiState.value = AlertsUiState(loading = false, counts = it) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loading = false, errorMessage = "Não foi possível carregar os Alertas.")
                }
        }
    }
}

@Composable
fun AlertsRoute(
    onBack: () -> Unit,
    onTrash: () -> Unit,
    onPendingSuppliers: () -> Unit,
    onPendingProducts: () -> Unit,
    onLocalPending: () -> Unit = {},
    vm: AlertsViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val localPending by com.babycatbe.nevesestoque.feature.offline.PendingStore.pending.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val counts = state.counts

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                    Text("Alertas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::refresh) { Text("Atualizar") }
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
                    "A Lixeira é uma área de recuperação. Abaixo ficam apenas cadastros e tarefas que exigem atenção.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            item { SectionTitle("OFFLINE") }
            item {
                AlertCard(
                    title = "Pendências locais",
                    count = localPending.size,
                    description = "Entradas e Conferências preparadas sem internet que ainda não foram enviadas ao estoque oficial.",
                    tone = if (localPending.isNotEmpty()) Tone.Amber else Tone.Neutral,
                    onClick = onLocalPending,
                )
            }
            if (state.loading) item { Card { Text("Carregando Alertas…", modifier = Modifier.padding(18.dp)) } }
            state.errorMessage?.let { message ->
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = vm::refresh) { Text("Tentar novamente") }
                        }
                    }
                }
            }
            if (counts != null) {
                item { SectionTitle("RECUPERAÇÃO") }
                item {
                    AlertCard(
                        title = "Lixeira",
                        count = counts.trash,
                        description = "Produtos, Categorias, Fornecedores, Entradas e Conferências restauráveis por 7 dias.",
                        tone = if (counts.trash > 0) Tone.Red else Tone.Neutral,
                        onClick = onTrash,
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        SectionTitle("PENDÊNCIAS", Modifier.weight(1f))
                        CountPill(alertPendingLabel(counts.pendingTotal), if (counts.pendingTotal > 0) Tone.Amber else Tone.Neutral)
                    }
                }
                if (counts.pendingTotal == 0) {
                    item {
                        Card {
                            Text(
                                "Nenhum cadastro pendente no momento.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                            )
                        }
                    }
                }
                item {
                    AlertCard(
                        title = "Fornecedores pendentes",
                        count = counts.pendingSuppliers,
                        description = "Cadastros rápidos que ainda precisam de Empresa e/ou Telefone.",
                        tone = if (counts.pendingSuppliers > 0) Tone.Amber else Tone.Neutral,
                        onClick = onPendingSuppliers,
                    )
                }
                item {
                    AlertCard(
                        title = "Produtos pendentes",
                        count = counts.pendingProducts,
                        description = "Produtos sem Categoria que ainda precisam ter o cadastro concluído.",
                        tone = if (counts.pendingProducts > 0) Tone.Amber else Tone.Neutral,
                        onClick = onPendingProducts,
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private enum class Tone { Neutral, Amber, Red }

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
private fun AlertCard(title: String, count: Int, description: String, tone: Tone, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                CountPill(count.toString(), tone)
            }
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CountPill(text: String, tone: Tone) {
    val (bg, fg) = when (tone) {
        Tone.Red -> Color(0xFFFEF2F2) to Color(0xFF991B1B)
        Tone.Amber -> Color(0xFFFFFBEB) to Color(0xFF78350F)
        Tone.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = MaterialTheme.shapes.extraLarge) {
        Text(
            text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

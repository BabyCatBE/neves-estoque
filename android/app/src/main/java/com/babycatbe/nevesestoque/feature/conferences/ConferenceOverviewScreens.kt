package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.ui.components.NevesContentCard
import com.babycatbe.nevesestoque.ui.components.NevesStatusMessage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.babycatbe.nevesestoque.ui.components.NevesRefreshIcon
import com.babycatbe.nevesestoque.ui.components.NevesActionLabel
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babycatbe.nevesestoque.ui.load.RefreshOnKeyChange
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConferencesHubScreen(
    onBack: () -> Unit,
    onPrint: () -> Unit,
    onNewConference: () -> Unit,
    onHistory: () -> Unit,
    onTrash: () -> Unit = {},
) {
    Scaffold(topBar = { ConferenceTopBar("Conferência", onBack) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text(
                "Conferência física",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "A contagem física é a referência do estoque. Cada Categoria pode ser conferida de forma independente.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConferenceHubCard(
                eyebrow = "PREPARAÇÃO",
                icon = NevesIcons.Print,
                title = "Imprimir papéis",
                description = "Gere as folhas A4 a partir das Categorias e Produtos atuais.",
                onClick = onPrint,
            )
            ConferenceHubCard(
                eyebrow = "OPERAÇÃO",
                icon = NevesIcons.Checklist,
                title = "Fazer conferência",
                description = "Escolha uma Categoria e registre todas as quantidades contadas.",
                onClick = onNewConference,
                primary = true,
            )
            ConferenceHubCard(
                eyebrow = "CONSULTA",
                icon = NevesIcons.History,
                title = "Histórico",
                description = "Consulte as Conferências salvas por Categoria e corrija um registro quando necessário.",
                onClick = onHistory,
            )
            ConferenceHubCard(
                eyebrow = "RECUPERAÇÃO",
                icon = NevesIcons.Trash,
                title = "Lixeira",
                description = "Restaure ou exclua definitivamente Conferências excluídas nos últimos 7 dias.",
                onClick = onTrash,
            )
        }
    }
}

@Composable
private fun ConferenceHubCard(
    icon: Int,
    eyebrow: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    NevesContentCard(onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            NevesIcon(icon, modifier = Modifier.padding(bottom = 10.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                eyebrow,
                color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 5.dp),
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun ConferenceCategoriesRoute(
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onEditConference: (String) -> Unit,
    onHistoryCategory: (String) -> Unit,
    saveStatuses: Map<String, ConferenceSaveStatus> = emptyMap(),
    onRetrySave: (String) -> Unit = {},
    onDiscardFailed: (String) -> Unit = {},
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val vm: ConferenceCategoriesViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    RefreshOnKeyChange(refreshKey) { vm.refresh() }
    ConferenceCategoriesScreen(
        title = "Fazer conferência",
        subtitle = "Cada Categoria é uma Conferência independente. Todas as quantidades precisam ser preenchidas.",
        state = state,
        onBack = onBack,
        onRefresh = vm::refresh,
        onCategoryClick = onCategoryClick,
        onEditConference = onEditConference,
        onHistoryCategory = onHistoryCategory,
        saveStatuses = saveStatuses,
        onRetrySave = onRetrySave,
        onDiscardFailed = onDiscardFailed,
        historyMode = false,
        noticeMessage = noticeMessage,
        onDismissNotice = onDismissNotice,
    )
}

@Composable
fun ConferenceHistoryCategoriesRoute(
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
    refreshKey: Long = 0L,
) {
    val vm: ConferenceCategoriesViewModel = viewModel(key = "conference-history-categories")
    val state by vm.uiState.collectAsStateWithLifecycle()
    RefreshOnKeyChange(refreshKey) { vm.refresh() }
    ConferenceCategoriesScreen(
        title = "Histórico de Conferências",
        subtitle = "Escolha uma Categoria para consultar as contagens salvas.",
        state = state,
        onBack = onBack,
        onRefresh = vm::refresh,
        onCategoryClick = onCategoryClick,
        onEditConference = {},
        onHistoryCategory = {},
        saveStatuses = emptyMap(),
        onRetrySave = {},
        onDiscardFailed = {},
        historyMode = true,
        noticeMessage = null,
        onDismissNotice = {},
    )
}

@Composable
private fun ConferenceCategoriesScreen(
    title: String,
    subtitle: String,
    state: ConferenceCategoriesUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onEditConference: (String) -> Unit,
    onHistoryCategory: (String) -> Unit,
    saveStatuses: Map<String, ConferenceSaveStatus>,
    onRetrySave: (String) -> Unit,
    onDiscardFailed: (String) -> Unit,
    historyMode: Boolean,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
) {
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var discardCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            ConferenceTopBar(
                title = title,
                onBack = onBack,
                trailing = {
                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                        NevesRefreshIcon(state.refreshing)
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            noticeMessage?.let { message ->
                item {
                    NevesContentCard {
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
                    Text("Categorias", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (state.loading) {
                item { NevesContentCard { Text("Carregando Categorias…", modifier = Modifier.padding(18.dp)) } }
            }
            state.errorMessage?.let {
                item { NevesContentCard { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp)) } }
            }
            items(state.categories, key = { it.id }) { category ->
                val enabled = if (historyMode) category.lastConferenceAt != null else category.productCount > 0
                val saveStatus = if (historyMode) null else saveStatuses[category.id]
                val savingBlocked = saveStatus?.phase == ConferenceSavePhase.Saving ||
                    saveStatus?.phase == ConferenceSavePhase.Failed
                val conferredToday = category.conferredToday ||
                    saveStatus?.phase == ConferenceSavePhase.Saved
                NevesContentCard(onClick = {
                    if (enabled && !savingBlocked) {
                        if (!historyMode && conferredToday) selectedCategoryId = category.id
                        else onCategoryClick(category.id)
                    }
                }) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(category.name, fontWeight = FontWeight.Bold)
                                Text(
                                    category.productCount.toString() + if (category.productCount == 1) " Produto" else " Produtos",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            if (!historyMode && conferredToday) {
                                Text(
                                    "✓ Conferida hoje",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Text(
                            when {
                                historyMode && category.lastConferenceAt != null ->
                                    "Última Conferência: " + formatConferenceDateShort(category.lastConferenceAt)
                                historyMode -> "Sem Conferências registradas."
                                category.productCount == 0 -> "Categoria sem Produtos ativos."
                                saveStatus?.phase == ConferenceSavePhase.Saving ->
                                    "Salvando em segundo plano. Aguarde a confirmação antes de reenviar."
                                saveStatus?.phase == ConferenceSavePhase.Failed ->
                                    "Falha no envio: " + (saveStatus.message ?: "tente novamente.")
                                conferredToday -> "Outra Conferência hoje só deve ser registrada se houve nova contagem física."
                                category.lastConferenceAt != null ->
                                    "Última Conferência: " + formatConferenceDateShort(category.lastConferenceAt)
                                else -> "Nunca conferida."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (saveStatus?.phase == ConferenceSavePhase.Saving) {
                            Text(
                                "Salvando…",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (saveStatus?.phase == ConferenceSavePhase.Failed) {
                            Row {
                                TextButton(onClick = { onRetrySave(category.id) }) {
                                    Text("Tentar novamente")
                                }
                                TextButton(onClick = { discardCategoryId = category.id }) {
                                    Text("Descartar tentativa")
                                }
                            }
                        }
                    }
                }
            }
            if (!state.loading && state.categories.isEmpty()) {
                item { NevesContentCard { NevesStatusMessage("Ainda não existem Categorias ativas.") } }
            }
        }
    }
    val selected = state.categories.firstOrNull { it.id == selectedCategoryId }
    if (!historyMode && selected != null) {
        AlertDialog(
            onDismissRequest = { selectedCategoryId = null },
            title = { Text(selected.name) },
            text = { Text("Esta Categoria já foi conferida hoje. Você deseja editar/visualizar a contagem ou fazer uma nova Conferência?") },
            confirmButton = {
                Button(onClick = {
                    selectedCategoryId = null
                    if (selected.todayConferenceCount == 1 && selected.latestConferenceId != null) {
                        onEditConference(selected.latestConferenceId)
                    } else {
                        onHistoryCategory(selected.id)
                    }
                }) { Text("Editar/visualizar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedCategoryId = null
                    onCategoryClick(selected.id)
                }) { Text("Nova conferência") }
            },
        )
    }
    if (discardCategoryId != null) {
        val id = discardCategoryId!!
        AlertDialog(
            onDismissRequest = { discardCategoryId = null },
            title = { Text("Descartar tentativa?") },
            text = { Text("A contagem preenchida para esta tentativa será perdida do aparelho. Em caso de falha de rede, verifique o Histórico antes de registrar outra.") },
            confirmButton = {
                Button(onClick = {
                    onDiscardFailed(id)
                    discardCategoryId = null
                }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { discardCategoryId = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
fun CategoryConferenceHistoryRoute(
    categoryId: String,
    onBack: () -> Unit,
    onConferenceClick: (String) -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val vm: CategoryConferenceHistoryViewModel = viewModel(
        key = "category-conference-history-" + categoryId,
        factory = CategoryConferenceHistoryViewModel.Factory(categoryId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    RefreshOnKeyChange(refreshKey) { vm.refresh() }

    val countsByDay = remember(state.conferences) {
        state.conferences.groupingBy { conferenceLocalDate(it.effectiveAt) }.eachCount()
    }

    Scaffold(
        topBar = {
            ConferenceTopBar(
                title = "Histórico da Categoria",
                onBack = onBack,
                trailing = {
                    TextButton(onClick = vm::refresh, enabled = !state.refreshing) {
                        NevesRefreshIcon(state.refreshing)
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            noticeMessage?.let { message ->
                item {
                    NevesContentCard {
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
                    Text(
                        state.setup?.categoryName ?: "Conferências",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Contagens físicas salvas para esta Categoria.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (state.loading) {
                item { NevesContentCard { Text("Carregando Conferências…", modifier = Modifier.padding(18.dp)) } }
            }
            state.errorMessage?.let {
                item { NevesContentCard { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp)) } }
            }
            items(state.conferences, key = { it.id }) { conference ->
                val dayCount = countsByDay[conferenceLocalDate(conference.effectiveAt)] ?: 1
                NevesContentCard(onClick = { onConferenceClick(conference.id) }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            formatConferenceDateLong(conference.effectiveAt) +
                                if (dayCount > 1) " · " + formatConferenceTime(conference.effectiveAt) else "",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Responsável: " + conference.physicalResponsible,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (!state.loading && state.conferences.isEmpty()) {
                item {
                    NevesContentCard {
                        Text(
                            "Nenhuma Conferência registrada para esta Categoria.",
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConferenceDetailRoute(
    conferenceId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: (String, String) -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val vm: ConferenceDetailViewModel = viewModel(
        key = "conference-detail-" + conferenceId,
        factory = ConferenceDetailViewModel.Factory(conferenceId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var deleteOpen by rememberSaveable { mutableStateOf(false) }

    RefreshOnKeyChange(refreshKey) { vm.refresh() }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            val categoryId = state.details?.categoryId ?: return@LaunchedEffect
            onDeleted(categoryId, "Conferência enviada para a Lixeira. Ela pode ser restaurada por 7 dias.")
        }
    }

    Scaffold(
        topBar = {
            ConferenceTopBar(
                title = "Conferência",
                onBack = onBack,
                trailing = {
                    TextButton(onClick = onEdit, enabled = state.details != null && !state.deleting) {
                        NevesActionLabel("Editar", NevesIcons.Edit)
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            noticeMessage?.let { message ->
                item {
                    NevesContentCard {
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
                item { NevesContentCard { Text("Carregando Conferência…", modifier = Modifier.padding(18.dp)) } }
            }
            state.errorMessage?.let {
                item { NevesContentCard { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp)) } }
            }
            state.actionError?.let {
                item { NevesContentCard { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp)) } }
            }
            state.details?.let { details ->
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(
                            details.categoryName,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            formatConferenceDateLong(details.effectiveAt),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "Responsável: " + details.physicalResponsible,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                details.observation?.let { observation ->
                    item {
                        NevesContentCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Observação", fontWeight = FontWeight.Bold)
                                Text(observation, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Quantidades registradas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(details.items, key = { it.id }) { item ->
                    NevesContentCard {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(item.productName, fontWeight = FontWeight.Bold)
                                Text(item.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                formatConferenceNumber(item.quantity),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                item {
                    NevesContentCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Excluir Conferência", fontWeight = FontWeight.Bold)
                            Text(
                                "A Conferência sairá do Histórico ativo e seus efeitos deixarão de compor os cálculos atuais. A restauração ficará disponível por 7 dias na Lixeira.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            TextButton(
                                onClick = { deleteOpen = true },
                                enabled = !state.deleting,
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                Text(if (state.deleting) "Excluindo…" else "Enviar para a Lixeira")
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteOpen) {
        val details = state.details
        AlertDialog(
            onDismissRequest = { if (!state.deleting) deleteOpen = false },
            title = { Text("Excluir Conferência?") },
            text = {
                Text(
                    (details?.categoryName ?: "Esta Conferência") +
                        " · " + (details?.effectiveAt?.let(::formatConferenceDateShort) ?: "") +
                        "\n\nEla poderá ser restaurada por 7 dias."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteOpen = false
                        vm.deleteConference()
                    },
                    enabled = !state.deleting,
                ) { NevesActionLabel("Excluir", NevesIcons.Trash) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }, enabled = !state.deleting) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
fun ConferencePrintRoute(onBack: () -> Unit) {
    val vm: ConferencePrintViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val data = state.data
    val pageCount = data?.categories?.sumOf { category ->
        if (category.products.isEmpty()) 0 else (category.products.size + 17) / 18
    } ?: 0

    Scaffold(topBar = { ConferenceTopBar("Papéis de Conferência", onBack) }) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Folhas A4", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "As folhas são geradas do cadastro atual. O Android abrirá a pré-visualização do sistema para escolher impressora ou salvar em PDF.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (state.loading) {
                item { NevesContentCard { Text("Gerando dados…", modifier = Modifier.padding(18.dp)) } }
            }
            state.errorMessage?.let {
                item { NevesContentCard { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp)) } }
            }
            data?.let { printData ->
                if (printData.pendingProductCount > 0) {
                    item {
                        NevesContentCard {
                            Text(
                                printData.pendingProductCount.toString() +
                                    " Produto(s) sem Categoria não aparecerão nos papéis.",
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                item {
                    NevesContentCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Prévia do conteúdo", fontWeight = FontWeight.Bold)
                            Text(
                                pageCount.toString() + " página(s) A4 · " +
                                    printData.categories.count { it.products.isNotEmpty() } + " Categoria(s)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                            Button(
                                onClick = { printConferenceSheets(context, printData) },
                                enabled = pageCount > 0,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) { Text("Abrir pré-visualização e imprimir") }
                        }
                    }
                }
                items(printData.categories.filter { it.products.isNotEmpty() }, key = { it.id }) { category ->
                    NevesContentCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(category.name, fontWeight = FontWeight.Bold)
                            Text(
                                category.products.size.toString() +
                                    if (category.products.size == 1) " Produto" else " Produtos",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (pageCount == 0) {
                    item { NevesContentCard { Text("Não existem Categorias com Produtos ativos para imprimir.", modifier = Modifier.padding(18.dp)) } }
                }
            }
        }
    }
}

@Composable
fun ConferenceTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    NevesTopBarSurface {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = onBack) { NevesIcon(NevesIcons.Back, "Voltar") }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(top = 10.dp),
            )
            trailing()
        }
    }
}


package com.babycatbe.nevesestoque.feature.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.babycatbe.nevesestoque.data.offline.ConnectivityMonitor
import java.time.OffsetDateTime
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun formatPendingDateTime(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(value)

internal fun formatPendingDate(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(value)

@Composable
fun PendingListRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val pending by PendingStore.pending.collectAsState()
    val problems by PendingStore.problems.collectAsState()
    val loaded by PendingStore.loaded.collectAsState()
    val online by ConnectivityMonitor.online.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<PendingOperation?>(null) }
    var toConfirm by remember { mutableStateOf<PendingOperation?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(loaded) {
        if (!loaded) PendingStore.reload()
    }

    PendingScaffold(title = "Pendências locais", onBack = onBack) {
        item {
            Text(
                "Entradas e Conferências preparadas sem internet. Elas ficam só neste aparelho e ainda não alteram o " +
                    "estoque. Nada é enviado automaticamente: confirme cada envio, edite, exclua ou mantenha pendente.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        (notice ?: noticeMessage)?.let { message ->
            item {
                Card {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { notice = null; onDismissNotice() }) { Text("Fechar") }
                    }
                }
            }
        }
        if (!online) {
            item { Warning("Sem internet: o envio fica disponível quando a conexão voltar. Editar e excluir funcionam normalmente.") }
        }
        if (problems.isNotEmpty()) {
            item {
                Warning(
                    if (problems.size == 1) problems.first().userMessage
                    else "Existem ${problems.size} pendências locais com problema de leitura. Os arquivos foram preservados neste aparelho."
                )
            }
        }
        if (!loaded) {
            item { Text("Carregando pendências locais…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (pending.isEmpty() && problems.isEmpty()) {
            item {
                Card {
                    Text(
                        "Nenhuma pendência local neste aparelho.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                    )
                }
            }
        }
        items(pending, key = { it.localId }) { operation ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(operation.kindLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(operation.summaryTitle, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    Text(operation.summarySubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Data: ${formatPendingDate(operation.effectiveAt)} · preparada em ${formatPendingDateTime(operation.createdAt)} por ${operation.actorLabel}" +
                            if (operation.updatedAt != operation.createdAt) " · editada em ${formatPendingDateTime(operation.updatedAt)}" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        Button(onClick = { toConfirm = operation }, enabled = online) { Text("Confirmar envio") }
                        OutlinedButton(onClick = { onEdit(operation.localId) }) { Text("Editar") }
                        TextButton(onClick = { toDelete = operation }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    toDelete?.let { operation ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Excluir pendência?") },
            text = {
                Text(
                    "${operation.kindLabel} “${operation.summaryTitle}” será removida somente deste aparelho. " +
                        "Nada foi enviado ao estoque oficial."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = true
                        scope.launch {
                            when (val result = PendingStore.delete(operation.localId)) {
                                PendingMutationResult.Success -> {
                                    toDelete = null
                                    notice = "Pendência excluída deste aparelho."
                                }
                                is PendingMutationResult.Failure -> notice = result.userMessage
                            }
                            deleting = false
                        }
                    },
                    enabled = !deleting,
                ) { Text(if (deleting) "Excluindo…" else "Excluir pendência", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }, enabled = !deleting) { Text("Manter pendente") } },
        )
    }

    toConfirm?.let { operation ->
        PendingConfirmFlow(operation) { message ->
            toConfirm = null
            if (message.isNotBlank()) notice = message
        }
    }
}

@Composable
fun PendingEditRoute(localId: String, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val pending by PendingStore.pending.collectAsState()
    val loaded by PendingStore.loaded.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val operation = pending.firstOrNull { it.localId == localId }

    androidx.compose.runtime.LaunchedEffect(localId, loaded) {
        if (!loaded) PendingStore.reload()
    }

    if (!loaded) {
        PendingScaffold(title = "Editar pendência", onBack = onBack) {
            item { Text("Carregando pendência…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        return
    }
    if (operation == null) {
        PendingScaffold(title = "Editar pendência", onBack = onBack) {
            item { Warning("Esta pendência não existe mais neste aparelho.") }
        }
        return
    }

    var draft by remember(localId, operation.updatedAt) { mutableStateOf(initialEditDraft(operation)) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    PendingScaffold(title = "Editar pendência", onBack = onBack) {
        item {
            Column {
                Text(operation.kindLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(operation.summaryTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "A edição mantém a mesma identificação da pendência. Para trocar Fornecedor, Categoria ou Produto, exclua e prepare outra.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (operation.kind != PendingKind.ProductConference) {
            item {
                Field("Data (AAAA-MM-DD)", draft.date) { draft = draft.copy(date = it) }
            }
        }
        if (operation.kind != PendingKind.Entry) {
            item { Field("Responsável pela contagem física", draft.responsible) { draft = draft.copy(responsible = it) } }
        }
        editItems(operation, draft) { draft = it }
        item { Field("Observação", draft.observation, singleLine = false) { draft = draft.copy(observation = it) } }
        if (errors.isNotEmpty()) {
            item { Warning(errors.joinToString("\n")) }
        }
        item {
            Button(
                onClick = {
                    val result = applyPendingEdit(operation, draft)
                    val updated = result.operation
                    if (updated == null) {
                        errors = result.errors
                    } else {
                        saving = true
                        scope.launch {
                            when (val saveResult = PendingStore.save(updated)) {
                                PendingMutationResult.Success -> onSaved("Pendência atualizada neste aparelho.")
                                is PendingMutationResult.Failure -> errors = listOf(saveResult.userMessage)
                            }
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Salvando…" else "Salvar edição") }
        }
    }
}

private fun LazyListScope.editItems(
    operation: PendingOperation,
    draft: PendingEditDraft,
    onChange: (PendingEditDraft) -> Unit,
) {
    when (operation.kind) {
        PendingKind.Entry -> {
            val payload = operation.entry ?: return
            payload.items.forEachIndexed { index, item ->
                val key = index.toString()
                val removed = index in draft.removedItems
                item(key = "entry-$index") {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${item.productName} (${item.unit})", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    onChange(draft.copy(removedItems = if (removed) draft.removedItems - index else draft.removedItems + index))
                                }) { Text(if (removed) "Manter" else "Remover") }
                            }
                            if (!removed) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = draft.quantities[key].orEmpty(),
                                        onValueChange = { onChange(draft.copy(quantities = draft.quantities + (key to it))) },
                                        label = { Text("Quantidade") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = draft.prices[key].orEmpty(),
                                        onValueChange = { onChange(draft.copy(prices = draft.prices + (key to it))) },
                                        label = { Text("Preço (R$)") },
                                        placeholder = { Text("Não informado") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            } else {
                                Text("Item será removido desta pendência.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        PendingKind.CategoryConference -> {
            val payload = operation.categoryConference ?: return
            items(payload.items, key = { "conf-${it.productId}" }) { item ->
                QuantityRow("${item.productName} (${item.unit})", draft.quantities[item.productId].orEmpty()) {
                    onChange(draft.copy(quantities = draft.quantities + (item.productId to it)))
                }
            }
        }
        PendingKind.ProductConference -> {
            val payload = operation.productConference ?: return
            item(key = "unit-${payload.productId}") {
                QuantityRow("Nova quantidade (${payload.unit})", draft.quantities[payload.productId].orEmpty()) {
                    onChange(draft.copy(quantities = draft.quantities + (payload.productId to it)))
                }
            }
        }
    }
}

@Composable
private fun QuantityRow(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(110.dp),
        )
    }
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun Warning(text: String) {
    Surface(color = Color(0xFFFEF3C7), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Color(0xFF92400E), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

@Composable
internal fun PendingScaffold(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            content()
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------- Confirmação consciente (etapa 10c) ----------

private sealed interface ConfirmStep {
    data object Checking : ConfirmStep
    data class EntryConflicts(val conflicts: List<EntryConferenceConflict>, val positions: List<EntryConflictPosition>) : ConfirmStep
    data class ConferenceReview(
        val warnings: List<com.babycatbe.nevesestoque.feature.conferences.ConferenceConsumptionWarning>,
        val sameDay: List<com.babycatbe.nevesestoque.feature.conferences.ConferenceHistoryItem>,
    ) : ConfirmStep
    data class Final(val entryEffectiveAt: String?) : ConfirmStep
    data object Sending : ConfirmStep
    data class Error(val message: String) : ConfirmStep
}

@Composable
fun PendingConfirmFlow(operation: PendingOperation, onDone: (String) -> Unit) {
    val repository = remember { PendingSyncRepository() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var step by remember(operation.localId) { mutableStateOf<ConfirmStep>(ConfirmStep.Checking) }
    var chosenPosition by remember(operation.localId) { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(operation.localId) {
        step = try {
            if (operation.kind == PendingKind.Entry) {
                val conflicts = repository.entryConflicts(operation)
                if (conflicts.isEmpty()) {
                    ConfirmStep.Final(null)
                } else {
                    ConfirmStep.EntryConflicts(conflicts, buildEntryConflictPositions(conflicts, operation.effectiveAt))
                }
            } else {
                val (warnings, sameDay) = repository.conferenceReview(operation)
                if (warnings.isEmpty() && sameDay.isEmpty()) ConfirmStep.Final(null) else ConfirmStep.ConferenceReview(warnings, sameDay)
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            ConfirmStep.Error("Não foi possível consultar o servidor agora. A pendência continua guardada neste aparelho.")
        }
    }

    fun send(entryEffectiveAt: String?) {
        step = ConfirmStep.Sending
        scope.launch {
            when (val result = repository.send(operation, entryEffectiveAt)) {
                is PendingSendResult.Sent -> onDone(result.message)
                is PendingSendResult.Failed -> step = ConfirmStep.Error(result.message)
            }
        }
    }

    val busy = step is ConfirmStep.Checking || step is ConfirmStep.Sending
    AlertDialog(
        onDismissRequest = { if (!busy) onDone("") },
        title = { Text("Confirmar envio · ${operation.kindLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(operation.summaryTitle, fontWeight = FontWeight.Bold)
                when (val current = step) {
                    ConfirmStep.Checking -> Text("Consultando o estado atual do servidor…")
                    ConfirmStep.Sending -> Text("Enviando ao estoque oficial…")
                    is ConfirmStep.Error -> Text(current.message, color = MaterialTheme.colorScheme.error)
                    is ConfirmStep.Final -> Text(
                        "Data: ${formatPendingDate(current.entryEffectiveAt ?: operation.effectiveAt)} · ${operation.summarySubtitle}. " +
                            "Ao confirmar, o registro passa a valer no estoque oficial."
                    )
                    is ConfirmStep.EntryConflicts -> {
                        Text("Existe Conferência no mesmo dia com Produto em comum. Quando esta Entrada aconteceu?")
                        current.conflicts.forEach { conflict ->
                            Text(
                                "Conferência das ${formatPendingDateTime(conflict.effectiveAt).takeLast(5)} · Responsável: ${conflict.physicalResponsible}\n" +
                                    conflict.items.joinToString("\n") {
                                        "• ${it.productName}: Entrada ${formatDecimalForEdit(it.entryQuantity)} · Conferência ${formatDecimalForEdit(it.conferenceQuantity)}"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        current.positions.forEach { position ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(
                                    selected = chosenPosition == position.id,
                                    onClick = { chosenPosition = position.id },
                                )
                                Column {
                                    Text(position.label, fontWeight = FontWeight.SemiBold)
                                    Text(position.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    is ConfirmStep.ConferenceReview -> {
                        if (current.warnings.isNotEmpty()) {
                            Text("Consumo fora do padrão em ${current.warnings.size} ${if (current.warnings.size == 1) "Produto" else "Produtos"}:")
                            current.warnings.forEach { warning ->
                                val name = operation.categoryConference?.items?.firstOrNull { it.productId == warning.productId }?.productName
                                    ?: operation.productConference?.productLabel ?: "Produto"
                                Text(
                                    "• $name: esperado ${formatDecimalForEdit(warning.expectedConsumption)} · indicado ${formatDecimalForEdit(warning.actualConsumption)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (current.sameDay.isNotEmpty()) {
                            Text(
                                "Já existe Conferência desta Categoria nesta data (" +
                                    current.sameDay.joinToString { "${formatPendingDateTime(it.effectiveAt).takeLast(5)} · ${it.physicalResponsible}" } +
                                    "). Se esta pendência substitui a existente, mantenha pendente e corrija a existente no Histórico. " +
                                    "Continue somente se foi realmente outra contagem física.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (val current = step) {
                is ConfirmStep.Final -> TextButton(onClick = { send(current.entryEffectiveAt) }) { Text("Enviar") }
                is ConfirmStep.EntryConflicts -> TextButton(
                    onClick = { step = ConfirmStep.Final(current.positions.firstOrNull { it.id == chosenPosition }?.effectiveAt) },
                    enabled = chosenPosition != null,
                ) { Text("Continuar") }
                is ConfirmStep.ConferenceReview -> TextButton(onClick = { step = ConfirmStep.Final(null) }) { Text("Continuar mesmo assim") }
                else -> {}
            }
        },
        dismissButton = {
            if (!busy) {
                TextButton(onClick = { onDone("") }) {
                    Text(if (step is ConfirmStep.Error) "Fechar" else "Manter pendente")
                }
            }
        },
    )
}

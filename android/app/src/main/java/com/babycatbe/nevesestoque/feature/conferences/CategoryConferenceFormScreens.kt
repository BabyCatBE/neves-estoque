package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

@Composable
fun CategoryConferenceFormRoute(
    categoryId: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm: CategoryConferenceFormViewModel = viewModel(
        key = "category-conference-form-" + categoryId,
        factory = CategoryConferenceFormViewModel.Factory(categoryId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.savedPendingMessage) {
        state.savedPendingMessage?.let {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onSaved(it)
        }
    }
    LaunchedEffect(state.savedConferenceId) {
        state.savedConferenceId?.let {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onSaved("Conferência salva com sucesso.")
        }
    }

    CategoryConferenceFormScreen(
        state = state,
        onBack = onBack,
        onSave = vm::requestSave,
        onConfirmWarnings = vm::confirmConsumptionWarnings,
        onDismissWarnings = vm::dismissConsumptionWarnings,
        onConfirmSameDay = vm::confirmSameDay,
        onDismissSameDay = vm::dismissSameDay,
        onClearError = vm::clearError,
    )
}

@Composable
private fun CategoryConferenceFormScreen(
    state: CategoryConferenceFormUiState,
    onBack: () -> Unit,
    onSave: (String, String, String, Map<String, String>) -> Unit,
    onConfirmWarnings: () -> Unit,
    onDismissWarnings: () -> Unit,
    onConfirmSameDay: () -> Unit,
    onDismissSameDay: () -> Unit,
    onClearError: () -> Unit,
) {
    val setup = state.setup
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var responsible by rememberSaveable { mutableStateOf("") }
    var observation by rememberSaveable { mutableStateOf("") }
    var quantities by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var initialized by remember { mutableStateOf(false) }
    var leaveOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(setup?.categoryId) {
        if (!initialized && setup != null) {
            quantities = setup.products.associate { it.id to "" }
            initialized = true
        }
    }

    val dirty = responsible.isNotBlank() || observation.isNotBlank() ||
        date != LocalDate.now().toString() || quantities.values.any { it.isNotBlank() }
    val busy = state.checking || state.saving

    BackHandler(enabled = busy) {}
    BackHandler(enabled = !busy && dirty) { leaveOpen = true }

    Scaffold(
        topBar = {
            ConferenceTopBar(
                title = "Nova Conferência",
                onBack = { if (dirty) leaveOpen = true else onBack() },
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading) {
                Card { Text("Carregando Categoria…", modifier = Modifier.padding(18.dp)) }
            }
            state.errorMessage?.let { error ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearError) { Text("Fechar") }
                    }
                }
            }

            setup?.let { current ->
                Text(
                    "CONFERÊNCIA POR CATEGORIA",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    current.categoryName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Preencha todos os " + current.products.size +
                        " Produtos. Campo vazio não significa zero.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it; onClearError() },
                            label = { Text("Data *") },
                            placeholder = { Text("AAAA-MM-DD") },
                            isError = state.fieldErrors.date != null,
                            supportingText = state.fieldErrors.date?.let { message -> { Text(message) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = responsible,
                            onValueChange = {
                                if (it.length <= 160) responsible = it
                                onClearError()
                            },
                            label = { Text("Responsável pela contagem física *") },
                            isError = state.fieldErrors.responsible != null,
                            supportingText = state.fieldErrors.responsible?.let { message -> { Text(message) } },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }

                Text(
                    "Contagem física",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                current.products.forEachIndexed { index, product ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "ITEM " + (index + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                product.name,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            Text(
                                "Unidade: " + product.unit,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedTextField(
                                value = quantities[product.id].orEmpty(),
                                onValueChange = { value ->
                                    quantities = quantities + (product.id to value)
                                    onClearError()
                                },
                                label = { Text("Quantidade *") },
                                placeholder = { Text("Zero é permitido") },
                                isError = state.fieldErrors.quantities[product.id] != null,
                                supportingText = state.fieldErrors.quantities[product.id]?.let { message -> { Text(message) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            )
                        }
                    }
                }

                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        OutlinedTextField(
                            value = observation,
                            onValueChange = {
                                if (it.length <= 2000) observation = it
                                onClearError()
                            },
                            label = { Text("Observação") },
                            placeholder = { Text("Observação única para esta Conferência.") },
                            minLines = 3,
                            isError = state.fieldErrors.observation != null,
                            supportingText = state.fieldErrors.observation?.let { message -> { Text(message) } },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Card {
                    Text(
                        "Esta Conferência só será considerada salva depois da confirmação do backend. O Offline Android será tratado em bloco próprio.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                Button(
                    onClick = { onSave(date, responsible, observation, quantities) },
                    enabled = !busy && current.products.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            state.saving -> "Salvando…"
                            state.checking -> "Verificando…"
                            else -> "Salvar Conferência"
                        }
                    )
                }
            }
        }
    }

    if (state.consumptionWarnings.isNotEmpty()) {
        ConsumptionWarningsDialog(
            warnings = state.consumptionWarnings,
            products = setup?.products.orEmpty(),
            onConfirm = onConfirmWarnings,
            onDismiss = onDismissWarnings,
        )
    }

    if (state.sameDayConferences.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissSameDay,
            title = { Text("Já existe Conferência nesta data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Só continue se esta for realmente outra contagem física. Para corrigir uma Conferência anterior, use o Histórico."
                    )
                    state.sameDayConferences.forEach { existing ->
                        Text(
                            "• " + formatConferenceTime(existing.effectiveAt) +
                                " · " + existing.physicalResponsible,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConfirmSameDay) { Text("É outra contagem") }
            },
            dismissButton = {
                TextButton(onClick = onDismissSameDay) { NevesIcon(NevesIcons.Back, "Voltar") }
            },
        )
    }

    if (leaveOpen) {
        AlertDialog(
            onDismissRequest = { leaveOpen = false },
            title = { Text("Descartar Conferência?") },
            text = { Text("Os dados preenchidos nesta tela serão perdidos.") },
            confirmButton = {
                TextButton(onClick = onBack) { Text("Descartar e sair") }
            },
            dismissButton = {
                TextButton(onClick = { leaveOpen = false }) { Text("Continuar") }
            },
        )
    }
}

@Composable
fun EditConferenceRoute(
    conferenceId: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm: EditConferenceViewModel = viewModel(
        key = "edit-conference-" + conferenceId,
        factory = EditConferenceViewModel.Factory(conferenceId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let {
            vm.consumeSavedMessage()
            onSaved(it)
        }
    }

    EditConferenceScreen(
        state = state,
        onBack = onBack,
        onRequestReview = vm::requestReview,
        onConfirmSave = vm::confirmSave,
        onDismissReview = vm::dismissReview,
    )
}

@Composable
private fun EditConferenceScreen(
    state: EditConferenceUiState,
    onBack: () -> Unit,
    onRequestReview: (String, String, String, Map<String, String>) -> Unit,
    onConfirmSave: () -> Unit,
    onDismissReview: () -> Unit,
) {
    val details = state.details
    var date by rememberSaveable { mutableStateOf("") }
    var responsible by rememberSaveable { mutableStateOf("") }
    var observation by rememberSaveable { mutableStateOf("") }
    var quantities by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var initialized by remember { mutableStateOf(false) }
    var leaveOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(details?.id) {
        if (!initialized && details != null) {
            date = conferenceDateInput(details.effectiveAt)
            responsible = details.physicalResponsible
            observation = details.observation.orEmpty()
            quantities = details.items.associate {
                it.productId to formatConferenceNumber(it.quantity)
            }
            initialized = true
        }
    }

    val dirty = details?.let { current ->
        date != conferenceDateInput(current.effectiveAt) ||
            responsible != current.physicalResponsible ||
            observation != current.observation.orEmpty() ||
            current.items.any { quantities[it.productId].orEmpty() != formatConferenceNumber(it.quantity) }
    } ?: false
    val busy = state.checking || state.saving

    BackHandler(enabled = busy) {}
    BackHandler(enabled = !busy && dirty) { leaveOpen = true }

    Scaffold(
        topBar = {
            ConferenceTopBar(
                title = "Corrigir Conferência",
                onBack = { if (dirty) leaveOpen = true else onBack() },
            )
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading) {
                Card { Text("Carregando Conferência…", modifier = Modifier.padding(18.dp)) }
            }
            state.errorMessage?.let {
                Card { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp)) }
            }

            details?.let { current ->
                Text(
                    current.categoryName,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Correção histórica",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "O mesmo registro será corrigido. A auditoria do backend preserva antes/depois.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it },
                            label = { Text("Data *") },
                            placeholder = { Text("AAAA-MM-DD") },
                            isError = state.fieldErrors.date != null,
                            supportingText = state.fieldErrors.date?.let { message -> { Text(message) } },
                            enabled = !busy,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = responsible,
                            onValueChange = { if (it.length <= 160) responsible = it },
                            label = { Text("Responsável pela contagem física *") },
                            isError = state.fieldErrors.responsible != null,
                            supportingText = state.fieldErrors.responsible?.let { message -> { Text(message) } },
                            enabled = !busy,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }

                Text(
                    "Quantidades",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                current.items.forEach { item ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(item.productName, fontWeight = FontWeight.Bold)
                            Text(
                                "Unidade: " + item.unit,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedTextField(
                                value = quantities[item.productId].orEmpty(),
                                onValueChange = { value ->
                                    quantities = quantities + (item.productId to value)
                                },
                                label = { Text("Quantidade *") },
                                isError = state.fieldErrors.quantities[item.productId] != null,
                                supportingText = state.fieldErrors.quantities[item.productId]?.let { message -> { Text(message) } },
                                enabled = !busy,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            )
                        }
                    }
                }

                Card {
                    OutlinedTextField(
                        value = observation,
                        onValueChange = { if (it.length <= 2000) observation = it },
                        label = { Text("Observação") },
                        minLines = 3,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }

                Button(
                    onClick = { onRequestReview(date, responsible, observation, quantities) },
                    enabled = dirty && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.checking) "Verificando…" else "Revisar correção")
                }
            }
        }
    }

    if (state.reviewOpen && details != null) {
        AlertDialog(
            onDismissRequest = onDismissReview,
            title = { Text("Salvar correção?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("O registro manterá o mesmo ID e a auditoria guardará o antes/depois.")
                    if (state.consumptionWarnings.isNotEmpty()) {
                        Text(
                            "Há consumo fora do padrão em " +
                                state.consumptionWarnings.size + " Produto(s).",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                        )
                        state.consumptionWarnings.forEach { warning ->
                            val productName = details.items
                                .firstOrNull { it.productId == warning.productId }
                                ?.productName ?: "Produto"
                            Text("• " + productName + ": " + consumptionWarningText(warning))
                        }
                    } else {
                        Text("Nenhum alerta de consumo atípico foi identificado.")
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConfirmSave, enabled = !state.saving) {
                    Text(if (state.saving) "Salvando…" else "Salvar correção")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReview, enabled = !state.saving) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (leaveOpen) {
        AlertDialog(
            onDismissRequest = { leaveOpen = false },
            title = { Text("Descartar alterações?") },
            text = { Text("As mudanças feitas nesta correção serão perdidas.") },
            confirmButton = { TextButton(onClick = onBack) { Text("Descartar e sair") } },
            dismissButton = { TextButton(onClick = { leaveOpen = false }) { Text("Continuar") } },
        )
    }
}

@Composable
private fun ConsumptionWarningsDialog(
    warnings: List<ConferenceConsumptionWarning>,
    products: List<ConferenceProduct>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revisar consumo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A nova contagem indica consumo fora do padrão histórico. Confirme somente se a contagem física estiver correta."
                )
                warnings.forEach { warning ->
                    val name = products.firstOrNull { it.id == warning.productId }?.name ?: "Produto"
                    Text(
                        "• " + name + ": " + consumptionWarningText(warning),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Contagem está correta") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Revisar valores") }
        },
    )
}

private fun consumptionWarningText(warning: ConferenceConsumptionWarning): String =
    when (warning.kind) {
        ConferenceConsumptionWarningKind.Inconsistent ->
            "a contagem indica aumento de estoque não explicado pelas Entradas."
        ConferenceConsumptionWarningKind.Above ->
            "consumo acima do padrão esperado."
        ConferenceConsumptionWarningKind.Below ->
            "consumo abaixo do padrão esperado."
    }


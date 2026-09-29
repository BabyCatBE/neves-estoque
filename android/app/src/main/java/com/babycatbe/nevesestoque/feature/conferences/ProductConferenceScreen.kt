package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.ui.components.NevesContentCard
import com.babycatbe.nevesestoque.ui.components.NevesRefreshIcon
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babycatbe.nevesestoque.ui.load.RefreshOnKeyChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.babycatbe.nevesestoque.ui.input.NevesNumericField
import com.babycatbe.nevesestoque.ui.input.NevesNumericKeypad
import com.babycatbe.nevesestoque.ui.input.rememberKeepVisibleOnFocus
import com.babycatbe.nevesestoque.ui.input.rememberNumericKeypadState
import com.babycatbe.nevesestoque.ui.input.requestFocusSafely
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductStockUpdateRoute(
    productId: String,
    onBack: () -> Unit,
    onConference: () -> Unit,
    onEntry: () -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val vm: ProductStockUpdateViewModel = viewModel(
        key = "product-stock-update-$productId",
        factory = ProductStockUpdateViewModel.Factory(productId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    RefreshOnKeyChange(refreshKey) { vm.refresh() }

    ProductStockUpdateScreen(
        state = state,
        onBack = onBack,
        onConference = onConference,
        onEntry = onEntry,
        onRefresh = vm::refresh,
        noticeMessage = noticeMessage,
        onDismissNotice = onDismissNotice,
    )
}

@Composable
private fun ProductStockUpdateScreen(
    state: ProductStockUpdateUiState,
    onBack: () -> Unit,
    onConference: () -> Unit,
    onEntry: () -> Unit,
    onRefresh: () -> Unit,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
) {
    val product = state.product

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        "Atualizar estoque",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                        NevesRefreshIcon(state.refreshing)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            noticeMessage?.let { message ->
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

            if (state.loading) {
                NevesContentCard { Text("Carregando Produto…", modifier = Modifier.padding(18.dp)) }
            }

            state.errorMessage?.let { message ->
                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                    }
                }
            }

            if (!state.loading && state.errorMessage == null && product != null) {
                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "PRODUTO",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            product.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "Estoque atual: " + if (product.stockRequiresConference) {
                                "Conferência necessária"
                            } else {
                                formatQuantity(product.currentQuantity, product.unit)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Text(
                    "Escolha como deseja atualizar o estoque.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "Conferência",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Registrar uma nova contagem física e substituir o checkpoint atual deste Produto.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Button(
                            onClick = onConference,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Text("Abrir Conferência")
                        }
                    }
                }

                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "Entrada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Registrar mercadoria recebida para este Produto, já abrindo a Nova Entrada com ele selecionado.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Button(
                            onClick = onEntry,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Text("Registrar Entrada")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductConferenceRoute(
    productId: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm: ProductConferenceViewModel = viewModel(
        key = "product-conference-$productId",
        factory = ProductConferenceViewModel.Factory(productId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var responsible by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var observation by rememberSaveable { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { message ->
            vm.consumeSavedMessage()
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onSaved(message)
        }
    }

    ProductConferenceScreen(
        state = state,
        responsible = responsible,
        quantity = quantity,
        observation = observation,
        onResponsibleChange = { responsible = it },
        onQuantityChange = { quantity = it },
        onObservationChange = { observation = it },
        onBack = onBack,
        onSave = { vm.requestSave(responsible, quantity, observation) },
        onConfirmWarning = vm::confirmWarning,
        onDismissWarning = vm::dismissWarning,
    )
}

@Composable
private fun ProductConferenceScreen(
    state: ProductConferenceUiState,
    responsible: String,
    quantity: String,
    observation: String,
    onResponsibleChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onObservationChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onConfirmWarning: () -> Unit,
    onDismissWarning: () -> Unit,
) {
    val product = state.product
    val responsibleFocus = remember { FocusRequester() }
    val quantityFocus = remember { FocusRequester() }
    val observationFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keypad = rememberNumericKeypadState()
    val busy = state.checking || state.saving
    var localErrors by remember { mutableStateOf(ProductConferenceFormErrors()) }

    // Fluxo rápido: Responsável → Quantidade (teclado do app) → Observação → Salvar.
    LaunchedEffect(state.loading) {
        if (!state.loading && state.product != null) responsibleFocus.requestFocusSafely()
    }
    // Erro ao salvar: volta o foco para o primeiro campo com problema.
    LaunchedEffect(state.fieldErrors) {
        if (state.fieldErrors.responsible != null) {
            if (responsibleFocus.requestFocusSafely()) keyboard?.show()
        } else if (state.fieldErrors.quantity != null) {
            quantityFocus.requestFocusSafely()
        }
    }

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack, enabled = !busy) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        "Conferência do Produto",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                }
            }
        },
        bottomBar = { NevesNumericKeypad(keypad) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading) {
                NevesContentCard { Text("Carregando Produto…", modifier = Modifier.padding(18.dp)) }
            }

            state.errorMessage?.let { message ->
                NevesContentCard {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }

            if (!state.loading && product != null) {
                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "PRODUTO",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            product.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            product.unit,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(
                            "Quantidade atual registrada: " + if (product.stockRequiresConference) {
                                "Conferência necessária"
                            } else {
                                formatQuantity(product.currentQuantity, product.unit)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                val responsibleError = localErrors.responsible ?: state.fieldErrors.responsible
                OutlinedTextField(
                    value = responsible,
                    onValueChange = {
                        onResponsibleChange(it)
                        localErrors = localErrors.copy(responsible = null)
                    },
                    label = { Text("Responsável pela contagem física") },
                    singleLine = true,
                    enabled = !busy,
                    isError = responsibleError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = {
                        val error = conferenceResponsibleError(responsible)
                        if (error != null) localErrors = localErrors.copy(responsible = error)
                        else quantityFocus.requestFocusSafely()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(responsibleFocus)
                        .then(rememberKeepVisibleOnFocus()),
                )
                responsibleError?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                NevesNumericField(
                    value = quantity,
                    onValueChange = {
                        onQuantityChange(it)
                        localErrors = localErrors.copy(quantity = null)
                    },
                    keypad = keypad,
                    label = "Nova quantidade",
                    placeholder = "0",
                    errorMessage = localErrors.quantity ?: state.fieldErrors.quantity,
                    onConfirm = {
                        val error = parseConferenceQuantity(quantity, "Nova quantidade").second
                        if (error != null) {
                            localErrors = localErrors.copy(quantity = error)
                        } else if (observationFocus.requestFocusSafely()) {
                            keyboard?.show()
                        }
                    },
                    confirmLabel = "Próximo",
                    focusRequester = quantityFocus,
                    enabled = !busy,
                    stepper = true,
                    stepSubject = product.name,
                )

                OutlinedTextField(
                    value = observation,
                    onValueChange = onObservationChange,
                    label = { Text("Observação (opcional)") },
                    minLines = 3,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(observationFocus)
                        .then(rememberKeepVisibleOnFocus()),
                )

                Text(
                    "A Conferência será registrada com a data e o horário atuais. A contagem física passa a ser o novo checkpoint deste Produto.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                Button(
                    onClick = onSave,
                    enabled = !busy,
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

                Text(
                    "Offline Android continua fora do escopo atual. A Conferência só é considerada salva após confirmação do backend.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    state.warning?.let { warning ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) onDismissWarning() },
            title = { Text("Há consumo fora do padrão") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Confira a nova quantidade. Se confirmar, este valor será considerado verdadeiro e entrará integralmente nos cálculos futuros."
                    )
                    Text(warningTitle(warning), fontWeight = FontWeight.SemiBold)
                    if (product != null) {
                        Text(
                            "Esperado: " + formatConsumption(warning.expectedConsumption, product.unit) +
                                " · Conferência indica: " + formatConsumption(warning.actualConsumption, product.unit) +
                                " · Diferença: " + formatDifference(warning, product.unit),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmWarning, enabled = !state.saving) {
                    Text(if (state.saving) "Salvando…" else "Confirmar mesmo assim")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissWarning, enabled = !state.saving) {
                    Text("Voltar e conferir")
                }
            },
        )
    }
}

private fun warningTitle(warning: ConferenceConsumptionWarning): String =
    when (warning.kind) {
        ConferenceConsumptionWarningKind.Inconsistent ->
            "A contagem indica aumento de estoque que não é explicado pelas Entradas registradas."
        ConferenceConsumptionWarningKind.Above -> "Consumo muito acima do esperado."
        ConferenceConsumptionWarningKind.Below -> "Consumo muito abaixo do esperado."
    }

private fun formatQuantity(value: Double?, unit: String): String =
    if (value == null) "Sem dados" else formatConsumption(value, unit)

private fun formatConsumption(value: Double, unit: String): String {
    val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply {
        maximumFractionDigits = 2
    }.format(value)
    return "$number $unit"
}

private fun formatDifference(
    warning: ConferenceConsumptionWarning,
    unit: String,
): String {
    val sign = if (warning.difference > 0) "+" else ""
    val amount = sign + formatConsumption(warning.difference, unit)
    val percent = warning.differencePercent ?: return amount
    val formattedPercent = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply {
        maximumFractionDigits = 1
    }.format(percent)
    return "$amount ($sign$formattedPercent%)"
}


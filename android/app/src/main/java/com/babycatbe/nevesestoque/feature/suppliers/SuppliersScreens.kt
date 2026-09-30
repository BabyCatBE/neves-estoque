package com.babycatbe.nevesestoque.feature.suppliers

import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import com.babycatbe.nevesestoque.ui.input.NevesNumericField
import com.babycatbe.nevesestoque.ui.input.NevesNumericKeypad
import com.babycatbe.nevesestoque.ui.input.rememberNumericKeypadState
import com.babycatbe.nevesestoque.ui.input.requestFocusSafely
import com.babycatbe.nevesestoque.ui.components.NevesContentCard
import com.babycatbe.nevesestoque.ui.components.NevesRefreshIcon
import com.babycatbe.nevesestoque.ui.components.NevesActionLabel
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babycatbe.nevesestoque.ui.load.RefreshOnKeyChange
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SuppliersRoute(
    onBack: () -> Unit,
    onSupplierClick: (String) -> Unit,
    onCreateSupplier: () -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
    initialPendingOnly: Boolean = false,
    onTrash: () -> Unit = {},
) {
    val vm: SuppliersViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    RefreshOnKeyChange(refreshKey) { vm.refresh() }
    SuppliersScreen(
        state = state,
        onBack = onBack,
        onSupplierClick = onSupplierClick,
        onCreateSupplier = onCreateSupplier,
        onRefresh = vm::refresh,
        noticeMessage = noticeMessage,
        onDismissNotice = onDismissNotice,
        initialPendingOnly = initialPendingOnly,
        onTrash = onTrash,
    )
}

@Composable
private fun SuppliersScreen(
    state: SuppliersUiState,
    onBack: () -> Unit,
    onSupplierClick: (String) -> Unit,
    onCreateSupplier: () -> Unit,
    onRefresh: () -> Unit,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
    initialPendingOnly: Boolean,
    onTrash: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var pendingOnly by rememberSaveable { mutableStateOf(initialPendingOnly) }

    val filtered = state.suppliers.filter {
        (!pendingOnly || it.isPending) && matchesSupplierSearch(it, search)
    }

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        "Fornecedores",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                        NevesRefreshIcon(state.refreshing)
                    }
                    TextButton(onClick = onCreateSupplier) { Text("Novo") }
                }
            }
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
                        "Cadastre contatos, empresas e parâmetros usados nas Entradas e nas futuras recomendações de compra.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Pesquisar") },
                        leadingIcon = { NevesIcon(NevesIcons.Search) },
                        placeholder = { Text("Contato, empresa ou telefone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { pendingOnly = !pendingOnly }, modifier = Modifier.weight(1f)) {
                            Text(if (pendingOnly) "Mostrando pendentes · Ver todos" else "Mostrar cadastros pendentes")
                        }
                        OutlinedButton(onClick = onTrash) { NevesActionLabel("Lixeira", NevesIcons.Trash) }
                    }
                }
            }

            item {
                NevesContentCard {
                    Text(
                        "Próxima compra recomendada continuará como “Aguardando Entradas” até existir histórico real suficiente. A sugestão de compra fica em Compras → Por fornecedor.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    )
                }
            }

            if (state.loading) {
                item { NevesContentCard { Text("Carregando Fornecedores…", modifier = Modifier.padding(18.dp)) } }
            }

            state.errorMessage?.let { error ->
                item {
                    NevesContentCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                        }
                    }
                }
            }

            if (!state.loading && state.errorMessage == null && filtered.isEmpty()) {
                item {
                    NevesContentCard {
                        Text(
                            when {
                                search.isNotBlank() -> "Nenhum Fornecedor encontrado."
                                pendingOnly -> "Nenhum cadastro pendente."
                                else -> "Nenhum Fornecedor cadastrado."
                            },
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                        )
                    }
                }
            }

            items(filtered, key = { it.id }) { supplier ->
                NevesContentCard(onClick = { onSupplierClick(supplier.id) }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "CONTATO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    supplier.name,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            if (supplier.isPending) {
                                Text(
                                    "Pendente",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        SupplierInfoLine("Empresa", supplier.company ?: "Não informada")
                        SupplierInfoLine("Telefone", formatSupplierPhoneDisplay(supplier.phone))
                        SupplierInfoLine("Próxima compra", "Aguardando Entradas")
                    }
                }
            }

            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp)) }
        }
    }
}

@Composable
fun SupplierDetailRoute(
    supplierId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: (String) -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val vm: SupplierDetailViewModel = viewModel(
        key = "supplier-detail-${supplierId}",
        factory = SupplierDetailViewModel.Factory(supplierId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    RefreshOnKeyChange(refreshKey) { vm.refresh() }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            onDeleted("Fornecedor enviado para a Lixeira. O histórico de Entradas foi preservado.")
        }
    }
    SupplierDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = vm::deleteSupplier,
        onRefresh = vm::refresh,
        noticeMessage = noticeMessage,
        onDismissNotice = onDismissNotice,
    )
}

@Composable
private fun SupplierDetailScreen(
    state: SupplierDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
) {
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    val supplier = state.supplier

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack, enabled = !state.deleting) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        "Fornecedor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                    TextButton(
                        onClick = onEdit,
                        enabled = supplier != null && !state.loading && !state.deleting,
                    ) { NevesActionLabel("Editar", NevesIcons.Edit) }
                    TextButton(
                        onClick = onRefresh,
                        enabled = !state.refreshing && !state.deleting,
                    ) { NevesRefreshIcon(state.refreshing) }
                }
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
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
                NevesContentCard { Text("Carregando Fornecedor…", modifier = Modifier.padding(18.dp)) }
            }

            state.errorMessage?.let { error ->
                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                    }
                }
            }

            state.actionError?.let { error ->
                NevesContentCard {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }

            if (!state.loading && state.errorMessage == null && supplier != null) {
                Column(Modifier.padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "CADASTRO DE FORNECEDOR",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (supplier.isPending) {
                            Text(
                                "Cadastro pendente",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Text(
                        supplier.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        supplier.company ?: "Empresa não informada",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (supplier.isPending) {
                    NevesContentCard {
                        Text(
                            "Este Fornecedor veio de um cadastro rápido ou está incompleto. Preencha Empresa e Telefone para concluir o cadastro.",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SupplierMetricCard(
                        "Empresa",
                        supplier.company ?: "Não informada",
                        Modifier.weight(1f),
                    )
                    SupplierMetricCard(
                        "Telefone",
                        formatSupplierPhoneDisplay(supplier.phone),
                        Modifier.weight(1f),
                    )
                }

                SupplierMetricCard(
                    "Próxima compra",
                    "Aguardando Entradas",
                    Modifier.fillMaxWidth(),
                )

                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Configuração de compra", fontWeight = FontWeight.Bold)
                        SupplierInfoLine(
                            "Frequência de compra",
                            supplier.purchaseFrequencyDays?.let { "A cada $it dias" } ?: "Não informada",
                        )
                        SupplierInfoLine(
                            "Dia preferencial",
                            supplierWeekdayLabel(supplier.preferredOrderWeekday),
                        )
                        SupplierInfoLine(
                            "Prazo de entrega",
                            formatSupplierDays(supplier.averageDeliveryDays),
                        )
                        SupplierInfoLine(
                            "Margem de segurança",
                            formatSupplierDays(supplier.safetyMarginDays),
                        )
                        SupplierInfoLine(
                            "Observação",
                            supplier.observation ?: "Sem observação",
                        )
                    }
                }

                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Lixeira", fontWeight = FontWeight.Bold)
                        Text(
                            "Excluir remove o Fornecedor do cadastro ativo, preserva o histórico de Entradas e mantém restauração por 7 dias na Lixeira do sistema.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        TextButton(
                            onClick = { deleteOpen = true },
                            enabled = !state.deleting,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Text(if (state.deleting) "Excluindo…" else "Excluir Fornecedor")
                        }
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 10.dp))
        }
    }

    BackHandler(enabled = state.deleting) {}

    if (deleteOpen && supplier != null) {
        AlertDialog(
            onDismissRequest = { if (!state.deleting) deleteOpen = false },
            title = { Text("Excluir Fornecedor?") },
            text = {
                Text(
                    "Excluir “${supplier.name}”? Ele sairá do cadastro ativo, mas o histórico de Entradas continuará preservado. Poderá ser restaurado por 7 dias."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteOpen = false
                        onDelete()
                    },
                    enabled = !state.deleting,
                ) { NevesActionLabel("Excluir Fornecedor", NevesIcons.Trash) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteOpen = false },
                    enabled = !state.deleting,
                ) { Text("Cancelar") }
            },
        )
    }
}

@Composable
fun SupplierFormRoute(
    supplierId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val vm: SupplierFormViewModel = viewModel(
        key = "supplier-form-${supplierId ?: "new"}",
        factory = SupplierFormViewModel.Factory(supplierId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    SupplierFormScreen(
        supplierId = supplierId,
        state = state,
        onBack = onBack,
        onSave = vm::save,
    )

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let { message ->
            vm.consumeSavedMessage()
            onSaved(message)
        }
    }
}

@Composable
private fun SupplierFormScreen(
    supplierId: String?,
    state: SupplierFormUiState,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String) -> Unit,
) {
    val keypad = rememberNumericKeypadState()
    val focusManager = LocalFocusManager.current
    val deliveryFocus = remember { FocusRequester() }
    val marginFocus = remember { FocusRequester() }
    val observationFocus = remember { FocusRequester() }
    val editing = supplierId != null
    var initialized by rememberSaveable(supplierId) { mutableStateOf(false) }
    var initialSignature by rememberSaveable(supplierId) { mutableStateOf("") }
    var name by rememberSaveable(supplierId) { mutableStateOf("") }
    var company by rememberSaveable(supplierId) { mutableStateOf("") }
    var phone by rememberSaveable(supplierId) { mutableStateOf("") }
    var observation by rememberSaveable(supplierId) { mutableStateOf("") }
    var purchaseFrequencyDays by rememberSaveable(supplierId) { mutableStateOf("") }
    var preferredOrderWeekday by rememberSaveable(supplierId) { mutableStateOf("") }
    var averageDeliveryDays by rememberSaveable(supplierId) { mutableStateOf("") }
    var safetyMarginDays by rememberSaveable(supplierId) { mutableStateOf("") }
    var weekdayMenuOpen by rememberSaveable { mutableStateOf(false) }
    var exitReviewOpen by rememberSaveable { mutableStateOf(false) }

    fun signature(): String = listOf(
        name,
        company,
        phone,
        observation,
        purchaseFrequencyDays,
        preferredOrderWeekday,
        averageDeliveryDays,
        safetyMarginDays,
    ).joinToString("\u0000")

    LaunchedEffect(state.loading, state.supplier?.id) {
        if (!state.loading && !initialized) {
            state.supplier?.let { supplier ->
                name = supplier.name
                company = supplier.company.orEmpty()
                phone = formatSupplierPhoneInput(supplier.phone.orEmpty())
                observation = supplier.observation.orEmpty()
                purchaseFrequencyDays = supplier.purchaseFrequencyDays?.toString().orEmpty()
                preferredOrderWeekday = supplier.preferredOrderWeekday?.toString().orEmpty()
                averageDeliveryDays = supplier.averageDeliveryDays?.toString().orEmpty()
                safetyMarginDays = supplier.safetyMarginDays?.toString().orEmpty()
            }
            initialSignature = signature()
            initialized = true
        }
    }

    val dirty = initialized && signature() != initialSignature

    fun submit() {
        focusManager.clearFocus()
        onSave(
            name,
            company,
            phone,
            observation,
            purchaseFrequencyDays,
            preferredOrderWeekday,
            averageDeliveryDays,
            safetyMarginDays,
        )
    }

    fun requestBack() {
        if (state.saving) return
        if (dirty) exitReviewOpen = true else onBack()
    }

    BackHandler(enabled = state.saving) {}
    BackHandler(enabled = !state.saving && dirty) { exitReviewOpen = true }

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = ::requestBack, enabled = !state.saving) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        if (editing) "Editar Fornecedor" else "Novo Fornecedor",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading) {
                NevesContentCard { Text("Carregando cadastro…", modifier = Modifier.padding(18.dp)) }
                return@Column
            }

            state.errorMessage?.let {
                NevesContentCard {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }

            if (state.errorMessage == null) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contato / vendedor") },
                    placeholder = { Text("Ex.: João") },
                    supportingText = state.fieldErrors.name?.let { { Text(it) } },
                    isError = state.fieldErrors.name != null,
                    enabled = !state.saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Empresa") },
                    placeholder = { Text("Ex.: Distribuidora Silva") },
                    supportingText = state.fieldErrors.company?.let { { Text(it) } },
                    isError = state.fieldErrors.company != null,
                    enabled = !state.saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = formatSupplierPhoneInput(it) },
                    label = { Text("Telefone") },
                    placeholder = { Text("(75) 9 9999-9999") },
                    supportingText = state.fieldErrors.phone?.let { { Text(it) } },
                    isError = state.fieldErrors.phone != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !state.saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                NevesContentCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Configuração de compra", fontWeight = FontWeight.Bold)

                        NevesNumericField(
                            value = purchaseFrequencyDays,
                            onValueChange = { purchaseFrequencyDays = it },
                            keypad = keypad,
                            label = "Frequência de compra (dias)",
                            placeholder = "Ex.: 7",
                            errorMessage = state.fieldErrors.purchaseFrequencyDays,
                            allowDecimal = false,
                            onConfirm = { deliveryFocus.requestFocusSafely() },
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )

                        Text("Dia preferencial", modifier = Modifier.padding(top = 10.dp))
                        Box {
                            OutlinedButton(
                                onClick = { weekdayMenuOpen = true },
                                enabled = !state.saving,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    preferredOrderWeekday.toIntOrNull()
                                        ?.let { supplierWeekdayLabel(it) }
                                        ?: "Não informado"
                                )
                            }
                            DropdownMenu(
                                expanded = weekdayMenuOpen,
                                onDismissRequest = { weekdayMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Não informado") },
                                    onClick = {
                                        preferredOrderWeekday = ""
                                        weekdayMenuOpen = false
                                    },
                                )
                                SUPPLIER_WEEKDAYS.forEach { day ->
                                    DropdownMenuItem(
                                        text = { Text(day.label) },
                                        onClick = {
                                            preferredOrderWeekday = day.value.toString()
                                            weekdayMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        state.fieldErrors.preferredOrderWeekday?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        NevesNumericField(
                            value = averageDeliveryDays,
                            onValueChange = { averageDeliveryDays = it },
                            keypad = keypad,
                            label = "Prazo de entrega (dias)",
                            placeholder = "Ex.: 2",
                            errorMessage = state.fieldErrors.averageDeliveryDays,
                            allowDecimal = false,
                            focusRequester = deliveryFocus,
                            onConfirm = { marginFocus.requestFocusSafely() },
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        NevesNumericField(
                            value = safetyMarginDays,
                            onValueChange = { safetyMarginDays = it },
                            keypad = keypad,
                            label = "Margem de segurança (dias)",
                            placeholder = "Ex.: 1",
                            errorMessage = state.fieldErrors.safetyMarginDays,
                            allowDecimal = false,
                            focusRequester = marginFocus,
                            onConfirm = { observationFocus.requestFocusSafely() },
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        OutlinedTextField(
                            value = observation,
                            onValueChange = { observation = it },
                            label = { Text("Observação (opcional)") },
                            placeholder = { Text("Informações úteis sobre atendimento, pedido ou entrega.") },
                            supportingText = state.fieldErrors.observation?.let { { Text(it) } },
                            isError = state.fieldErrors.observation != null,
                            enabled = !state.saving,
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(observationFocus),
                        )
                    }
                }

                NevesContentCard {
                    Text(
                        "Empresa e telefone são obrigatórios no cadastro completo. O cadastro rápido com apenas o nome será tratado no bloco de Entradas e ficará marcado como pendente.",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    )
                }

                Button(
                    onClick = ::submit,
                    enabled = !state.saving && (!editing || dirty),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.saving) "Salvando…" else "Salvar Fornecedor")
                }
            }
        }
    }

    if (exitReviewOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.saving) exitReviewOpen = false },
            title = { Text("Alterações não salvas") },
            text = {
                Text("Salve, descarte ou continue editando antes de sair deste cadastro.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        exitReviewOpen = false
                        submit()
                    },
                    enabled = !state.saving,
                ) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { exitReviewOpen = false },
                        enabled = !state.saving,
                    ) { Text("Continuar") }
                    TextButton(
                        onClick = {
                            exitReviewOpen = false
                            onBack()
                        },
                        enabled = !state.saving,
                    ) { Text("Descartar") }
                }
            },
        )
    }
}

@Composable
private fun SupplierMetricCard(
    label: String,
    value: String,
    modifier: Modifier,
) {
    NevesContentCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                label.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(value, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun SupplierInfoLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

private fun formatSupplierDays(value: Int?): String =
    when (value) {
        null -> "Não informado"
        1 -> "1 dia"
        else -> "$value dias"
    }


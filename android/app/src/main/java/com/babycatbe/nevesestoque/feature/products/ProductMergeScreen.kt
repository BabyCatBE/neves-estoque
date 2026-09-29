package com.babycatbe.nevesestoque.feature.products

import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesTopBarSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProductMergeRoute(
    productId: String,
    onBack: () -> Unit,
    onMerged: (String, String) -> Unit,
) {
    val vm: ProductMergeViewModel = viewModel(
        key = "product-merge-" + productId,
        factory = ProductMergeViewModel.Factory(productId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.mergedResult) {
        val result = state.mergedResult ?: return@LaunchedEffect
        onMerged(
            result.survivorProductId,
            if (result.reconciledAfterAmbiguousFailure) {
                "Mescla confirmada após reconciliação com o servidor. Faça uma nova Conferência física."
            } else {
                "Produtos mesclados com sucesso. Faça uma nova Conferência física."
            },
        )
    }

    ProductMergeScreen(
        state = state,
        onBack = onBack,
        onSelectCandidate = vm::selectCandidate,
        onClearCandidate = vm::clearCandidate,
        onMerge = vm::merge,
    )
}

@Composable
private fun ProductMergeScreen(
    state: ProductMergeUiState,
    onBack: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onClearCandidate: () -> Unit,
    onMerge: (ProductMergeDraft) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf(false) }
    var finalName by rememberSaveable { mutableStateOf("") }
    var finalCategoryId by rememberSaveable { mutableStateOf("") }
    var finalUnit by rememberSaveable { mutableStateOf("") }
    var survivorEquivalentQuantity by rememberSaveable { mutableStateOf("") }
    var absorbedEquivalentQuantity by rememberSaveable { mutableStateOf("") }
    var priceSourceWire by rememberSaveable { mutableStateOf(MergeInitialPriceSource.None.wireValue) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryMenuOpen by rememberSaveable { mutableStateOf(false) }
    var unitMenuOpen by rememberSaveable { mutableStateOf(false) }
    var priceMenuOpen by rememberSaveable { mutableStateOf(false) }

    val source = state.source
    val candidate = state.candidate
    val pair = if (source != null && candidate != null) determineMergePair(source, candidate) else null
    val catalog = state.catalog

    BackHandler(enabled = state.merging) {
        // A mescla é transacional e não deve ser abandonada enquanto o backend responde.
    }

    LaunchedEffect(candidate?.id) {
        val activePair = pair
        if (activePair != null) {
            finalName = activePair.survivor.name
            finalCategoryId = activePair.survivor.categoryId.orEmpty()
            finalUnit = activePair.survivor.unit
            survivorEquivalentQuantity = ""
            absorbedEquivalentQuantity = ""
            priceSourceWire = defaultMergeInitialPriceSource(activePair).wireValue
            formError = null
            preview = false
            search = ""
        }
    }

    fun priceSource(): MergeInitialPriceSource =
        MergeInitialPriceSource.entries.firstOrNull { it.wireValue == priceSourceWire }
            ?: MergeInitialPriceSource.None

    fun validate(): ProductMergeDraft? {
        val activePair = pair ?: return null
        val validated = validateProductMergeDraft(
            pair = activePair,
            finalName = finalName,
            finalCategoryId = finalCategoryId,
            finalUnit = finalUnit,
            survivorEquivalentQuantity = survivorEquivalentQuantity,
            absorbedEquivalentQuantity = absorbedEquivalentQuantity,
            initialPriceSource = priceSource(),
        )
        formError = validated.error
        if (validated.draft != null) {
            finalName = validated.draft.finalName
        }
        return validated.draft
    }

    Scaffold(
        topBar = {
            NevesTopBarSurface {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    TextButton(onClick = onBack, enabled = !state.merging) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        "Mesclar Produtos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                }
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading) {
                Card { Text("Carregando Produtos…", modifier = Modifier.padding(18.dp)) }
            }

            state.errorMessage?.let {
                Card {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            state.actionError?.let {
                Card {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }

            if (!state.loading && state.errorMessage == null && source != null && catalog != null) {
                ProductMergeIdentityCard(
                    label = "Produto atual",
                    product = source,
                    helper = "Escolha abaixo o cadastro duplicado.",
                )

                if (pair == null) {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Escolha o cadastro duplicado", fontWeight = FontWeight.Bold)
                            Text(
                                "O cadastro mais antigo sempre será o ID que permanece.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                label = { Text("Pesquisar Produto") },
                        leadingIcon = { NevesIcon(NevesIcons.Search) },
                                placeholder = { Text("Digite o nome do Produto duplicado") },
                                singleLine = true,
                                enabled = !state.selectingCandidate,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )

                            val candidates = catalog.products
                                .filter {
                                    it.id != source.id &&
                                        it.categoryId != null &&
                                        matchesMergeSearch(it.name, search)
                                }
                                .sortedBy { it.name.lowercase(Locale.forLanguageTag("pt-BR")) }

                            if (state.selectingCandidate) {
                                Text(
                                    "Carregando Produto duplicado…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            } else if (candidates.isEmpty()) {
                                Text(
                                    "Nenhum outro Produto ativo encontrado.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            } else {
                                candidates.forEach { product ->
                                    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                        TextButton(
                                            onClick = { onSelectCandidate(product.id) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(Modifier.fillMaxWidth()) {
                                                Text(product.name, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    product.unit + " · criado em " + formatMergeDate(product.createdAt),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!preview) {
                    ProductMergeIdentityCard(
                        label = "ID que permanece",
                        product = pair.survivor,
                        helper = "Cadastro mais antigo",
                    )
                    ProductMergeIdentityCard(
                        label = "ID que será absorvido",
                        product = pair.absorbed,
                        helper = "Cadastro mais novo · não vai para a Lixeira",
                    )

                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("Dados finais visíveis", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Escolha como o Produto único ficará depois da mescla.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        formError = null
                                        onClearCandidate()
                                    },
                                    enabled = !state.merging,
                                ) { Text("Trocar duplicado") }
                            }

                            OutlinedTextField(
                                value = finalName,
                                onValueChange = { finalName = it; formError = null },
                                label = { Text("Nome final") },
                                enabled = !state.merging,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )

                            Text("Categoria final", modifier = Modifier.padding(top = 12.dp))
                            Box {
                                TextButton(
                                    onClick = { categoryMenuOpen = true },
                                    enabled = !state.merging,
                                ) {
                                    Text(
                                        catalog.categories.firstOrNull { it.id == finalCategoryId }?.name
                                            ?: "Selecionar…"
                                    )
                                }
                                DropdownMenu(
                                    expanded = categoryMenuOpen,
                                    onDismissRequest = { categoryMenuOpen = false },
                                ) {
                                    catalog.categories
                                        .sortedBy { it.name.lowercase(Locale.forLanguageTag("pt-BR")) }
                                        .forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category.name) },
                                                onClick = {
                                                    finalCategoryId = category.id
                                                    categoryMenuOpen = false
                                                    formError = null
                                                },
                                            )
                                        }
                                }
                            }

                            Text("Unidade final", modifier = Modifier.padding(top = 8.dp))
                            Box {
                                TextButton(
                                    onClick = { unitMenuOpen = true },
                                    enabled = !state.merging,
                                ) { Text(finalUnit.ifBlank { "Selecionar…" }) }
                                DropdownMenu(
                                    expanded = unitMenuOpen,
                                    onDismissRequest = { unitMenuOpen = false },
                                ) {
                                    listOf(pair.survivor.unit, pair.absorbed.unit)
                                        .distinct()
                                        .forEach { unit ->
                                            DropdownMenuItem(
                                                text = { Text(unit) },
                                                onClick = {
                                                    finalUnit = unit
                                                    unitMenuOpen = false
                                                    formError = null
                                                },
                                            )
                                        }
                                }
                            }

                            Text("Referência inicial de preço", modifier = Modifier.padding(top = 8.dp))
                            Box {
                                TextButton(
                                    onClick = { priceMenuOpen = true },
                                    enabled = !state.merging,
                                ) {
                                    Text(priceSourceLabel(priceSource(), pair))
                                }
                                DropdownMenu(
                                    expanded = priceMenuOpen,
                                    onDismissRequest = { priceMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Nenhuma") },
                                        onClick = {
                                            priceSourceWire = MergeInitialPriceSource.None.wireValue
                                            priceMenuOpen = false
                                        },
                                    )
                                    if (pair.survivor.initialPrice != null) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Cadastro mais antigo · " +
                                                        formatMergeMoney(pair.survivor.initialPrice)
                                                )
                                            },
                                            onClick = {
                                                priceSourceWire = MergeInitialPriceSource.Survivor.wireValue
                                                priceMenuOpen = false
                                            },
                                        )
                                    }
                                    if (pair.absorbed.initialPrice != null) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Cadastro mais novo · " +
                                                        formatMergeMoney(pair.absorbed.initialPrice)
                                                )
                                            },
                                            onClick = {
                                                priceSourceWire = MergeInitialPriceSource.Absorbed.wireValue
                                                priceMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                            Text(
                                "Essa referência só é usada se não existir preço real válido no histórico de Entradas.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )

                            if (pair.survivor.unit != pair.absorbed.unit) {
                                Text(
                                    "Conversão obrigatória de unidade",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 14.dp),
                                )
                                Text(
                                    "Informe uma equivalência real entre as duas unidades.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = survivorEquivalentQuantity,
                                    onValueChange = {
                                        survivorEquivalentQuantity = it
                                        formError = null
                                    },
                                    label = { Text("Quantidade em " + pair.survivor.unit) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    enabled = !state.merging,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                                OutlinedTextField(
                                    value = absorbedEquivalentQuantity,
                                    onValueChange = {
                                        absorbedEquivalentQuantity = it
                                        formError = null
                                    },
                                    label = { Text("Quantidade em " + pair.absorbed.unit) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    enabled = !state.merging,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            } else {
                                Text(
                                    "Os dois cadastros já usam a mesma unidade: " + pair.survivor.unit + ".",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }

                            Text(
                                "Estoque inicial e preço inicial do cadastro absorvido não são somados nem viram eventos artificiais. Eles ficam preservados no backup. O estoque final exigirá nova Conferência física.",
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 14.dp),
                            )

                            formError?.let { MergeErrorText(it) }
                            Button(
                                onClick = {
                                    if (validate() != null) preview = true
                                },
                                enabled = !state.merging,
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            ) {
                                Text("Ver prévia da mescla")
                            }
                        }
                    }
                } else {
                    val previewDraft = validateProductMergeDraft(
                        pair = pair,
                        finalName = finalName,
                        finalCategoryId = finalCategoryId,
                        finalUnit = finalUnit,
                        survivorEquivalentQuantity = survivorEquivalentQuantity,
                        absorbedEquivalentQuantity = absorbedEquivalentQuantity,
                        initialPriceSource = priceSource(),
                    ).draft
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "CONFIRMAÇÃO FINAL",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Prévia da mescla",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )

                            MergePreviewLine("Produto final", finalName)
                            MergePreviewLine(
                                "Categoria final",
                                catalog.categories.firstOrNull { it.id == finalCategoryId }?.name
                                    ?: "—",
                            )
                            MergePreviewLine("Unidade final", finalUnit)
                            MergePreviewLine(
                                "ID que permanece",
                                pair.survivor.name + " · cadastro mais antigo",
                            )

                            if (pair.survivor.unit != pair.absorbed.unit && previewDraft != null) {
                                Text(
                                    "Equivalência: " +
                                        formatMergeDecimal(previewDraft.survivorEquivalentQuantity ?: 0.0) +
                                        " " + pair.survivor.unit + " = " +
                                        formatMergeDecimal(previewDraft.absorbedEquivalentQuantity ?: 0.0) +
                                        " " + pair.absorbed.unit +
                                        ". Todo o histórico será convertido para " + finalUnit + ".",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }

                            Text(
                                "Depois de confirmar, Entradas, preços e Conferências dos dois IDs passam para o cadastro mais antigo. O cadastro mais novo é absorvido sem ir para a Lixeira. O estoque atual fica como Conferência necessária até nova contagem física.",
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                            Text(
                                "A ação não possui desfazer pela interface. Antes de alterar os dados, o banco salva backup completo dos dois cadastros e do histórico afetado.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 10.dp),
                            )

                            formError?.let { MergeErrorText(it) }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        preview = false
                                        formError = null
                                    },
                                    enabled = !state.merging,
                                    modifier = Modifier.weight(1f),
                                ) { NevesIcon(NevesIcons.Back, "Voltar") }
                                Button(
                                    onClick = {
                                        val confirmed = validate()
                                        if (confirmed != null) onMerge(confirmed)
                                    },
                                    enabled = !state.merging,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (state.merging) "Mesclando…" else "Confirmar mescla")
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun ProductMergeIdentityCard(
    label: String,
    product: ProductDetails,
    helper: String,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                label.uppercase(Locale.forLanguageTag("pt-BR")),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(product.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text(
                product.unit + " · criado em " + formatMergeDate(product.createdAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MergePreviewLine(label: String, value: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MergeErrorText(value: String) {
    Text(
        value,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 10.dp),
    )
}

private fun priceSourceLabel(
    source: MergeInitialPriceSource,
    pair: ProductMergePair,
): String =
    when (source) {
        MergeInitialPriceSource.None -> "Nenhuma"
        MergeInitialPriceSource.Survivor ->
            "Cadastro mais antigo · " + (pair.survivor.initialPrice?.let(::formatMergeMoney) ?: "Sem preço")
        MergeInitialPriceSource.Absorbed ->
            "Cadastro mais novo · " + (pair.absorbed.initialPrice?.let(::formatMergeMoney) ?: "Sem preço")
    }

private fun formatMergeDate(value: String): String =
    runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(value)

private fun formatMergeMoney(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)

private fun formatMergeDecimal(value: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply {
        maximumFractionDigits = 8
    }.format(value)


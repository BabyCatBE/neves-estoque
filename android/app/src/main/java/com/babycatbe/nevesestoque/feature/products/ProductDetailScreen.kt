package com.babycatbe.nevesestoque.feature.products

import com.babycatbe.nevesestoque.ui.components.NevesStatusMessage
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProductDetailRoute(
    productId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onUpdateStock: () -> Unit,
    refreshKey: Long = 0L,
    noticeMessage: String? = null,
    onDismissNotice: () -> Unit = {},
) {
    val vm: ProductDetailViewModel = viewModel(
        key = "product-detail-$productId",
        factory = ProductDetailViewModel.Factory(productId),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    RefreshOnKeyChange(refreshKey) { vm.refresh() }
    BackHandler(onBack = onBack)
    ProductDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onUpdateStock = onUpdateStock,
        onRefresh = vm::refresh,
        noticeMessage = noticeMessage,
        onDismissNotice = onDismissNotice,
    )
}

@Composable
private fun ProductDetailScreen(
    state: ProductDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onUpdateStock: () -> Unit,
    onRefresh: () -> Unit,
    noticeMessage: String?,
    onDismissNotice: () -> Unit,
) {
    val product = state.product
    val categoryName = state.categories.firstOrNull { it.id == product?.categoryId }?.name ?: "Sem categoria"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            NevesTopBarSurface {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { NevesIcon(NevesIcons.Back, "Voltar") }
                    Text(
                        "Produto",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onEdit,
                        enabled = product != null && !state.loading && !state.convertingUnit,
                    ) { NevesActionLabel("Editar", NevesIcons.Edit) }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.refreshing && !state.convertingUnit,
                    ) {
                        NevesRefreshIcon(state.refreshing)
                    }
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
                    Card {
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
                item { Card { Text("Carregando Produto…", modifier = Modifier.padding(18.dp)) } }
            }

            state.errorMessage?.let { error ->
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                        }
                    }
                }
            }

            state.actionError?.let { error ->
                item {
                    Card {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }

            if (!state.loading && state.errorMessage == null && product != null) {
                item {
                    Column(Modifier.padding(top = 16.dp)) {
                        ProductImagePlaceholder()
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
                        Text(
                            "CADASTRO DE PRODUTO",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            product.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "$categoryName · ${product.unit}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                if (product.stockRequiresConference) {
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Conferência física necessária", fontWeight = FontWeight.Bold)
                                Text(
                                    "Este Produto foi mesclado. O estoque só volta a ser considerado confiável após uma nova Conferência física.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProductMetricCard(
                            "Estoque atual",
                            if (product.stockRequiresConference) "Conferência necessária"
                            else formatQuantity(product.currentQuantity, product.unit),
                            Modifier.weight(1f),
                        )
                        ProductMetricCard(
                            "Preço atual",
                            product.currentPrice?.let(::formatMoney) ?: "Sem preço",
                            Modifier.weight(1f),
                        )
                    }
                }

                item {
                    ProductMetricCard(
                        "Valor atual",
                        when {
                            product.stockRequiresConference -> "Conferência necessária"
                            product.currentValue != null -> formatMoney(product.currentValue)
                            else -> "Sem dados"
                        },
                        Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Atualizar estoque", fontWeight = FontWeight.Bold)
                            Text(
                                "Registre uma nova contagem física deste Produto.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            TextButton(
                                onClick = onUpdateStock,
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                Text("Atualizar estoque")
                            }
                        }
                    }
                }

                item {
                    Column {
                        HorizontalDivider()
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                            Text("Dados do Produto", fontWeight = FontWeight.Bold)
                            DetailLine("Categoria", categoryName)
                            DetailLine("Unidade", product.unit)
                            if (product.categoryId == null) DetailLine("Status", "Cadastro pendente")
                            product.initialStockQuantity?.let {
                                DetailLine("Estoque inicial", formatQuantity(it, product.unit))
                            }
                            product.initialPrice?.let {
                                DetailLine("Preço inicial", formatMoney(it))
                            }
                        }
                    }
                }

                item {
                    UsageCard(product.usageInsights, product.unit)
                }

                item {
                    Text("Histórico de preços", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (product.initialPrice != null) {
                    item {
                        Column {
                            HorizontalDivider()
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Text("Referência inicial", fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(
                                        formatMoney(product.initialPrice),
                                        product.initialPriceAt?.let(::formatDate),
                                    ).joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                if (product.priceHistory.isEmpty() && product.initialPrice == null) {
                    item { Card { NevesStatusMessage("Nenhum preço registrado até agora.") } }
                } else {
                    items(product.priceHistory, key = { it.id }) { price ->
                        Column {
                            HorizontalDivider()
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Text(
                                    when {
                                        price.unitPrice == null -> "Preço não informado"
                                        price.unitPrice == 0.0 -> "Bonificação"
                                        else -> formatMoney(price.unitPrice)
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${formatDate(price.effectiveAt)} · ${price.supplierName}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                                Text(
                                    formatQuantity(price.quantity, product.unit),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                                if (price.unitPrice == null || price.unitPrice == 0.0) {
                                    Text(
                                        if (price.unitPrice == null)
                                            "Não altera a referência de preço atual."
                                        else "Bonificação não altera a referência de preço atual.",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }


            }

            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp)) }
        }
    }

}

@Composable
private fun ProductMetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun UsageCard(insights: ProductUsageInsights, unit: String) {
    Column {
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Text("Duração do estoque", fontWeight = FontWeight.Bold)

            if (insights.status == UsageStatus.Ready) {
                DetailLine("Consumo médio/dia", formatAverage(insights.dailyAverage, unit))
                DetailLine("Consumo médio/semana", formatAverage(insights.weeklyAverage, unit))
                DetailLine("Cobertura estimada", formatCoverage(insights.coverageDays))
                Text(
                    "Baseado nas Conferências válidas e Entradas do histórico recente, com maior peso para o período mais recente.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                Text(
                    usageInsufficientMessage(insights),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun usageInsufficientMessage(insights: ProductUsageInsights): String =
    when (insights.reason) {
        UsageReason.NeedsTwoConferences -> "São necessárias pelo menos duas Conferências válidas para começar o cálculo."
        UsageReason.InvalidInterval -> "As Conferências disponíveis ainda não formam um intervalo válido."
        UsageReason.NegativeConsumption -> "O histórico recente possui intervalo incompatível com um consumo confiável."
        UsageReason.NeedsMoreHistory -> "Ainda não há histórico suficiente para uma estimativa confiável."
        null -> "Dados insuficientes para calcular."
    }

private fun formatAverage(value: Double?, unit: String): String =
    if (value == null) "Sem dados"
    else "${NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply { maximumFractionDigits = 2 }.format(value)} $unit"

private fun formatCoverage(days: Double?): String =
    if (days == null) "Sem estimativa"
    else "${NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply { maximumFractionDigits = 1 }.format(days)} dias"

private fun formatQuantity(value: Double?, unit: String): String {
    if (value == null) return "Sem dados"
    val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply { maximumFractionDigits = 2 }.format(value)
    return "$number $unit"
}

private fun formatMoney(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)

private fun formatDate(value: String): String =
    runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(value)


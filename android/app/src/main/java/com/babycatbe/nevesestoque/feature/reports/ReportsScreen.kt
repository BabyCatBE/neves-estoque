package com.babycatbe.nevesestoque.feature.reports

import com.babycatbe.nevesestoque.ui.load.LatestLoad
import com.babycatbe.nevesestoque.ui.load.loadCatching
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val series: MonthlyStockValueSeries? = null,
    val errorMessage: String? = null,
)

class ReportsViewModel : ViewModel() {
    private val repository = ReportsRepository()
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()
    private val latestLoad = LatestLoad()

    init {
        refresh()
    }

    fun refresh() {
        latestLoad.launch(viewModelScope) {
            val hasData = _uiState.value.series != null
            _uiState.value = _uiState.value.copy(loading = !hasData, refreshing = hasData, errorMessage = null)
            loadCatching { repository.loadHistory() }
                .onSuccess { series -> _uiState.value = ReportsUiState(loading = false, series = series) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = "Não foi possível carregar os Relatórios.",
                    )
                }
        }
    }
}

@Composable
fun ReportsRoute(onBack: () -> Unit, reportsViewModel: ReportsViewModel = viewModel()) {
    val state by reportsViewModel.uiState.collectAsState()
    ReportsScreen(state = state, onBack = onBack, onRefresh = reportsViewModel::refresh)
}

@Composable
private fun ReportsScreen(state: ReportsUiState, onBack: () -> Unit, onRefresh: () -> Unit) {
    val points = state.series?.points.orEmpty()
    val latest = points.lastOrNull()

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                ) {
                    TextButton(onClick = onBack) { Text("Voltar") }
                    Text(
                        "Relatórios",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefresh, enabled = !state.loading && !state.refreshing) {
                        Text(if (state.refreshing) "Atualizando…" else "Atualizar")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("ESTOQUE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Valor do estoque ao longo do tempo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Histórico mensal calculado pelas Conferências físicas e Entradas registradas. " +
                            "Quando não existe Conferência exatamente no fechamento, o sistema usa os registros " +
                            "disponíveis sem inventar consumo ou saída.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            if (state.loading) {
                item { Card { Text("Carregando Relatórios…", modifier = Modifier.padding(18.dp)) } }
            }

            state.errorMessage?.let { message ->
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRefresh) { Text("Tentar novamente") }
                        }
                    }
                }
            }

            if (!state.loading && state.errorMessage == null && state.series != null && points.isEmpty()) {
                item {
                    Card {
                        Text(
                            "Ainda não existem registros suficientes para montar o histórico mensal.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }

            if (latest != null) {
                item { CurrentSummaryCard(latest) }
                item { ChartCard(points) }
                item { PrintCard(points) }
                item {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("FECHAMENTOS", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            Text("Valores mensais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${points.size} ${if (points.size == 1) "mês disponível" else "meses disponíveis"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(points.reversed(), key = { it.month }) { point -> MonthlyValueCard(point) }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CurrentSummaryCard(point: MonthlyStockSeriesPoint) {
    val report = point.report
    Card {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            StatusBadges(point, estimatedLabel = "Estimado pelos registros disponíveis")
            Text("VALOR CONHECIDO", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 14.dp))
            Text(reportValueLabel(report), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Referência em ${formatReportDate(point.referenceDate)}." +
                    if (point.isProvisional) " Este mês ainda pode mudar com novas Entradas, Conferências ou correções." else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("COBERTURA DO CÁLCULO", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Metric("Produtos", report.productCount, Modifier.weight(1f))
                Metric("Valorados", report.valuedProductCount, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Metric("Sem preço", report.missingPriceProductCount, Modifier.weight(1f))
                Metric("Estoque incerto", report.unknownStockProductCount, Modifier.weight(1f))
            }
            if (report.isIncomplete) {
                AttentionBox("* Atenção: " + reportIncompleteMessage(report), Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun MonthlyValueCard(point: MonthlyStockSeriesPoint) {
    val report = point.report
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                formatReportMonth(point.month).replaceFirstChar { it.uppercase() },
                fontWeight = FontWeight.Bold,
            )
            StatusBadges(point, estimatedLabel = "Estimado", modifier = Modifier.padding(top = 4.dp))
            Text(
                "Referência: ${formatReportDate(point.referenceDate)} · ${report.productCount} " +
                    if (report.productCount == 1) "Produto" else "Produtos",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (report.isIncomplete) {
                Text(
                    reportIncompleteMessage(report),
                    color = AMBER_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text("VALOR CONHECIDO", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
            Text(reportValueLabel(report), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChartCard(points: List<MonthlyStockSeriesPoint>) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("EVOLUÇÃO MENSAL", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text("Valor conhecido do estoque", fontWeight = FontWeight.Bold)
            Text(
                "O gráfico soma somente valores calculáveis. Produtos sem preço ou com estoque ainda não " +
                    "confiável ficam sinalizados nos meses abaixo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            StockValueChart(points, Modifier.fillMaxWidth().height(200.dp).padding(top = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                points.forEach { point ->
                    Text(
                        formatReportShortMonth(point.month),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            val scale = reportChartScale(points.map { it.report.totalKnown })
            Text(
                "Escala: ${formatReportCompactMoney(scale.min)} a ${formatReportCompactMoney(scale.max)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (points.size == 1) {
                Text(
                    "O histórico começa em ${formatReportMonth(points.first().month)}. O gráfico ganhará linha " +
                        "quando houver mais de um mês disponível.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StockValueChart(points: List<MonthlyStockSeriesPoint>, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val haloColor = MaterialTheme.colorScheme.primaryContainer
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val scale = remember(points) { reportChartScale(points.map { it.report.totalKnown }) }

    Canvas(modifier) {
        val count = points.size
        fun xAt(index: Int): Float {
            val slot = size.width / count
            return slot * index + slot / 2f
        }
        fun yAt(value: Double): Float = (size.height - scale.ratio(value) * size.height).toFloat()

        scale.gridValues.forEach { value ->
            val y = yAt(value)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 14f)),
            )
        }

        if (count > 1) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = xAt(index)
                val y = yAt(point.report.totalKnown)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        points.forEachIndexed { index, point ->
            val center = Offset(xAt(index), yAt(point.report.totalKnown))
            drawCircle(color = haloColor, radius = 18f, center = center)
            drawCircle(color = lineColor, radius = 10f, center = center)
        }
    }
}

@Composable
private fun PrintCard(points: List<MonthlyStockSeriesPoint>) {
    val context = LocalContext.current
    var periodMode by rememberSaveable { mutableStateOf(false) }
    var startMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var endMonth by rememberSaveable { mutableStateOf<String?>(null) }

    val first = points.first().month
    val last = points.last().month
    val effectiveStart = startMonth?.takeIf { month -> points.any { it.month == month } } ?: first
    val effectiveEnd = endMonth?.takeIf { month -> points.any { it.month == month } } ?: last
    val selected = if (periodMode) {
        runCatching { filterSeriesPoints(points, effectiveStart, effectiveEnd) }.getOrDefault(emptyList())
    } else {
        points
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("IMPRESSÃO", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text("Imprimir / Salvar PDF", fontWeight = FontWeight.Bold)
            Text(
                "Gera o Relatório de Valor do Estoque em A4. Pelo diálogo do sistema é possível imprimir ou salvar em PDF.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                RadioButton(selected = !periodMode, onClick = { periodMode = false })
                Text("Todo o histórico")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = periodMode, onClick = { periodMode = true })
                Text("Período selecionado")
            }

            if (periodMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    MonthPicker(
                        label = "De",
                        value = effectiveStart,
                        months = points.map { it.month },
                        onSelect = { month ->
                            startMonth = month
                            if (month > effectiveEnd) endMonth = month
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MonthPicker(
                        label = "Até",
                        value = effectiveEnd,
                        months = points.map { it.month },
                        onSelect = { month ->
                            endMonth = month
                            if (month < effectiveStart) startMonth = month
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(
                "Pré-visualização atual: ${reportPeriodLabel(selected).ifBlank { "nenhum mês selecionado" }}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )

            Button(
                onClick = { printStockReport(context, selected) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Text("Imprimir / Salvar PDF")
            }
        }
    }
}

@Composable
private fun MonthPicker(
    label: String,
    value: String,
    months: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(formatReportShortMonth(value))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                months.forEach { month ->
                    DropdownMenuItem(
                        text = { Text(formatReportMonth(month)) },
                        onClick = {
                            onSelect(month)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadges(point: MonthlyStockSeriesPoint, estimatedLabel: String, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        if (point.isProvisional) Badge("Provisório", AMBER_BG, AMBER_TEXT)
        if (point.report.hasExactPhysicalClose) {
            Badge("Fechamento físico exato", GREEN_BG, GREEN_TEXT)
        } else {
            Badge(estimatedLabel, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = MaterialTheme.shapes.extraLarge) {
        Text(
            text,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AttentionBox(text: String, modifier: Modifier = Modifier) {
    Surface(color = AMBER_BG, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Text(text, color = AMBER_TEXT, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

private val AMBER_BG = Color(0xFFFEF3C7)
private val AMBER_TEXT = Color(0xFF92400E)
private val GREEN_BG = Color(0xFFD1FAE5)
private val GREEN_TEXT = Color(0xFF065F46)

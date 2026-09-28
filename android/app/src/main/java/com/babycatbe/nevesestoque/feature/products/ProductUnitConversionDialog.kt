package com.babycatbe.nevesestoque.feature.products

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductUnitConversionDialog(
    product: ProductDetails,
    converting: Boolean,
    backendError: String?,
    onDismiss: () -> Unit,
    onConfirm: (ProductUnitConversionDraft) -> Unit,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    var newUnit by rememberSaveable { mutableStateOf("") }
    var oldText by rememberSaveable { mutableStateOf("1") }
    var newText by rememberSaveable { mutableStateOf("") }
    var oldError by rememberSaveable { mutableStateOf<String?>(null) }
    var newError by rememberSaveable { mutableStateOf<String?>(null) }
    var unitError by rememberSaveable { mutableStateOf<String?>(null) }
    var oldValue by rememberSaveable { mutableStateOf<Double?>(null) }
    var newValue by rememberSaveable { mutableStateOf<Double?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    fun validatePreview() {
        unitError = if (newUnit.isBlank() || newUnit == product.unit) {
            "Escolha uma unidade diferente da atual."
        } else null
        val oldResult = parsePositiveConversionQuantity(oldText, "Quantidade em ${product.unit}")
        val newResult = parsePositiveConversionQuantity(
            newText,
            "Quantidade em ${newUnit.ifBlank { "nova unidade" }}",
        )
        oldError = oldResult.error
        newError = newResult.error
        if (unitError == null && oldResult.value != null && newResult.value != null) {
            oldValue = oldResult.value
            newValue = newResult.value
            preview = true
        }
    }

    val factor = if (preview && oldValue != null && newValue != null) {
        calculateUnitConversionFactor(oldValue!!, newValue!!)
    } else null

    AlertDialog(
        onDismissRequest = { if (!converting) onDismiss() },
        title = { Text("Alterar unidade de ${product.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
             ) {
                Text(
                    "ALTERAÇÃO PROTEGIDA",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!preview || factor == null) {
                    Text(
                        "Informe uma equivalência real. Exemplo: 12 UN = 1 CX.",
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = oldText,
                        onValueChange = { oldText = it; oldError = null },
                        label = { Text("Quantidade em ${product.unit}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = oldError != null,
                        supportingText = oldError?.let { value -> { Text(value) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Nova unidade", modifier = Modifier.padding(top = 6.dp))
                    Box {
                        TextButton(onClick = { menuOpen = true }) {
                            Text(newUnit.ifBlank { "Selecionar…" })
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            PRODUCT_UNITS.filter { it != product.unit }.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit) },
                                    onClick = {
                                        newUnit = unit
                                        unitError = null
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    unitError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it; newError = null },
                        label = { Text("Quantidade em ${newUnit.ifBlank { "nova unidade" }}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = newError != null,
                        supportingText = newError?.let { value -> { Text(value) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "A conversço é retroativa: estoque inicial, Entradas, preços e Conferências são convertidos, inclusive registros excluídos/restauráveis.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        "${fmt(oldValue!!)} ${product.unit} = ${fmt(newValue!!)} $newUnit",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    PreviewValue(
                        "Estoque atual",
                        if (product.stockRequiresConference) "Conferência necessária"
                        else "${fmtNullable(product.currentQuantity)} ${product.unit}",
                        if (product.stockRequiresConference) "Conferência necessária"
                        else "${fmtNullable(convertQuantityForUnit(product.currentQuantity, factor))} $newUnit",
                    )
                    PreviewValue(
                        "Preço atual",
                        price(product.currentPrice, product.unit),
                        price(convertPriceForUnit(product.currentPrice, factor), newUnit),
                    )
                    product.initialStockQuantity?.let {
                        PreviewValue(
                            "Estoque inicial",
                            "${fmt(it)} ${product.unit}",
                            "${fmt(convertQuantityForUnit(it, factor)!!)} $newUnit",
                        )
                    }
                    product.initialPrice?.let {
                        PreviewValue(
                            "Preço inicial",
                            price(it, product.unit),
                            price(convertPriceForUnit(it, factor), newUnit),
                        )
                    }
                    Text(
                        if (product.currentValue != null) {
                            "Valor atual preservado: ${money(product.currentValue)}. Preços usam a razão inversa para preservar a equivalência financeira."
                        } else {
                            "Preços usam a razão inversa para preservar a equivalência financeira."
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (product.stockRequiresConference) {
                        Text(
                            "A exigência de Conferência física criada pela mescla permanece após a conversão.",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Text(
                        "O banco cria backup antes da conversão e atualiza Produto, Entradas e Conferências em uma única transação auditada.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                backendError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!preview || factor == null) {
                        validatePreview()
                    } else {
                        onConfirm(
                            ProductUnitConversionDraft(
                                productId = product.id,
                                newUnit = newUnit,
                                oldQuantity = oldValue!!,
                              newQuantity = newValue!!,
                             )
                        )
                    }
                },
                enabled = !converting,
            ) {
                Text(
                    when {
                        converting -> "Convertendo…"
                        preview -> "Confirmar conversão"
                        else -> "Ver prévia"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (preview) preview = false else onDismiss() },
                enabled = !converting,
            ) {
                Text(if (preview) "Voltar" else "Cancelar")
            }
        },
    )
}

@Composable
private fun PreviewValue(label: String, before: String, after: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(
            "$before → $after",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun fmtNullable(value: Double?): String =
    value?.let(::fmt) ?: "Sem dados"

private fun fmt(value: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply {
        maximumFractionDigits = 8
    }.format(value)

private fun price(value: Double?, unit: String): String =
    if (value == null) "Sem preço" else "${moneyPrecise(value)} / $unit"

private fun moneyPrecise(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 8
    }.format(value)

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)

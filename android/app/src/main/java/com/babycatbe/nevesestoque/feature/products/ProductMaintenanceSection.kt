package com.babycatbe.nevesestoque.feature.products

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.babycatbe.nevesestoque.ui.components.NevesActionLabel
import com.babycatbe.nevesestoque.ui.components.NevesIcons

/** Presentation only: operations still belong to ProductDetailViewModel and the protected flows. */
@Composable
internal fun ProductMaintenanceSection(
    state: ProductDetailUiState,
    enabled: Boolean,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onConvertUnit: (ProductUnitConversionDraft) -> Unit,
    onClearConversionError: () -> Unit,
    onConsumeConversionNotice: () -> Unit,
    onRefresh: () -> Unit,
) {
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    var unitConversionOpen by rememberSaveable { mutableStateOf(false) }
    val product = state.product
    val actionsEnabled = enabled && !state.loading && !state.refreshing &&
        !state.deleting && !state.deleted && !state.convertingUnit && state.errorMessage == null

    LaunchedEffect(state.conversionNotice) {
        if (state.conversionNotice != null) unitConversionOpen = false
    }

    HorizontalDivider()
    Text("Unidade", style = MaterialTheme.typography.titleMedium)
    if (state.loading) Text("Carregando ações do Produto…")
    state.errorMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRefresh, enabled = enabled && !state.refreshing) { Text("Tentar novamente") }
    }
    state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.conversionNotice?.let {
        Text(it, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onRefresh, enabled = enabled && !state.refreshing) { Text("Atualizar") }
        TextButton(onClick = onConsumeConversionNotice) { Text("Fechar") }
    }
    if (product != null) {
        Text("Unidade atual: ${product.unit}", fontWeight = FontWeight.SemiBold)
        Text(
            "Informe uma equivalência real. A conversão altera retroativamente quantidades, preços, Entradas e Conferências.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { onClearConversionError(); unitConversionOpen = true },
            enabled = actionsEnabled,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(if (state.convertingUnit) "Convertendo…" else "Alterar unidade") }

        HorizontalDivider()
        Text("Ações avançadas", style = MaterialTheme.typography.titleMedium)
        Text(
            if (product.categoryId == null) "Complete o cadastro deste Produto antes de mesclar."
            else "Una um cadastro duplicado preservando o ID mais antigo, o histórico real e a auditoria.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onMerge,
            enabled = actionsEnabled && product.categoryId != null,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Mesclar com outro Produto") }

        HorizontalDivider()
        Text("Lixeira", style = MaterialTheme.typography.titleMedium)
        Text(
            "Excluir remove este Produto das áreas ativas. Ele ficará restaurável por 7 dias.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { deleteOpen = true },
            enabled = actionsEnabled,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { NevesActionLabel(if (state.deleting) "Excluindo…" else "Excluir Produto", NevesIcons.Trash) }
    }

    if (unitConversionOpen && product != null) {
        ProductUnitConversionDialog(
            product = product,
            converting = state.convertingUnit,
            backendError = state.conversionError,
            onDismiss = {
                if (!state.convertingUnit) {
                    unitConversionOpen = false
                    onClearConversionError()
                }
            },
            onConfirm = onConvertUnit,
        )
    }

    if (deleteOpen && product != null) {
        AlertDialog(
            onDismissRequest = { if (!state.deleting) deleteOpen = false },
            title = { Text("Excluir Produto?") },
            text = {
                Text(
                    productDeleteConfirmation(
                        name = product.name,
                        currentQuantity = product.currentQuantity,
                        unit = product.unit,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteOpen = false
                        onDelete()
                    },
                    enabled = !state.deleting,
                ) {
                    NevesActionLabel("Excluir Produto", NevesIcons.Trash)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteOpen = false },
                    enabled = !state.deleting,
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

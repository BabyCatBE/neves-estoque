package com.babycatbe.nevesestoque.ui.input

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val nevesDateFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

internal fun formatNevesDateForDisplay(value: String): String =
    runCatching { LocalDate.parse(value).format(nevesDateFormatter) }.getOrDefault(value)

internal fun nevesDatePickerMillis(value: String): Long =
    runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

internal fun nevesIsoDateFromPickerMillis(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).toLocalDate().toString()

/**
 * Campo padrão de data do Android.
 *
 * O valor interno continua ISO (AAAA-MM-DD). Na interface a data é DD/MM/AAAA e a alteração
 * acontece somente pelo calendário visual. Datas futuras ficam indisponíveis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NevesDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onDateConfirmed: (() -> Unit)? = null,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldModifier =
        if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier

    OutlinedTextField(
        value = formatNevesDateForDisplay(value),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text("DD/MM/AAAA") },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { message -> { Text(message) } },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { pickerOpen = true }, enabled = enabled) {
                NevesIcon(NevesIcons.Calendar, "Selecionar data")
            }
        },
        modifier = fieldModifier.onFocusChanged {
            if (it.isFocused) keyboard?.hide()
        },
    )

    if (pickerOpen) {
        val today = LocalDate.now()
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayMillis

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = nevesDatePickerMillis(value),
            selectableDates = selectableDates,
        )

        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selected ->
                            onValueChange(nevesIsoDateFromPickerMillis(selected))
                            pickerOpen = false
                            onDateConfirmed?.invoke()
                        }
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

package com.babycatbe.nevesestoque.ui.input

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.theme.NevesColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Estado do teclado numérico próprio do app numa tela de formulário.
 *
 * O teclado aparece somente enquanto um [NevesNumericField] está focado. Ao focar um campo de texto
 * comum (ex.: Observação), o campo numérico perde o foco, o teclado do app some e o teclado normal
 * do celular volta a ser usado.
 */
@Stable
class NumericKeypadState internal constructor() {
    internal var active by mutableStateOf<NumericFieldHandle?>(null)

    val isVisible: Boolean get() = active != null
}

@Composable
fun rememberNumericKeypadState(): NumericKeypadState = remember { NumericKeypadState() }

/** Pede foco sem derrubar a tela se o campo ainda não estiver na composição. */
fun FocusRequester.requestFocusSafely(): Boolean =
    runCatching { requestFocus() }.isSuccess

internal class NumericFieldHandle(
    val value: () -> String,
    val onValueChange: (String) -> Unit,
    val onConfirm: () -> Unit,
    val confirmLabel: () -> String,
    val allowDecimal: () -> Boolean,
)

/** Folga acima/abaixo do campo ao rolar: mantém o nome do Produto visível junto com o campo. */
private val VisibleMarginTop = 88.dp
private val VisibleMarginBottom = 24.dp

/**
 * Mantém o elemento visível acima do teclado (do app ou do celular) quando recebe foco.
 * A segunda tentativa cobre a animação do teclado do celular, que reduz a área aos poucos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberKeepVisibleOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val top = with(density) { VisibleMarginTop.toPx() }
    val bottom = with(density) { VisibleMarginBottom.toPx() }
    return Modifier
        .onSizeChanged { size = it }
        .bringIntoViewRequester(requester)
        .onFocusChanged { focus ->
            if (focus.isFocused) {
                scope.launch {
                    val area = { Rect(0f, -top, size.width.toFloat(), size.height + bottom) }
                    delay(90)
                    requester.bringIntoView(area())
                    delay(260)
                    requester.bringIntoView(area())
                }
            }
        }
}

/**
 * Campo numérico que usa o teclado próprio do app em vez do teclado do celular.
 *
 * - [onConfirm] é chamado pela tecla de avançar (ou Enter de teclado físico): a tela valida o campo e
 *   decide o próximo foco; com erro, mantém o foco aqui.
 * - [stepper] mostra os botões − e + (usar somente na Conferência). Eles não tiram o foco do campo.
 */
@Composable
fun NevesNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    keypad: NumericKeypadState,
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    confirmLabel: String = "Próximo",
    placeholder: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    stepper: Boolean = false,
    stepSubject: String? = null,
    allowDecimal: Boolean = true,
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentConfirmLabel by rememberUpdatedState(confirmLabel)
    val currentAllowDecimal by rememberUpdatedState(allowDecimal)
    val handle = remember(keypad) {
        NumericFieldHandle(
            value = { currentValue },
            onValueChange = { currentOnValueChange(it) },
            onConfirm = { currentOnConfirm() },
            confirmLabel = { currentConfirmLabel },
            allowDecimal = { currentAllowDecimal },
        )
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val keepVisible = rememberKeepVisibleOnFocus()

    DisposableEffect(keypad, handle) {
        onDispose { if (keypad.active === handle) keypad.active = null }
    }

    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(it) } },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = fieldModifier
                .focusRequester(focusRequester)
                .then(keepVisible)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        keypad.active = handle
                        // Campo numérico nunca usa o teclado do celular.
                        keyboard?.hide()
                    } else if (keypad.active === handle) {
                        keypad.active = null
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    handleHardwareKey(event.key, handle)
                },
        )
    }

    if (!stepper) {
        field(modifier.fillMaxWidth())
        return
    }

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        StepButton(
            icon = NevesIcons.Remove,
            description = "Diminuir 1" + (stepSubject?.let { " em $it" } ?: ""),
            enabled = enabled,
            onClick = { NumericInputEditing.step(currentValue, -1)?.let(currentOnValueChange) },
        )
        field(Modifier.weight(1f))
        StepButton(
            icon = NevesIcons.Add,
            description = "Aumentar 1" + (stepSubject?.let { " em $it" } ?: ""),
            enabled = enabled,
            onClick = { NumericInputEditing.step(currentValue, 1)?.let(currentOnValueChange) },
        )
    }
}

@Composable
private fun StepButton(@DrawableRes icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        // Não recebe foco: o campo continua ativo e o teclado do app continua aberto.
        modifier = Modifier
            .padding(top = 8.dp)
            .size(52.dp)
            .focusProperties { canFocus = false },
    ) {
        NevesIcon(icon, description)
    }
}

private fun handleHardwareKey(key: Key, handle: NumericFieldHandle): Boolean {
    val digit = when (key) {
        Key.Zero, Key.NumPad0 -> '0'
        Key.One, Key.NumPad1 -> '1'
        Key.Two, Key.NumPad2 -> '2'
        Key.Three, Key.NumPad3 -> '3'
        Key.Four, Key.NumPad4 -> '4'
        Key.Five, Key.NumPad5 -> '5'
        Key.Six, Key.NumPad6 -> '6'
        Key.Seven, Key.NumPad7 -> '7'
        Key.Eight, Key.NumPad8 -> '8'
        Key.Nine, Key.NumPad9 -> '9'
        else -> null
    }
    when {
        digit != null -> handle.onValueChange(NumericInputEditing.appendDigit(handle.value(), digit))
        key == Key.Comma || key == Key.Period || key == Key.NumPadDot || key == Key.NumPadComma -> {
            if (!handle.allowDecimal()) return true
            handle.onValueChange(NumericInputEditing.appendDecimalSeparator(handle.value()))
        }
        key == Key.Backspace -> handle.onValueChange(NumericInputEditing.backspace(handle.value()))
        key == Key.Enter || key == Key.NumPadEnter -> handle.onConfirm()
        else -> return false
    }
    return true
}

private val KeyHeight = 52.dp
private val KeyGap = 6.dp
private val KeyShape = RoundedCornerShape(12.dp)

/**
 * Teclado numérico próprio do app, fixo na parte de baixo da tela (usar no `bottomBar` do Scaffold,
 * assim o conteúdo encolhe e o campo ativo continua visível acima dele).
 * Números, vírgula, apagar, ocultar e avançar/concluir.
 */
@Composable
fun NevesNumericKeypad(state: NumericKeypadState) {
    val active = state.active ?: return
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    // Voltar com o teclado aberto só fecha o teclado.
    BackHandler(enabled = true) { focusManager.clearFocus() }

    fun press(action: () -> Unit) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        action()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KeyGap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(KeyHeight * 4 + KeyGap * 3),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(KeyGap),
                modifier = Modifier.weight(3f).fillMaxHeight(),
            ) {
                listOf("123", "456", "789").forEach { row ->
                    KeyRow {
                        row.forEach { digit ->
                            TextKey(digit.toString()) {
                                press { active.onValueChange(NumericInputEditing.appendDigit(active.value(), digit)) }
                            }
                        }
                    }
                }
                KeyRow {
                    TextKey(",", description = "Vírgula", enabled = active.allowDecimal()) {
                        press { active.onValueChange(NumericInputEditing.appendDecimalSeparator(active.value())) }
                    }
                    TextKey("0") {
                        press { active.onValueChange(NumericInputEditing.appendDigit(active.value(), '0')) }
                    }
                    IconKey(NevesIcons.Backspace, "Apagar") {
                        press { active.onValueChange(NumericInputEditing.backspace(active.value())) }
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(KeyGap),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                KeyBox(
                    description = "Ocultar teclado",
                    background = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().height(KeyHeight),
                    onClick = { press { focusManager.clearFocus() } },
                ) {
                    NevesIcon(NevesIcons.KeyboardHide, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                KeyBox(
                    description = active.confirmLabel(),
                    background = NevesColors.Primary,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onClick = { press { active.onConfirm() } },
                ) {
                    Text(
                        active.confirmLabel(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KeyGap),
        modifier = Modifier.fillMaxWidth().height(KeyHeight),
        content = content,
    )
}

@Composable
private fun RowScope.TextKey(
    text: String,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    KeyBox(
        description = description ?: text,
        background = MaterialTheme.colorScheme.surface,
        enabled = enabled,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        onClick = onClick,
    ) {
        Text(
            text,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.IconKey(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    KeyBox(
        description = description,
        background = MaterialTheme.colorScheme.surface,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        onClick = onClick,
    ) {
        NevesIcon(icon, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun KeyBox(
    description: String,
    background: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(KeyShape)
            .background(background)
            // As teclas nunca recebem foco: o campo numérico ativo continua focado.
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        content()
    }
}

package com.babycatbe.nevesestoque.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Cores de identidade Neves que não pertencem ao ColorScheme do Material 3.
 * Existe uma paleta clara (valores aprovados antes do Dark Mode, sem alteração) e uma escura
 * (grafite/carvão quente). As telas continuam lendo `NevesColors.X`; o tema decide a paleta.
 */
@Immutable
data class NevesPalette(
    val header: Color,
    val headerText: Color,
    val headerMuted: Color,
    val cardBorder: Color,
    val accent: Color,
    val primary: Color,
    val alert: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val errorContainer: Color,
    val onError: Color,
    val offlineBanner: Color,
    val onOfflineBanner: Color,
)

internal val LightNevesPalette = NevesPalette(
    header = Color(0xFF09090B),
    headerText = Color.White,
    headerMuted = Color(0xFFA1A1AA),
    cardBorder = Color(0xFFF8DADA),
    accent = Color(0xFFEF4444),
    // Darker red retains readable white labels on filled buttons.
    primary = Color(0xFFB91C1C),
    alert = Color(0xFFF59E0B),
    warningContainer = Color(0xFFFEF3C7),
    onWarning = Color(0xFF78350F),
    successContainer = Color(0xFFD1FAE5),
    onSuccess = Color(0xFF065F46),
    errorContainer = Color(0xFFFEE2E2),
    onError = Color(0xFF991B1B),
    // Mesmo visual anterior da faixa Offline: marrom âmbar com texto branco.
    offlineBanner = Color(0xFF78350F),
    onOfflineBanner = Color.White,
)

internal val DarkNevesPalette = NevesPalette(
    // Header continua mais escuro que o fundo (#181614), na mesma família quente.
    header = Color(0xFF110F0D),
    headerText = Color(0xFFF4F1ED),
    headerMuted = Color(0xFFB8B0A7),
    cardBorder = Color(0xFF3A2A27),
    accent = Color(0xFFEF4444),
    // Vermelho Neves ajustado para fundo escuro: branco sobre ele >= 4.5:1 e >= 3:1 sobre cards.
    primary = Color(0xFFD93A3A),
    alert = Color(0xFFF59E0B),
    warningContainer = Color(0xFF3A2C12),
    onWarning = Color(0xFFF5CF8A),
    successContainer = Color(0xFF173323),
    onSuccess = Color(0xFF9BE3B8),
    errorContainer = Color(0xFF4A1F1B),
    onError = Color(0xFFFFB4AB),
    offlineBanner = Color(0xFF5C3A10),
    onOfflineBanner = Color(0xFFFDE7C2),
)

/** Interpolação usada somente durante a transição Claro ↔ Escuro. Extremos retornam a paleta exata. */
fun lerpNevesPalette(light: NevesPalette, dark: NevesPalette, fraction: Float): NevesPalette {
    if (fraction <= 0f) return light
    if (fraction >= 1f) return dark
    fun mix(a: Color, b: Color) = lerp(a, b, fraction)
    return NevesPalette(
        header = mix(light.header, dark.header),
        headerText = mix(light.headerText, dark.headerText),
        headerMuted = mix(light.headerMuted, dark.headerMuted),
        cardBorder = mix(light.cardBorder, dark.cardBorder),
        accent = mix(light.accent, dark.accent),
        primary = mix(light.primary, dark.primary),
        alert = mix(light.alert, dark.alert),
        warningContainer = mix(light.warningContainer, dark.warningContainer),
        onWarning = mix(light.onWarning, dark.onWarning),
        successContainer = mix(light.successContainer, dark.successContainer),
        onSuccess = mix(light.onSuccess, dark.onSuccess),
        errorContainer = mix(light.errorContainer, dark.errorContainer),
        onError = mix(light.onError, dark.onError),
        offlineBanner = mix(light.offlineBanner, dark.offlineBanner),
        onOfflineBanner = mix(light.onOfflineBanner, dark.onOfflineBanner),
    )
}

val LocalNevesPalette = staticCompositionLocalOf { LightNevesPalette }

object NevesColors {
    val Header: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.header
    val HeaderText: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.headerText
    val HeaderMuted: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.headerMuted
    val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.cardBorder
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.accent
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.primary
    val Alert: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.alert
    val WarningContainer: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.warningContainer
    val OnWarning: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.onWarning
    val SuccessContainer: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.successContainer
    val OnSuccess: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.onSuccess
    val ErrorContainer: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.errorContainer
    val OnError: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.onError
    val OfflineBanner: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.offlineBanner
    val OnOfflineBanner: Color @Composable @ReadOnlyComposable get() = LocalNevesPalette.current.onOfflineBanner
}

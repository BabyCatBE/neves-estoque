package com.babycatbe.nevesestoque.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Transição única e central Claro ↔ Escuro: seletor e paleta usam a mesma duração/easing. */
object NevesThemeTransition {
    const val DURATION_MILLIS = 300
    val Easing = FastOutSlowInEasing
}

internal val NevesLightColors = lightColorScheme(
    primary = LightNevesPalette.primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEE2E2),
    onPrimaryContainer = LightNevesPalette.onError,
    secondary = Color(0xFF52525B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4E7),
    onSecondaryContainer = Color(0xFF27272A),
    tertiary = Color(0xFF92400E),
    onTertiary = Color.White,
    tertiaryContainer = LightNevesPalette.warningContainer,
    onTertiaryContainer = LightNevesPalette.onWarning,
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF18181B),
    surface = Color.White,
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF1F1F3),
    onSurfaceVariant = Color(0xFF52525B),
    surfaceTint = Color.Transparent,
    surfaceDim = Color(0xFFE4E4E7),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF4F4F5),
    surfaceContainerHigh = Color(0xFFEDEDEF),
    surfaceContainerHighest = Color(0xFFE4E4E7),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFFD4D4D8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = LightNevesPalette.errorContainer,
    onErrorContainer = LightNevesPalette.onError,
)

/** Grafite/carvão quente aprovado: fundo #181614, cards #211E1B, secundária #2A2622, elevada #302B27. */
internal val NevesDarkColors = darkColorScheme(
    primary = DarkNevesPalette.primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1F1C),
    onPrimaryContainer = Color(0xFFF8B4AE),
    inversePrimary = LightNevesPalette.primary,
    secondary = Color(0xFFB8B0A7),
    onSecondary = Color(0xFF181614),
    secondaryContainer = Color(0xFF302B27),
    onSecondaryContainer = Color(0xFFF4F1ED),
    tertiary = Color(0xFFE7A33E),
    onTertiary = Color(0xFF2A1A05),
    tertiaryContainer = DarkNevesPalette.warningContainer,
    onTertiaryContainer = DarkNevesPalette.onWarning,
    background = Color(0xFF181614),
    onBackground = Color(0xFFF4F1ED),
    surface = Color(0xFF211E1B),
    onSurface = Color(0xFFF4F1ED),
    surfaceVariant = Color(0xFF2A2622),
    onSurfaceVariant = Color(0xFFB8B0A7),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFF4F1ED),
    inverseOnSurface = Color(0xFF2A2622),
    surfaceDim = Color(0xFF141210),
    surfaceBright = Color(0xFF3A3430),
    surfaceContainerLowest = Color(0xFF181614),
    surfaceContainerLow = Color(0xFF211E1B),
    surfaceContainer = Color(0xFF2A2622),
    surfaceContainerHigh = Color(0xFF302B27),
    surfaceContainerHighest = Color(0xFF3A3430),
    outline = Color(0xFF8A8078),
    outlineVariant = Color(0xFF3D3732),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = DarkNevesPalette.errorContainer,
    onErrorContainer = DarkNevesPalette.onError,
    scrim = Color.Black,
)

/** Interpola o ColorScheme durante a transição. Nos extremos devolve o esquema exato (claro intacto). */
fun lerpColorScheme(light: ColorScheme, dark: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return light
    if (fraction >= 1f) return dark
    fun mix(a: Color, b: Color) = lerp(a, b, fraction)
    return light.copy(
        primary = mix(light.primary, dark.primary),
        onPrimary = mix(light.onPrimary, dark.onPrimary),
        primaryContainer = mix(light.primaryContainer, dark.primaryContainer),
        onPrimaryContainer = mix(light.onPrimaryContainer, dark.onPrimaryContainer),
        inversePrimary = mix(light.inversePrimary, dark.inversePrimary),
        secondary = mix(light.secondary, dark.secondary),
        onSecondary = mix(light.onSecondary, dark.onSecondary),
        secondaryContainer = mix(light.secondaryContainer, dark.secondaryContainer),
        onSecondaryContainer = mix(light.onSecondaryContainer, dark.onSecondaryContainer),
        tertiary = mix(light.tertiary, dark.tertiary),
        onTertiary = mix(light.onTertiary, dark.onTertiary),
        tertiaryContainer = mix(light.tertiaryContainer, dark.tertiaryContainer),
        onTertiaryContainer = mix(light.onTertiaryContainer, dark.onTertiaryContainer),
        background = mix(light.background, dark.background),
        onBackground = mix(light.onBackground, dark.onBackground),
        surface = mix(light.surface, dark.surface),
        onSurface = mix(light.onSurface, dark.onSurface),
        surfaceVariant = mix(light.surfaceVariant, dark.surfaceVariant),
        onSurfaceVariant = mix(light.onSurfaceVariant, dark.onSurfaceVariant),
        surfaceTint = mix(light.surfaceTint, dark.surfaceTint),
        inverseSurface = mix(light.inverseSurface, dark.inverseSurface),
        inverseOnSurface = mix(light.inverseOnSurface, dark.inverseOnSurface),
        error = mix(light.error, dark.error),
        onError = mix(light.onError, dark.onError),
        errorContainer = mix(light.errorContainer, dark.errorContainer),
        onErrorContainer = mix(light.onErrorContainer, dark.onErrorContainer),
        outline = mix(light.outline, dark.outline),
        outlineVariant = mix(light.outlineVariant, dark.outlineVariant),
        scrim = mix(light.scrim, dark.scrim),
        surfaceBright = mix(light.surfaceBright, dark.surfaceBright),
        surfaceContainer = mix(light.surfaceContainer, dark.surfaceContainer),
        surfaceContainerHigh = mix(light.surfaceContainerHigh, dark.surfaceContainerHigh),
        surfaceContainerHighest = mix(light.surfaceContainerHighest, dark.surfaceContainerHighest),
        surfaceContainerLow = mix(light.surfaceContainerLow, dark.surfaceContainerLow),
        surfaceContainerLowest = mix(light.surfaceContainerLowest, dark.surfaceContainerLowest),
        surfaceDim = mix(light.surfaceDim, dark.surfaceDim),
    )
}

private val BaseTypography = Typography()
private val NevesTypography = BaseTypography.copy(
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)
private val NevesShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Tema escolhido (alvo), não o valor intermediário da animação. */
val LocalNevesDarkTheme = staticCompositionLocalOf { false }

/**
 * Tema único do app. A troca Claro ↔ Escuro anima um único progresso (0 = claro, 1 = escuro) e
 * interpola centralmente ColorScheme + paleta Neves; as telas não têm animações próprias de cor.
 * Na primeira composição o progresso já nasce no valor salvo, sem animação de abertura.
 */
@Composable
fun NevesTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(NevesThemeTransition.DURATION_MILLIS, easing = NevesThemeTransition.Easing),
        label = "nevesThemeTransition",
    )
    val colorScheme = remember(progress) { lerpColorScheme(NevesLightColors, NevesDarkColors, progress) }
    val palette = remember(progress) { lerpNevesPalette(LightNevesPalette, DarkNevesPalette, progress) }
    CompositionLocalProvider(LocalNevesPalette provides palette, LocalNevesDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = colorScheme, typography = NevesTypography, shapes = NevesShapes, content = content)
    }
}

package com.babycatbe.nevesestoque.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val NevesLightColors = lightColorScheme(
    primary = NevesColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEE2E2),
    onPrimaryContainer = NevesColors.OnError,
    secondary = Color(0xFF52525B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4E7),
    onSecondaryContainer = Color(0xFF27272A),
    tertiary = Color(0xFF92400E),
    onTertiary = Color.White,
    tertiaryContainer = NevesColors.WarningContainer,
    onTertiaryContainer = NevesColors.OnWarning,
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
    errorContainer = NevesColors.ErrorContainer,
    onErrorContainer = NevesColors.OnError,
)
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

@Composable
fun NevesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NevesLightColors, typography = NevesTypography, shapes = NevesShapes, content = content)
}

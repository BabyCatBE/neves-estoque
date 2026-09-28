package com.babycatbe.nevesestoque.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NevesLightColors = lightColorScheme(
    primary = Color(0xFFB91C1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4E4),
    onPrimaryContainer = Color(0xFF5C0000),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF18181B),
    surface = Color.White,
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF1F1F3),
    onSurfaceVariant = Color(0xFF5F5F66),
    error = Color(0xFFB3261E),
)

@Composable
fun NevesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NevesLightColors,
        content = content,
    )
}

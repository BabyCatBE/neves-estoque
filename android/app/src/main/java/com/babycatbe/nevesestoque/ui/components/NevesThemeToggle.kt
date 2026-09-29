package com.babycatbe.nevesestoque.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.babycatbe.nevesestoque.ui.theme.NevesColors
import com.babycatbe.nevesestoque.ui.theme.NevesThemeTransition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ToggleWidth = 60.dp
private val ToggleHeight = 32.dp
private val TogglePadding = 3.dp

/**
 * Seletor Claro/Escuro em cápsula: Sol à esquerda, Lua à direita e indicador vermelho Neves que
 * desliza para o lado ativo. Desenho compacto (60×32 dp) com área de toque mínima de 48 dp.
 * Trilho e borda vêm do ColorScheme, que já interpola junto com o app; o indicador e os ícones
 * usam a mesma duração/easing da transição global.
 */
@Composable
fun NevesThemeToggle(dark: Boolean, onDarkChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(NevesThemeTransition.DURATION_MILLIS, easing = NevesThemeTransition.Easing),
        label = "nevesThemeToggle",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val border = MaterialTheme.colorScheme.outlineVariant
    val indicator = NevesColors.Primary
    val onIndicator = MaterialTheme.colorScheme.onPrimary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = dark,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onValueChange = onDarkChange,
            )
            .semantics {
                contentDescription = "Tema escuro"
                stateDescription = if (dark) "Escuro" else "Claro"
            },
    ) {
        Canvas(Modifier.size(ToggleWidth, ToggleHeight)) {
            val radius = size.height / 2f
            drawRoundRect(color = track, cornerRadius = CornerRadius(radius))
            drawRoundRect(color = border, cornerRadius = CornerRadius(radius), style = Stroke(width = 1.dp.toPx()))

            val pad = TogglePadding.toPx()
            val knobRadius = radius - pad
            val sunCenter = Offset(pad + knobRadius, size.height / 2f)
            val moonCenter = Offset(size.width - pad - knobRadius, size.height / 2f)
            val knobX = sunCenter.x + (moonCenter.x - sunCenter.x) * progress
            drawCircle(color = indicator, radius = knobRadius, center = Offset(knobX, size.height / 2f))

            drawSun(sunCenter, lerp(onIndicator, inactive, progress), scale = 1f - 0.12f * progress)
            drawMoon(moonCenter, lerp(inactive, onIndicator, progress), scale = 0.88f + 0.12f * progress)
        }
    }
}

private fun DrawScope.drawSun(center: Offset, color: Color, scale: Float) {
    val core = 3.2.dp.toPx() * scale
    val rayStart = 5.2.dp.toPx() * scale
    val rayEnd = 7.2.dp.toPx() * scale
    drawCircle(color = color, radius = core, center = center)
    repeat(8) { index ->
        val angle = (index * PI / 4).toFloat()
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
            color = color,
            start = Offset(center.x + dx * rayStart, center.y + dy * rayStart),
            end = Offset(center.x + dx * rayEnd, center.y + dy * rayEnd),
            strokeWidth = 1.6.dp.toPx() * scale,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMoon(center: Offset, color: Color, scale: Float) {
    val radius = 6.dp.toPx() * scale
    val cutRadius = 5.dp.toPx() * scale
    val disc = Path().apply {
        addOval(Rect(center, radius))
    }
    val cutCenter = Offset(center.x + 3.4.dp.toPx() * scale, center.y - 2.6.dp.toPx() * scale)
    val cut = Path().apply {
        addOval(Rect(cutCenter, cutRadius))
    }
    val crescent = Path().apply { op(disc, cut, PathOperation.Difference) }
    drawPath(path = crescent, color = color)
}

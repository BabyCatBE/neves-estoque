package com.babycatbe.nevesestoque.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.babycatbe.nevesestoque.ui.theme.NevesColors

/** Operational content only. Controls and semantic warning/error surfaces keep their own colors. */
@Composable
fun NevesContentCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    border: BorderStroke = BorderStroke(1.dp, NevesColors.CardBorder),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.contentCardAccent(shape, NevesColors.Primary),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = border,
        content = content,
    )
}

/** Keep Material's native click semantics, ripple and touch handling. */
@Composable
fun NevesContentCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    border: BorderStroke = BorderStroke(1.dp, NevesColors.CardBorder),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.contentCardAccent(shape, NevesColors.Primary),
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = border,
        content = content,
    )
}

// Draw against the card's measured bounds, including expanded/animated content. No fixed height,
// intrinsic measurement or additional layout nodes: long lists and large fonts keep natural sizing.
// Clip the decoration and ripple to the same rounded shape. The stripe is deliberately on the left.
private fun Modifier.contentCardAccent(shape: Shape, stripe: Color): Modifier = clip(shape).drawWithContent {
    drawContent()
    drawRect(color = stripe, size = Size(4.dp.toPx(), size.height))
}

package com.babycatbe.nevesestoque.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.babycatbe.nevesestoque.ui.theme.NevesColors

@Composable
fun NevesIcon(@DrawableRes icon: Int, description: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(icon), contentDescription = description, modifier = modifier.size(24.dp), tint = tint)
}

@Composable
fun NevesActionLabel(text: String, @DrawableRes icon: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NevesIcon(icon, modifier = Modifier.size(18.dp))
        Text(text)
    }
}

@Composable
fun NevesRefreshIcon(refreshing: Boolean) {
    if (refreshing) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp).semantics { stateDescription = "Atualizando" },
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
    } else NevesIcon(NevesIcons.Refresh, "Atualizar")
}

/** Header-only palette: readable controls on the same surface as the system status bar. */
@Composable
fun NevesTopBarSurface(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = NevesColors.Accent,
        onSurface = NevesColors.HeaderText,
        onSurfaceVariant = NevesColors.HeaderMuted,
    )) {
        Surface(color = NevesColors.Header, contentColor = NevesColors.HeaderText, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** Compact readable state feedback; no transition delays or layout-wide fades. */
@Composable
fun NevesStatusMessage(text: String, @DrawableRes icon: Int = NevesIcons.Inventory, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier = modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NevesIcon(icon, tint = color)
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

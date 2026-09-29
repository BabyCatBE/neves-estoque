package com.babycatbe.nevesestoque.feature.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesIcons

/** Reserved visual area only: deliberately no click, image picker or persistence. */
@Composable
internal fun ProductImagePlaceholder() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NevesIcon(NevesIcons.Products, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text("Imagem do Produto", style = MaterialTheme.typography.labelLarge)
                Text("Sem imagem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Anchor-width menu with bounded, scrollable height for long category/unit lists. */
@Composable
internal fun ProductChoiceField(
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val menuWidth = maxWidth
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(value, modifier = Modifier.weight(1f))
            NevesIcon(NevesIcons.Expand)
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth).heightIn(max = 320.dp),
        ) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { expanded = false; onSelect(id) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

package com.babycatbe.nevesestoque.data.offline

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import com.babycatbe.nevesestoque.ui.theme.NevesColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Faixa exibida quando o aparelho está sem internet: informa que a consulta usa a cópia local
 * e que alterações oficiais continuam exigindo conexão.
 */
@Composable
fun OfflineBanner(offlineMode: Boolean) {
    val online by ConnectivityMonitor.online.collectAsStateWithLifecycle()
    val lastSnapshot by OfflineStore.lastSnapshotAt.collectAsStateWithLifecycle()
    if (online && !offlineMode) return

    Surface(color = NevesColors.OfflineBanner, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        NevesIcon(NevesIcons.Offline, tint = NevesColors.OnOfflineBanner)
        Text(
            offlineBannerText(lastSnapshot),
            color = NevesColors.OnOfflineBanner,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        }
    }
}

fun offlineBannerText(lastSnapshotMillis: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
    val snapshot = lastSnapshotMillis?.let {
        " · dados de " + Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    }.orEmpty()
    return "Sem internet$snapshot. Consulta pela cópia deste aparelho; alterações oficiais exigem conexão."
}


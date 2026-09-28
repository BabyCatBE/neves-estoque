package com.babycatbe.nevesestoque.data.offline

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val online by ConnectivityMonitor.online.collectAsState()
    val lastSnapshot by OfflineStore.lastSnapshotAt.collectAsState()
    if (online && !offlineMode) return

    Surface(color = Color(0xFF78350F), modifier = Modifier.fillMaxWidth()) {
        Text(
            offlineBannerText(lastSnapshot),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

fun offlineBannerText(lastSnapshotMillis: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
    val snapshot = lastSnapshotMillis?.let {
        " · dados de " + Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    }.orEmpty()
    return "Sem internet$snapshot. Consulta pela cópia deste aparelho; alterações oficiais exigem conexão."
}

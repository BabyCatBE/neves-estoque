package com.babycatbe.nevesestoque.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.time.ZoneId

class OfflineStoreTest {
    @Test
    fun atomicWriteReplacesContentWithoutLeavingTempFile() {
        val dir = Files.createTempDirectory("offline-test").toFile()
        val target = File(dir, "sub/data.json")
        OfflineStore.writeAtomic(target, "primeiro".toByteArray())
        OfflineStore.writeAtomic(target, "segundo".toByteArray())
        assertEquals("segundo", target.readText())
        assertFalse(File(target.parentFile, "data.json.tmp").exists())
        dir.deleteRecursively()
    }

    @Test
    fun onlyNetworkFailuresMayUseLocalCopy() {
        assertTrue(isNetworkFailure(IOException("sem rede")))
        assertTrue(isNetworkFailure(IllegalStateException("envelope", SocketTimeoutException("timeout"))))
        assertFalse(isNetworkFailure(IllegalStateException("Acesso não autorizado.")))
    }

    @Test
    fun bannerTextShowsSnapshotTime() {
        val text = offlineBannerText(1_790_600_000_000L, ZoneId.of("America/Sao_Paulo"))
        assertTrue(text.startsWith("Sem internet · dados de "))
        assertTrue(text.contains("alterações oficiais exigem conexão"))
        assertEquals(
            "Sem internet. Consulta pela cópia deste aparelho; alterações oficiais exigem conexão.",
            offlineBannerText(null),
        )
    }
}

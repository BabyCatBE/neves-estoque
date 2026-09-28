package com.babycatbe.nevesestoque.data.offline

import com.babycatbe.nevesestoque.awaitSuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        awaitSuspend { OfflineStore.writeAtomic(target, "primeiro".toByteArray()) }
        awaitSuspend { OfflineStore.writeAtomic(target, "segundo".toByteArray()) }
        assertEquals("segundo", target.readText())
        assertTrue(target.parentFile.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        dir.deleteRecursively()
    }

    @Test
    fun failedReplacementPreservesPreviousVersionAndCleansTempFile() {
        val dir = Files.createTempDirectory("offline-replace-failure").toFile()
        val target = File(dir, "pending.json").apply { writeText("versão válida") }

        val failure = runCatching {
            awaitSuspend {
                OfflineStore.writeAtomic(target, "nova versão".toByteArray()) { _, _ ->
                    throw IOException("falha simulada")
                }
            }
        }

        assertTrue(failure.exceptionOrNull() is IOException)
        assertEquals("versão válida", target.readText())
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        dir.deleteRecursively()
    }

    @Test
    fun cancellationBeforeReplacementKeepsPreviousVersionAndCleansTempFile() {
        val dir = Files.createTempDirectory("offline-cancel").toFile()
        val target = File(dir, "pending.json").apply { writeText("versão válida") }

        val failure = runCatching {
            awaitSuspend {
                OfflineStore.writeAtomic(target, "nova versão".toByteArray()) { _, _ ->
                    throw CancellationException("cancelamento simulado")
                }
            }
        }

        assertTrue(failure.exceptionOrNull() is CancellationException)
        assertEquals("versão válida", target.readText())
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        dir.deleteRecursively()
    }

    @Test
    fun concurrentWritesToSameFileNeverLeavePartialOrTemporaryContent() {
        val dir = Files.createTempDirectory("offline-concurrent").toFile()
        val target = File(dir, "pending.json")
        val values = (1..20).map { "conteúdo-$it-" + "x".repeat(2_000) }

        awaitSuspend {
            coroutineScope {
                values.map { value ->
                    async(Dispatchers.Default) { OfflineStore.writeAtomic(target, value.toByteArray()) }
                }.awaitAll()
            }
        }

        assertTrue(target.readText() in values)
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
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

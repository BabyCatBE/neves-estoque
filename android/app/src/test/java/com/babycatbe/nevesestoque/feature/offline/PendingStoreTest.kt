package com.babycatbe.nevesestoque.feature.offline

import com.babycatbe.nevesestoque.awaitSuspend
import com.babycatbe.nevesestoque.data.offline.OfflineStore
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
import java.nio.file.Files

class PendingStoreTest {
    private fun operation(updatedAt: String = "2026-09-28T10:00:00-03:00") = PendingOperation(
        localId = "local-1",
        kind = PendingKind.Entry,
        createdAt = "2026-09-28T09:00:00-03:00",
        updatedAt = updatedAt,
        authUserId = "auth-1",
        appUserId = "app-1",
        deviceId = "device-1",
        actorLabel = "Elias",
        idempotencyKey = "idem-1",
        entry = PendingEntryPayload(
            supplierLabel = "Moinho",
            effectiveAt = "2026-09-28T08:00:00-03:00",
            items = listOf(PendingEntryItem(productId = "p1", productName = "Farinha", unit = "SC", quantity = 2.0)),
        ),
    )

    private fun setUpDirectory(): File {
        val dir = Files.createTempDirectory("pending-store-test").toFile()
        OfflineStore.initializeForTests(dir)
        awaitSuspend { OfflineStore.prepare() }
        awaitSuspend { PendingStore.reload() }
        return dir
    }

    @Test
    fun firstWriteReadAndReplacementPreserveIdentityAndSerialization() {
        val dir = setUpDirectory()
        val first = operation()
        assertTrue(awaitSuspend { PendingStore.save(first) } is PendingMutationResult.Success)
        assertTrue(awaitSuspend { PendingStore.reload() } is PendingLoadResult.Success)
        assertEquals(first, PendingStore.find("local-1"))

        val replacement = first.copy(updatedAt = "2026-09-28T11:00:00-03:00", actorLabel = "Elias Júnior")
        assertTrue(awaitSuspend { PendingStore.save(replacement) } is PendingMutationResult.Success)
        assertTrue(awaitSuspend { PendingStore.reload() } is PendingLoadResult.Success)
        assertEquals("local-1", PendingStore.find("local-1")?.localId)
        assertEquals("idem-1", PendingStore.find("local-1")?.idempotencyKey)
        assertEquals("Elias Júnior", PendingStore.find("local-1")?.actorLabel)
        assertTrue(OfflineStore.pendingDir().listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        dir.deleteRecursively()
    }

    @Test
    fun invalidFileIsPreservedAndReportedAsRecoverableProblem() {
        val dir = setUpDirectory()
        val invalid = File(OfflineStore.pendingDir(), "danificada.json").apply { writeText("{não é json") }

        assertTrue(awaitSuspend { PendingStore.reload() } is PendingLoadResult.Success)

        assertTrue(invalid.exists())
        assertEquals("{não é json", invalid.readText())
        assertTrue(PendingStore.problems.value.any { it.fileName == invalid.name })
        dir.deleteRecursively()
    }

    @Test
    fun failedSaveDoesNotClaimSuccessOrReplacePreviousPending() {
        val dir = setUpDirectory()
        val first = operation()
        assertTrue(awaitSuspend { PendingStore.save(first) } is PendingMutationResult.Success)
        val file = OfflineStore.pendingDir().resolve("local-1.json")
        val previousBytes = file.readBytes()

        val result = awaitSuspend {
            PendingStore.save(first.copy(actorLabel = "Outro")) { _, _ -> throw IOException("falha simulada") }
        }

        assertTrue(result is PendingMutationResult.Failure)
        assertTrue(previousBytes.contentEquals(file.readBytes()))
        assertEquals("Elias", PendingStore.find("local-1")?.actorLabel)
        dir.deleteRecursively()
    }

    @Test
    fun successfulDeleteRemovesOnlyPendingAndFailedDeleteRemainsVisible() {
        val dir = setUpDirectory()
        val first = operation()
        assertTrue(awaitSuspend { PendingStore.save(first) } is PendingMutationResult.Success)

        val failed = awaitSuspend { PendingStore.delete(first.localId) { false } }
        assertTrue(failed is PendingMutationResult.Failure)
        assertTrue(OfflineStore.pendingDir().resolve("local-1.json").exists())
        assertEquals(first, PendingStore.find(first.localId))

        assertTrue(awaitSuspend { PendingStore.delete(first.localId) } is PendingMutationResult.Success)
        assertFalse(OfflineStore.pendingDir().resolve("local-1.json").exists())
        assertEquals(null, PendingStore.find(first.localId))
        dir.deleteRecursively()
    }

    @Test
    fun cancelledSaveDoesNotChangeLoadedStateOrStoredFile() {
        val dir = setUpDirectory()
        val first = operation()
        assertTrue(awaitSuspend { PendingStore.save(first) } is PendingMutationResult.Success)
        val file = OfflineStore.pendingDir().resolve("local-1.json")
        val previousBytes = file.readBytes()

        val failure = runCatching {
            awaitSuspend {
                PendingStore.save(first.copy(actorLabel = "Outro")) { _, _ ->
                    throw CancellationException("cancelamento simulado")
                }
            }
        }

        assertTrue(failure.exceptionOrNull() is CancellationException)
        assertTrue(previousBytes.contentEquals(file.readBytes()))
        assertEquals("Elias", PendingStore.find(first.localId)?.actorLabel)
        dir.deleteRecursively()
    }

    @Test
    fun concurrentSavesOfSamePendingKeepMemoryAndDiskConsistent() {
        val dir = setUpDirectory()
        val versions = (1..12).map { index ->
            operation(updatedAt = "2026-09-28T10:${index.toString().padStart(2, '0')}:00-03:00")
                .copy(actorLabel = "Versão $index")
        }

        val results = awaitSuspend {
            coroutineScope {
                versions.map { candidate ->
                    async(Dispatchers.Default) { PendingStore.save(candidate) }
                }.awaitAll()
            }
        }

        assertTrue(results.all { it is PendingMutationResult.Success })
        val memory = PendingStore.find("local-1")
        val disk = OfflineStore.json.decodeFromString(
            PendingOperation.serializer(),
            OfflineStore.pendingDir().resolve("local-1.json").readText(),
        )
        assertEquals(disk, memory)
        assertTrue(OfflineStore.pendingDir().listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        dir.deleteRecursively()
    }
}

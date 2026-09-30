package com.babycatbe.nevesestoque.data.offline

import com.babycatbe.nevesestoque.awaitSuspend
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Bloco 252 (go-live): o catálogo do período de testes não pode reaparecer a partir da cópia
 * local, e a invalidação nunca pode tocar em pendências nem no registro de acesso.
 */
class CacheGenerationTest {
    private fun seedOldInstall(dir: File, generation: String?) {
        val root = File(dir, "offline")
        File(root, "cache").mkdirs()
        File(root, "pending").mkdirs()
        File(root, "access").mkdirs()
        File(root, "cache/catalog-products.json").writeText("""[{"id":"old-product"}]""")
        File(root, "cache/bin-illustration").writeBytes(byteArrayOf(1, 2, 3))
        File(root, "pending/local-1.json").writeText("""{"localId":"local-1"}""")
        File(root, "access/last-access.json").writeText("""{"user":"u"}""")
        if (generation != null) File(root, "cache-generation").writeText(generation)
    }

    @Test
    fun oldGenerationClearsOnlyCacheAndKeepsPendingAndAccess() {
        val dir = Files.createTempDirectory("cache-generation-test").toFile()
        seedOldInstall(dir, generation = null)
        OfflineStore.initializeForTests(dir, awaitPrepare = true)
        awaitSuspend { OfflineStore.prepare() }

        val root = File(dir, "offline")
        assertTrue(File(root, "cache").listFiles().orEmpty().isEmpty())
        assertNull(awaitSuspend { OfflineStore.readCache("catalog-products") })
        assertEquals("""{"localId":"local-1"}""", File(root, "pending/local-1.json").readText())
        assertEquals("""{"user":"u"}""", File(root, "access/last-access.json").readText())
        assertEquals(OfflineStore.CACHE_GENERATION.toString(), File(root, "cache-generation").readText().trim())
        dir.deleteRecursively()
    }

    @Test
    fun currentGenerationKeepsCacheOnLaterOpenings() {
        val dir = Files.createTempDirectory("cache-generation-test").toFile()
        seedOldInstall(dir, generation = OfflineStore.CACHE_GENERATION.toString())
        OfflineStore.initializeForTests(dir, awaitPrepare = true)
        awaitSuspend { OfflineStore.prepare() }

        assertEquals("""[{"id":"old-product"}]""", awaitSuspend { OfflineStore.readCache("catalog-products") })
        assertTrue(File(dir, "offline/pending/local-1.json").exists())
        dir.deleteRecursively()
    }

    @Test
    fun cacheReadStartedBeforePrepareNeverSeesOldGeneration() {
        val dir = Files.createTempDirectory("cache-generation-test").toFile()
        seedOldInstall(dir, generation = "1")
        OfflineStore.initializeForTests(dir, awaitPrepare = true)

        val earlyRead = awaitSuspend {
            coroutineScope {
                val read = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    OfflineStore.readCache("catalog-products")
                }
                delay(50)
                assertFalse(read.isCompleted)
                OfflineStore.prepare()
                read.await()
            }
        }

        assertNull(earlyRead)
        assertTrue(File(dir, "offline/pending/local-1.json").exists())
        dir.deleteRecursively()
    }
}

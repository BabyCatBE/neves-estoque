package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.awaitSuspend
import com.babycatbe.nevesestoque.data.offline.OfflineStore
import com.babycatbe.nevesestoque.feature.offline.PendingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ConferenceDraftStoreTest {
    @Test
    fun partiallyFilledDraftSurvivesReloadAndDoesNotBecomeSendablePendingOperation() {
        val dir = Files.createTempDirectory("conference-draft").toFile()
        try {
            OfflineStore.initializeForTests(dir)
            val draft = LocalConferenceDraft(
                ownerId = "user-A", categoryId = "category-A",
                date = "2026-10-19", responsible = "Responsável",
                observation = "Voltar depois",
                quantities = mapOf("p1" to "0", "p2" to "", "p3" to "2,5"),
            )
            val saved = awaitSuspend { ConferenceDraftStore.save(draft) }
            assertTrue(saved is ConferenceDraftResult.Success)
            val loaded = awaitSuspend { ConferenceDraftStore.load("user-A", "category-A") }
            assertTrue(loaded is ConferenceDraftResult.Success)
            val restored = (loaded as ConferenceDraftResult.Success).value!!
            assertEquals("0", restored.quantities["p1"])
            assertEquals("", restored.quantities["p2"])
            assertEquals("2,5", restored.quantities["p3"])
            assertEquals(2, restored.filledCount(listOf("p1", "p2", "p3")))
            assertTrue(PendingStore.pending.value.none { it.kindLabel == "Conferência por Categoria" })
            awaitSuspend { OfflineStore.clearCache() }
            val afterClear = awaitSuspend { ConferenceDraftStore.load("user-A", "category-A") }
            assertTrue(afterClear is ConferenceDraftResult.Success)
            assertEquals(draft, (afterClear as ConferenceDraftResult.Success).value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun draftsAreScopedToUserAndCategoryAndNotDeletedByOtherUser() {
        val dir = Files.createTempDirectory("conference-owner").toFile()
        try {
            OfflineStore.initializeForTests(dir)
            val draft = LocalConferenceDraft("user-A", "category-A", "2026-10-19", "", "",
                mapOf("p1" to "0", "p2" to ""))
            awaitSuspend { ConferenceDraftStore.save(draft) }
            assertNull((awaitSuspend {
                ConferenceDraftStore.load("user-B", "category-A")
            } as ConferenceDraftResult.Success).value)
            assertNull((awaitSuspend {
                ConferenceDraftStore.load("user-A", "category-B")
            } as ConferenceDraftResult.Success).value)
            val owners = awaitSuspend { ConferenceDraftStore.list("user-B") }
            assertEquals(0, (owners as ConferenceDraftResult.Success).value.size)
            assertEquals(1, (awaitSuspend {
                ConferenceDraftStore.list("user-A")
            } as ConferenceDraftResult.Success).value.size)
            assertTrue(awaitSuspend {
                ConferenceDraftStore.delete("user-A", "category-A")
            } is ConferenceDraftResult.Success)
            assertNull((awaitSuspend {
                ConferenceDraftStore.load("user-A", "category-A")
            } as ConferenceDraftResult.Success).value)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun invalidDraftIsPreservedRatherThanSilentlyOverwritten() {
        val dir = Files.createTempDirectory("conference-corrupt").toFile()
        try {
            OfflineStore.initializeForTests(dir)
            val draft = LocalConferenceDraft("user-A", "category-A", "2026-10-19", "", "", mapOf("p1" to "1"))
            awaitSuspend { ConferenceDraftStore.save(draft) }
            val saved = OfflineStore.conferenceDraftsDir().listFiles()!!.single()
            saved.writeText("{invalid json")
            assertTrue(awaitSuspend {
                ConferenceDraftStore.load("user-A", "category-A")
            } is ConferenceDraftResult.Failure)
            assertTrue(saved.exists())
            assertFalse(saved.readText().contains("2026-10-19"))
        } finally {
            dir.deleteRecursively()
        }
    }
}

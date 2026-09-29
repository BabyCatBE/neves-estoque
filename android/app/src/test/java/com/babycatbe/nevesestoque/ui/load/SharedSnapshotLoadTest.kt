package com.babycatbe.nevesestoque.ui.load

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SharedSnapshotLoadTest {

    @Test
    fun openingReusesPreloadInsteadOfRepeatingQuery() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger()
        val response = CompletableDeferred<String>()
        val shared = SharedSnapshotLoad(
            loadOfficial = { calls.incrementAndGet(); response.await() },
            loadSnapshot = { null },
            scope = scope,
        )

        shared.warm()
        val screen = async { shared.load(reuseInFlight = true) }
        yield()
        response.complete("oficial")

        assertEquals("oficial", withTimeout(5_000) { screen.await() })
        assertEquals(1, calls.get())
        assertEquals("oficial", shared.peek())
        scope.cancel()
    }

    @Test
    fun manualRefreshAlwaysFetchesAgain() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger()
        val shared = SharedSnapshotLoad(
            loadOfficial = { "leitura-" + calls.incrementAndGet() },
            loadSnapshot = { null },
            scope = scope,
        )

        assertEquals("leitura-1", shared.load(reuseInFlight = true))
        assertEquals("leitura-2", shared.load(reuseInFlight = false))
        assertEquals(2, calls.get())
        scope.cancel()
    }

    @Test
    fun olderResultNeverReplacesNewerOne() = runBlocking {
        // Unconfined: cada leitura começa na ordem em que foi pedida (teste determinístico).
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val responses = listOf(CompletableDeferred<String>(), CompletableDeferred())
        val index = AtomicInteger()
        val shared = SharedSnapshotLoad(
            loadOfficial = { responses[index.getAndIncrement()].await() },
            loadSnapshot = { null },
            scope = scope,
        )

        val older = async { shared.load(reuseInFlight = false) }
        yield()
        val newer = async { shared.load(reuseInFlight = false) }
        yield()
        responses[1].complete("novo")
        withTimeout(5_000) { newer.await() }
        responses[0].complete("antigo")
        withTimeout(5_000) { older.await() }

        assertEquals("novo", shared.peek())
        scope.cancel()
    }

    @Test
    fun snapshotShowsLocalCopyWithoutNetworkAndClearDiscardsIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val shared = SharedSnapshotLoad(
            loadOfficial = { "oficial" },
            loadSnapshot = { "copia-local" },
            scope = scope,
        )

        assertEquals("copia-local", shared.snapshotOrNull())
        shared.clear()
        assertNull(shared.peek())
        scope.cancel()
    }

    @Test
    fun brokenLocalCopyIsIgnored() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val shared = SharedSnapshotLoad<String>(
            loadOfficial = { "oficial" },
            loadSnapshot = { error("cópia corrompida") },
            scope = scope,
        )

        assertNull(shared.snapshotOrNull())
        scope.cancel()
    }
}

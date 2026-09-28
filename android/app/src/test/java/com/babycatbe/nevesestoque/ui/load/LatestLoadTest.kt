package com.babycatbe.nevesestoque.ui.load

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LatestLoadTest {

    @Test
    fun newerLoadCancelsOlderAndOlderResultNeverWins() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val latest = LatestLoad()
        val oldResponse = CompletableDeferred<String>()
        val newResponse = CompletableDeferred<String>()
        val applied = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()

        val old = latest.launch(scope) {
            loadCatching { oldResponse.await() }
                .onSuccess { synchronized(applied) { applied += it } }
                .onFailure { synchronized(errors) { errors += it } }
        }
        val new = latest.launch(scope) {
            loadCatching { newResponse.await() }
                .onSuccess { synchronized(applied) { applied += it } }
                .onFailure { synchronized(errors) { errors += it } }
        }

        oldResponse.complete("antigo")
        newResponse.complete("novo")
        old.join()
        new.join()

        assertTrue(old.isCancelled)
        assertEquals(listOf("novo"), applied)
        // Cancelamento não vira mensagem de erro na tela.
        assertEquals(emptyList<Throwable>(), errors)
        scope.cancel()
    }

    @Test
    fun loadCatchingKeepsOrdinaryFailures() = runBlocking {
        val result = loadCatching<Int> { error("sem rede") }
        assertEquals("sem rede", result.exceptionOrNull()?.message)
    }

    @Test
    fun loadCatchingRethrowsCancellation() = runBlocking {
        try {
            loadCatching<Int> {
                yield()
                throw CancellationException("cancelada")
            }
            fail("cancelamento deveria ser propagado")
        } catch (_: CancellationException) {
        }
    }
}

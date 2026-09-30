package com.babycatbe.nevesestoque.data.supabase

import com.babycatbe.nevesestoque.awaitSuspend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloco 252: cabeçalhos buscados por id em blocos (filtro `in` no servidor), em vez de ler a
 * tabela inteira e filtrar no aparelho.
 */
class IdChunksTest {
    @Test
    fun emptyIdsMakeNoRequest() {
        var calls = 0
        val result = awaitSuspend { fetchByIdChunks<String>(emptyList()) { calls += 1; emptyList() } }
        assertTrue(result.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun idsAreDeduplicatedAndSplitIntoBoundedChunks() {
        val requested = mutableListOf<List<String>>()
        val ids = (1..250).map { "id-$it" } + listOf("id-1", "id-2")
        val result = awaitSuspend {
            fetchByIdChunks(ids, chunkSize = 100) { chunk ->
                requested += chunk
                chunk.map { "row-$it" }
            }
        }
        assertEquals(listOf(100, 100, 50), requested.map { it.size })
        assertEquals(250, result.size)
        assertEquals(250, result.toSet().size)
        assertTrue(requested.all { it.size <= 100 })
    }
}

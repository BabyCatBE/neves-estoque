package com.babycatbe.nevesestoque.data.supabase

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Executa um bloco suspend que não suspende de fato (fontes falsas síncronas). */
fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return checkNotNull(outcome) { "o bloco suspendeu; use apenas fontes síncronas nos testes" }.getOrThrow()
}

/** UUID canônico determinístico para testes (ordem textual = ordem numérica). */
fun testUuid(prefix: Int, index: Int): String {
    val hex = (prefix.toLong() * 1_000_000L + index).toString(16).padStart(12, '0')
    return "00000000-0000-4000-8000-$hex"
}

/**
 * "Servidor" falso de uma tabela: aplica filtro, cursor `id > afterId`, ordem por id,
 * o limit pedido e um teto de linhas por resposta ([maxRows]), que pode ser menor que o
 * pedido. Registra quantas linhas cada requisição devolveu.
 */
class FakeKeysetTable<T>(
    rows: List<T>,
    private val idOf: (T) -> String,
    private val maxRows: Int? = null,
    private val failOnRequest: Int? = null,
) {
    private val sorted = rows.sortedBy(idOf)
    val returned = mutableListOf<Int>()
    val afterIds = mutableListOf<String?>()

    fun page(afterId: String?, limit: Long): List<T> {
        afterIds += afterId
        if (failOnRequest == returned.size + 1) {
            returned += 0
            throw IllegalStateException("falha simulada")
        }
        var page = sorted.filter { afterId == null || idOf(it) > afterId }.take(limit.toInt())
        if (maxRows != null) page = page.take(maxRows)
        returned += page.size
        return page
    }

    fun readAll(pageSize: Long = DEFAULT_KEYSET_PAGE_SIZE, maxPages: Int = DEFAULT_KEYSET_MAX_PAGES): List<T> =
        runSuspend { fetchAllByIdKeyset(idOf, pageSize, maxPages) { afterId, limit -> page(afterId, limit) } }
}

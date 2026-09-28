package com.babycatbe.nevesestoque.data.supabase

/**
 * Leitura completa e determinística de tabelas históricas via PostgREST.
 *
 * Paridade com a Web (src/shared/lib/keysetPagination.ts).
 *
 * Por que cursor (keyset) e não `range(from, to)`:
 * - o servidor pode devolver MENOS linhas do que o pedido (limite `max-rows` do
 *   PostgREST/Supabase). A regra "página menor que o tamanho pedido = acabou" trunca o
 *   histórico em silêncio quando esse limite é menor que o pedido;
 * - com cursor por `id` (chave primária, única e imutável), cada página pede
 *   `id > último id recebido`, ordenado por `id`. Linhas que existem durante toda a
 *   leitura não são puladas nem repetidas, mesmo que o servidor corte a página.
 *
 * Regra de término: somente uma página VAZIA encerra a leitura.
 */
const val DEFAULT_KEYSET_PAGE_SIZE = 500L
const val DEFAULT_KEYSET_MAX_PAGES = 10_000

class IncompletePaginationException(message: String) : IllegalStateException(message)

/**
 * Lê todas as páginas, em ordem crescente de `id`, até receber uma página vazia.
 *
 * Nunca devolve resultado parcial: qualquer exceção de [fetchPage] é propagada, e a
 * leitura falha com [IncompletePaginationException] quando o servidor devolve linhas
 * fora da ordem crescente estrita de `id` ou quando passa de [maxPages] páginas.
 *
 * @param fetchPage recebe `afterId` (`null` na primeira página) e o tamanho pedido.
 */
suspend fun <T> fetchAllByIdKeyset(
    idOf: (T) -> String,
    pageSize: Long = DEFAULT_KEYSET_PAGE_SIZE,
    maxPages: Int = DEFAULT_KEYSET_MAX_PAGES,
    fetchPage: suspend (afterId: String?, limit: Long) -> List<T>,
): List<T> {
    require(pageSize > 0) { "pageSize deve ser positivo." }
    require(maxPages > 0) { "maxPages deve ser positivo." }

    val rows = mutableListOf<T>()
    var afterId: String? = null
    var pages = 0

    while (true) {
        if (pages >= maxPages) {
            throw IncompletePaginationException(
                "Leitura interrompida: mais de $maxPages páginas. O histórico não foi carregado por completo."
            )
        }
        val page = fetchPage(afterId, pageSize)
        pages += 1
        if (page.isEmpty()) break

        for (row in page) {
            val id = idOf(row)
            // UUIDs canônicos (hex minúsculo, hífens em posição fixa) comparam como texto
            // na mesma ordem em que o PostgreSQL ordena o tipo uuid.
            val previous = afterId
            if (previous != null && id <= previous) {
                throw IncompletePaginationException(
                    "Leitura interrompida: o servidor devolveu registros fora da ordem esperada."
                )
            }
            rows += row
            afterId = id
        }
    }
    return rows
}

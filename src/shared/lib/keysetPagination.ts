/**
 * Leitura completa e determinística de tabelas históricas via PostgREST.
 *
 * Por que cursor (keyset) e não `range(from, to)`:
 * - o servidor pode devolver MENOS linhas do que o pedido (limite `max-rows` do
 *   PostgREST/Supabase). A regra "página menor que o tamanho pedido = acabou"
 *   trunca o histórico em silêncio quando esse limite é menor que o pedido;
 * - com cursor por `id` (chave primária, única e imutável), cada página pede
 *   `id > último id recebido`, ordenado por `id`. Linhas que existem durante toda a
 *   leitura não são puladas nem repetidas, mesmo que o servidor corte a página.
 *
 * Regra de término: somente uma página VAZIA encerra a leitura. Uma página curta
 * não prova que não há mais registros.
 */

export const DEFAULT_KEYSET_PAGE_SIZE = 500;
export const DEFAULT_KEYSET_MAX_PAGES = 10_000;

export type KeysetPageResult<T> = {
  data: T[] | null;
  error: unknown;
};

export type KeysetPageRequest = {
  /** `null` na primeira página; depois, o `id` da última linha recebida. */
  afterId: string | null;
  /** Tamanho pedido ao servidor (o servidor pode devolver menos). */
  limit: number;
};

export type KeysetFetchPage<T> = (
  request: KeysetPageRequest
) => PromiseLike<KeysetPageResult<T>>;

export type KeysetOptions = {
  pageSize?: number;
  maxPages?: number;
};

export class IncompletePaginationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "IncompletePaginationError";
  }
}

/**
 * Lê todas as páginas, em ordem crescente de `id`, até receber uma página vazia.
 *
 * Falha (lança erro, nunca devolve resultado parcial) quando:
 * - qualquer página devolve erro;
 * - o servidor devolve linhas fora da ordem crescente estrita de `id` (o que
 *   poderia duplicar ou perder linhas);
 * - o número de páginas passa de `maxPages` (proteção contra laço infinito).
 */
export async function fetchAllByIdKeyset<T extends { id: string }>(
  fetchPage: KeysetFetchPage<T>,
  options: KeysetOptions = {}
): Promise<T[]> {
  const pageSize = options.pageSize ?? DEFAULT_KEYSET_PAGE_SIZE;
  const maxPages = options.maxPages ?? DEFAULT_KEYSET_MAX_PAGES;

  if (!Number.isInteger(pageSize) || pageSize <= 0) {
    throw new RangeError("pageSize deve ser um inteiro positivo.");
  }
  if (!Number.isInteger(maxPages) || maxPages <= 0) {
    throw new RangeError("maxPages deve ser um inteiro positivo.");
  }

  const rows: T[] = [];
  let afterId: string | null = null;

  for (let pageIndex = 0; ; pageIndex += 1) {
    if (pageIndex >= maxPages) {
      throw new IncompletePaginationError(
        `Leitura interrompida: mais de ${maxPages} páginas. O histórico não foi carregado por completo.`
      );
    }

    const result = await fetchPage({ afterId, limit: pageSize });
    if (result.error) throw result.error;

    const page = result.data ?? [];
    if (page.length === 0) break;

    for (const row of page) {
      // UUIDs canônicos (hex minúsculo, hífens em posição fixa) comparam como
      // texto na mesma ordem que o PostgreSQL ordena o tipo uuid.
      if (afterId !== null && !(row.id > afterId)) {
        throw new IncompletePaginationError(
          "Leitura interrompida: o servidor devolveu registros fora da ordem esperada."
        );
      }
      rows.push(row);
      afterId = row.id;
    }
  }

  return rows;
}

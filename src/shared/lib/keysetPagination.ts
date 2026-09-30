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
export const DEFAULT_ID_CHUNK_SIZE = 100;

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

/** Cursor numérico (ex.: `bigint generated always as identity`). */
export type NumericKeysetPageRequest = {
  /** `null` na primeira página; depois, o `id` da última linha recebida. */
  afterId: number | null;
  /** Tamanho pedido ao servidor (o servidor pode devolver menos). */
  limit: number;
};

export type NumericKeysetFetchPage<T> = (
  request: NumericKeysetPageRequest
) => PromiseLike<KeysetPageResult<T>>;

async function fetchAllByKeyset<T, K extends string | number>(
  fetchPage: (request: { afterId: K | null; limit: number }) => PromiseLike<KeysetPageResult<T>>,
  keyOf: (row: T) => K,
  options: KeysetOptions
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
  let afterId: K | null = null;

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
      const key = keyOf(row);
      // UUIDs canônicos (hex minúsculo, hífens em posição fixa) comparam como
      // texto na mesma ordem que o PostgreSQL ordena o tipo uuid; ids numéricos
      // comparam numericamente.
      if (afterId !== null && !(key > afterId)) {
        throw new IncompletePaginationError(
          "Leitura interrompida: o servidor devolveu registros fora da ordem esperada."
        );
      }
      rows.push(row);
      afterId = key;
    }
  }

  return rows;
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
export function fetchAllByIdKeyset<T extends { id: string }>(
  fetchPage: KeysetFetchPage<T>,
  options: KeysetOptions = {}
): Promise<T[]> {
  return fetchAllByKeyset<T, string>(fetchPage, (row) => row.id, options);
}

/** Mesma regra de `fetchAllByIdKeyset`, para chave primária numérica. */
export function fetchAllByNumericIdKeyset<T extends { id: number }>(
  fetchPage: NumericKeysetFetchPage<T>,
  options: KeysetOptions = {}
): Promise<T[]> {
  return fetchAllByKeyset<T, number>(fetchPage, (row) => row.id, options);
}


export type IdChunkOptions = {
  chunkSize?: number;
};

/**
 * Divide uma lista de ids em blocos pequenos para filtros `in (...)`.
 *
 * Útil para tabelas em que cada id solicitado devolve no máximo uma linha
 * (cabeçalhos de Entrada/Conferência, Produtos, Fornecedores etc.). Evita URLs
 * enormes e respostas cortadas pelo teto de linhas do servidor.
 */
export async function fetchByIdChunks<T>(
  ids: Iterable<string>,
  fetchChunk: (ids: string[]) => PromiseLike<KeysetPageResult<T>>,
  options: IdChunkOptions = {}
): Promise<T[]> {
  const chunkSize = options.chunkSize ?? DEFAULT_ID_CHUNK_SIZE;
  if (!Number.isInteger(chunkSize) || chunkSize <= 0) {
    throw new RangeError("chunkSize deve ser um inteiro positivo.");
  }

  const distinctIds = [...new Set(ids)];
  if (distinctIds.length === 0) return [];

  const rows: T[] = [];
  for (let index = 0; index < distinctIds.length; index += chunkSize) {
    const chunk = distinctIds.slice(index, index + chunkSize);
    const result = await fetchChunk(chunk);
    if (result.error) throw result.error;
    rows.push(...(result.data ?? []));
  }
  return rows;
}

/**
 * Variante para tabelas-filhas em que um id de filtro pode devolver várias linhas.
 * Cada bloco de ids é paginado por `id`, portanto continua correto mesmo quando
 * o servidor limita uma resposta a menos linhas que o solicitado.
 */
export async function fetchAllByIdKeysetInChunks<T extends { id: string }>(
  ids: Iterable<string>,
  fetchPage: (ids: string[], request: KeysetPageRequest) => PromiseLike<KeysetPageResult<T>>,
  options: KeysetOptions & IdChunkOptions = {}
): Promise<T[]> {
  const chunkSize = options.chunkSize ?? DEFAULT_ID_CHUNK_SIZE;
  if (!Number.isInteger(chunkSize) || chunkSize <= 0) {
    throw new RangeError("chunkSize deve ser um inteiro positivo.");
  }

  const distinctIds = [...new Set(ids)];
  if (distinctIds.length === 0) return [];

  const rows: T[] = [];
  for (let index = 0; index < distinctIds.length; index += chunkSize) {
    const chunk = distinctIds.slice(index, index + chunkSize);
    const chunkRows = await fetchAllByIdKeyset<T>(
      (request) => fetchPage(chunk, request),
      options
    );
    rows.push(...chunkRows);
  }
  return rows;
}

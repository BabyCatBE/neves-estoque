/**
 * Cliente PostgREST falso, em memória, SOMENTE para testes.
 *
 * Simula o que importa para a integridade da paginação:
 * - filtros usados pelos módulos (is/eq/lt/lte/gt/in), ordenação, limit e range;
 * - `maxRows`: teto de linhas por resposta imposto pelo "servidor", menor que o
 *   tamanho pedido pelo cliente — exatamente o cenário que trunca leituras que
 *   param em "página menor que o pedido";
 * - falha injetada em uma requisição específica de uma tabela.
 */

export type FakeRow = Record<string, unknown>;

export type FakeRequestLog = {
  table: string;
  filters: string[];
  limit: number | null;
  returned: number;
};

type Filter = (row: FakeRow) => boolean;

type FakeResponse = { data: FakeRow[] | null; error: { message: string } | null };

export type FakeQueryBuilder = PromiseLike<FakeResponse> & {
  select(columnList: string): FakeQueryBuilder;
  is(column: string, value: null): FakeQueryBuilder;
  eq(column: string, value: unknown): FakeQueryBuilder;
  lt(column: string, value: unknown): FakeQueryBuilder;
  lte(column: string, value: unknown): FakeQueryBuilder;
  gt(column: string, value: unknown): FakeQueryBuilder;
  in(column: string, values: readonly unknown[]): FakeQueryBuilder;
  order(column: string, config?: { ascending?: boolean }): FakeQueryBuilder;
  limit(count: number): FakeQueryBuilder;
  range(fromIndex: number, toIndex: number): FakeQueryBuilder;
};

export type FakePostgrestOptions = {
  tables: Record<string, FakeRow[]>;
  /** Teto de linhas por resposta (padrão: sem teto). */
  maxRows?: number;
  /** Falha a N-ésima requisição (base 1) da tabela indicada. */
  failOn?: { table: string; request: number; message?: string };
};

function compareValues(column: string, left: unknown, right: unknown) {
  if (column.endsWith("_at") && typeof left === "string" && typeof right === "string") {
    return new Date(left).getTime() - new Date(right).getTime();
  }
  if (typeof left === "number" && typeof right === "number") return left - right;
  const a = String(left);
  const b = String(right);
  return a < b ? -1 : a > b ? 1 : 0;
}

export function createFakePostgrest(options: FakePostgrestOptions) {
  const requests: FakeRequestLog[] = [];
  const requestCountByTable = new Map<string, number>();

  function from(table: string): FakeQueryBuilder {
    const source = options.tables[table] ?? [];
    const filters: Filter[] = [];
    const filterLabels: string[] = [];
    const orders: Array<{ column: string; ascending: boolean }> = [];
    let columns: string[] | null = null;
    let limitValue: number | null = null;
    let rangeValue: { from: number; to: number } | null = null;

    const builder: FakeQueryBuilder = {
      select(columnList: string) {
        columns = columnList.split(",").map((column) => column.trim());
        return builder;
      },
      is(column: string, value: null) {
        filters.push((row) => (row[column] ?? null) === value);
        filterLabels.push(`${column}.is.${String(value)}`);
        return builder;
      },
      eq(column: string, value: unknown) {
        filters.push((row) => compareValues(column, row[column], value) === 0);
        filterLabels.push(`${column}.eq.${String(value)}`);
        return builder;
      },
      lt(column: string, value: unknown) {
        filters.push((row) => compareValues(column, row[column], value) < 0);
        filterLabels.push(`${column}.lt.${String(value)}`);
        return builder;
      },
      lte(column: string, value: unknown) {
        filters.push((row) => compareValues(column, row[column], value) <= 0);
        filterLabels.push(`${column}.lte.${String(value)}`);
        return builder;
      },
      gt(column: string, value: unknown) {
        filters.push((row) => compareValues(column, row[column], value) > 0);
        filterLabels.push(`${column}.gt.${String(value)}`);
        return builder;
      },
      in(column: string, values: readonly unknown[]) {
        filters.push((row) => values.includes(row[column]));
        filterLabels.push(`${column}.in.(${values.length})`);
        return builder;
      },
      order(column: string, config: { ascending?: boolean } = {}) {
        orders.push({ column, ascending: config.ascending ?? true });
        return builder;
      },
      limit(count: number) {
        limitValue = count;
        return builder;
      },
      range(fromIndex: number, toIndex: number) {
        rangeValue = { from: fromIndex, to: toIndex };
        return builder;
      },
      then<TResult1 = FakeResponse, TResult2 = never>(
        onfulfilled?: ((value: FakeResponse) => TResult1 | PromiseLike<TResult1>) | null,
        onrejected?: ((reason: unknown) => TResult2 | PromiseLike<TResult2>) | null
      ): Promise<TResult1 | TResult2> {
        return Promise.resolve(execute()).then(onfulfilled, onrejected);
      }
    };

    function execute(): FakeResponse {
      const requestNumber = (requestCountByTable.get(table) ?? 0) + 1;
      requestCountByTable.set(table, requestNumber);

      if (options.failOn?.table === table && options.failOn.request === requestNumber) {
        requests.push({ table, filters: filterLabels, limit: limitValue, returned: 0 });
        return { data: null, error: { message: options.failOn.message ?? "falha simulada" } };
      }

      let rows = source.filter((row) => filters.every((filter) => filter(row)));
      if (orders.length) {
        rows = [...rows].sort((a, b) => {
          for (const order of orders) {
            const diff = compareValues(order.column, a[order.column], b[order.column]);
            if (diff !== 0) return order.ascending ? diff : -diff;
          }
          return 0;
        });
      }
      if (rangeValue) rows = rows.slice(rangeValue.from, rangeValue.to + 1);
      if (limitValue !== null) rows = rows.slice(0, limitValue);
      if (options.maxRows !== undefined) rows = rows.slice(0, options.maxRows);

      const projected = rows.map((row) => {
        if (!columns) return { ...row };
        return Object.fromEntries(columns.map((column) => [column, row[column] ?? null]));
      });

      requests.push({ table, filters: filterLabels, limit: limitValue, returned: projected.length });
      return { data: projected, error: null };
    }

    return builder;
  }

  return {
    client: { from },
    requests,
    requestsFor(table: string) {
      return requests.filter((request) => request.table === table);
    }
  };
}

/** UUID canônico determinístico para testes (ordem textual = ordem numérica). */
export function testUuid(prefix: number, index: number) {
  const hex = (prefix * 1_000_000 + index).toString(16).padStart(12, "0");
  return `00000000-0000-4000-8000-${hex}`;
}

import { describe, expect, it } from "vitest";
import { createFakePostgrest, testUuid, type FakeRow } from "../testing/fakePostgrest";
import {
  fetchAllByIdKeyset,
  IncompletePaginationError,
  type KeysetPageRequest
} from "./keysetPagination";

type Row = { id: string; value: number };

function makeRows(count: number): Row[] {
  // Inseridos fora de ordem de propósito: a leitura deve devolver em ordem de id.
  return Array.from({ length: count }, (_, index) => ({
    id: testUuid(1, index + 1),
    value: index + 1
  })).reverse();
}

function sortedIds(rows: Row[]) {
  return rows.map((row) => row.id).sort();
}

function readTable(rows: Row[], pageSize: number, maxRows?: number) {
  const fake = createFakePostgrest({ tables: { items: rows as FakeRow[] }, maxRows });
  const fetchPage = ({ afterId, limit }: KeysetPageRequest) => {
    let query = fake.client.from("items").select("id,value");
    if (afterId !== null) query = query.gt("id", afterId);
    return query.order("id", { ascending: true }).limit(limit);
  };
  return {
    fake,
    result: fetchAllByIdKeyset(
      fetchPage as unknown as (request: KeysetPageRequest) => PromiseLike<{
        data: Row[] | null;
        error: unknown;
      }>,
      { pageSize }
    )
  };
}

describe("fetchAllByIdKeyset", () => {
  it("zero registros: uma única requisição e lista vazia", async () => {
    const { fake, result } = readTable([], 3);
    await expect(result).resolves.toEqual([]);
    expect(fake.requests).toHaveLength(1);
  });

  it("menos de uma página: lê tudo e confirma o fim com página vazia", async () => {
    const rows = makeRows(2);
    const { fake, result } = readTable(rows, 3);
    const read = await result;
    expect(read.map((row) => row.id)).toEqual(sortedIds(rows));
    expect(fake.requests.map((request) => request.returned)).toEqual([2, 0]);
  });

  it("exatamente uma página", async () => {
    const rows = makeRows(3);
    const { fake, result } = readTable(rows, 3);
    const read = await result;
    expect(read.map((row) => row.id)).toEqual(sortedIds(rows));
    expect(fake.requests.map((request) => request.returned)).toEqual([3, 0]);
  });

  it("mais de uma página", async () => {
    const rows = makeRows(4);
    const { fake, result } = readTable(rows, 3);
    const read = await result;
    expect(read.map((row) => row.id)).toEqual(sortedIds(rows));
    expect(fake.requests.map((request) => request.returned)).toEqual([3, 1, 0]);
  });

  it("três páginas completas: sem perda, sem duplicação, ordem estável", async () => {
    const rows = makeRows(9);
    const { fake, result } = readTable(rows, 3);
    const read = await result;
    const ids = read.map((row) => row.id);
    expect(ids).toEqual(sortedIds(rows));
    expect(new Set(ids).size).toBe(9);
    expect(fake.requests.map((request) => request.returned)).toEqual([3, 3, 3, 0]);
    // Cada página continua exatamente de onde a anterior parou.
    expect(fake.requests[1]?.filters).toContain(`id.gt.${ids[2]}`);
    expect(fake.requests[2]?.filters).toContain(`id.gt.${ids[5]}`);
  });

  it("servidor devolve menos que o pedido: continua até a página vazia", async () => {
    // Pedido de 500, servidor corta em 2 (teto do servidor menor que o pedido).
    const rows = makeRows(7);
    const { fake, result } = readTable(rows, 500, 2);
    const read = await result;
    expect(read.map((row) => row.id)).toEqual(sortedIds(rows));
    expect(fake.requests.map((request) => request.returned)).toEqual([2, 2, 2, 1, 0]);
  });

  it("é determinística: duas leituras devolvem a mesma sequência", async () => {
    const rows = makeRows(8);
    const first = await readTable(rows, 3).result;
    const second = await readTable([...rows].reverse(), 3).result;
    expect(second).toEqual(first);
  });

  it("erro em página intermediária: rejeita e não devolve resultado parcial", async () => {
    let calls = 0;
    const rows = [...makeRows(9)].sort((a, b) => (a.id < b.id ? -1 : 1));
    const read = fetchAllByIdKeyset<Row>(
      async ({ afterId, limit }) => {
        calls += 1;
        if (calls === 2) return { data: null, error: new Error("falha na página 2") };
        const start = afterId === null ? 0 : rows.findIndex((row) => row.id === afterId) + 1;
        return { data: rows.slice(start, start + limit), error: null };
      },
      { pageSize: 3 }
    );
    await expect(read).rejects.toThrow("falha na página 2");
    expect(calls).toBe(2);
  });

  it("servidor fora de ordem: falha em vez de arriscar duplicar ou perder linhas", async () => {
    const read = fetchAllByIdKeyset<Row>(
      async ({ afterId }) => ({
        data:
          afterId === null
            ? [
                { id: testUuid(1, 2), value: 2 },
                { id: testUuid(1, 1), value: 1 }
              ]
            : [],
        error: null
      }),
      { pageSize: 3 }
    );
    await expect(read).rejects.toBeInstanceOf(IncompletePaginationError);
  });

  it("servidor que ignora o cursor: falha em vez de repetir linhas", async () => {
    const read = fetchAllByIdKeyset<Row>(
      async () => ({ data: [{ id: testUuid(1, 1), value: 1 }], error: null }),
      { pageSize: 3 }
    );
    await expect(read).rejects.toBeInstanceOf(IncompletePaginationError);
  });

  it("limite de páginas: falha em vez de devolver histórico incompleto", async () => {
    const rows = [...makeRows(10)].sort((a, b) => (a.id < b.id ? -1 : 1));
    const read = fetchAllByIdKeyset<Row>(
      async ({ afterId, limit }) => {
        const start = afterId === null ? 0 : rows.findIndex((row) => row.id === afterId) + 1;
        return { data: rows.slice(start, start + limit), error: null };
      },
      { pageSize: 2, maxPages: 3 }
    );
    await expect(read).rejects.toBeInstanceOf(IncompletePaginationError);
  });

  it("rejeita pageSize inválido", async () => {
    await expect(
      fetchAllByIdKeyset<Row>(async () => ({ data: [], error: null }), { pageSize: 0 })
    ).rejects.toBeInstanceOf(RangeError);
  });
});

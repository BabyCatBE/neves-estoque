import { describe, expect, it } from "vitest";
import {
  matchesAnySearchText,
  matchesSearchText,
  normalizeSearchText
} from "./searchText";

describe("searchText", () => {
  it("ignora acentos, caixa e espaços excedentes", () => {
    expect(normalizeSearchText("  PÃO   FRANCÊS ")).toBe("pao frances");
  });

  it("encontra texto acentuado com pesquisa sem acento", () => {
    expect(matchesSearchText("Pão Francês", "pao")).toBe(true);
  });

  it("pesquisa em vários campos", () => {
    expect(
      matchesAnySearchText(["João", "Distribuidora São José", "(75) 99999-9999"], "sao jose")
    ).toBe(true);
  });
});

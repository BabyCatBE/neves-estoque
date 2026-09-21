import { describe, expect, it } from "vitest";
import { normalizeProductName } from "./normalizeProductName";

describe("normalizeProductName", () => {
  it("ignora caixa e espaços excedentes", () => {
    expect(normalizeProductName("  Farinha   de Trigo  ")).toBe(normalizeProductName("farinha de trigo"));
  });

  it("não remove acentos", () => {
    expect(normalizeProductName("Cafe")).not.toBe(normalizeProductName("Café"));
  });
});

import { describe, expect, it } from "vitest";
import {
  buildEditedConferenceEffectiveAt,
  localDayRange,
  parseConferenceQuantity
} from "./conferenceValidation";

describe("parseConferenceQuantity", () => {
  it("aceita zero como contagem física válida", () => {
    expect(parseConferenceQuantity("0")).toBe(0);
  });

  it("aceita vírgula decimal", () => {
    expect(parseConferenceQuantity("2,5")).toBe(2.5);
  });

  it("rejeita campo em branco", () => {
    expect(() => parseConferenceQuantity(" ")).toThrow("Quantidade é obrigatória.");
  });

  it("rejeita quantidade negativa", () => {
    expect(() => parseConferenceQuantity("-1")).toThrow("Quantidade inválida.");
  });
});

describe("datas de Conferência", () => {
  it("preserva o horário original ao corrigir somente a data", () => {
    const original = new Date(2026, 8, 24, 14, 35, 20, 100);
    const updated = new Date(
      buildEditedConferenceEffectiveAt("2026-09-20", original.toISOString())
    );

    expect(updated.getHours()).toBe(14);
    expect(updated.getMinutes()).toBe(35);
    expect(updated.getSeconds()).toBe(20);
  });

  it("gera intervalo local de um único dia", () => {
    const { start, end } = localDayRange("2026-09-24");
    expect(new Date(end).getTime() - new Date(start).getTime()).toBeGreaterThanOrEqual(
      23 * 60 * 60 * 1000
    );
    expect(new Date(end).getTime() - new Date(start).getTime()).toBeLessThanOrEqual(
      25 * 60 * 60 * 1000
    );
  });
});

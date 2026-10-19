import { describe, expect, it } from "vitest";
import { conferenceDraftFilledCount, conferenceDraftKey, type ConferenceDraft } from "./conferenceDrafts";

describe("rascunho local de Conferência", () => {
  it("não confunde zero preenchido com campo vazio ou produto ausente", () => {
    const draft: ConferenceDraft = {
      id: "user|category",
      ownerId: "user",
      categoryId: "category",
      date: "2026-10-19",
      responsible: "",
      observation: "",
      quantities: { p1: "0", p2: "", p3: "  ", p4: "2,5" },
      updatedAt: "2026-10-19T10:00:00Z"
    };
    expect(conferenceDraftFilledCount(draft, ["p1", "p2", "p3", "p4", "p5"])).toBe(2);
  });

  it("isola identificadores por usuário e categoria", () => {
    expect(conferenceDraftKey("user-A", "category")).not.toBe(conferenceDraftKey("user-B", "category"));
    expect(conferenceDraftKey("user-A", "category")).not.toBe(conferenceDraftKey("user-A", "other"));
    expect(() => conferenceDraftKey("", "category")).toThrow();
  });
});

import { describe, expect, it } from "vitest";
import type { EntryConferenceConflict } from "../../entries/api/entries";
import { buildEntryConflictPositions } from "./pendingSync";

function conflict(id: string, effectiveAt: string): EntryConferenceConflict {
  return {
    conferenceId: id,
    effectiveAt,
    physicalResponsible: "Responsável",
    registeredByLabel: "Usuário",
    deviceLabel: "PC",
    items: []
  };
}

describe("offline pending sync", () => {
  it("oferece antes e depois para uma Conferência relevante", () => {
    const options = buildEntryConflictPositions(
      [conflict("c1", "2026-09-27T15:00:00.000Z")],
      "2026-09-27T14:00:00.000Z"
    );

    expect(options.map((item) => item.id)).toEqual(["before-first", "after-last"]);
    const before = options[0];
    const after = options[1];
    expect(before).toBeDefined();
    expect(after).toBeDefined();
    if (!before || !after) throw new Error("Posições esperadas não foram geradas.");

    expect(new Date(before.effectiveAt).getTime()).toBeLessThan(
      new Date("2026-09-27T15:00:00.000Z").getTime()
    );
    expect(new Date(after.effectiveAt).getTime()).toBeGreaterThan(
      new Date("2026-09-27T15:00:00.000Z").getTime()
    );
  });

  it("oferece posição intermediária quando há duas Conferências em horários distintos", () => {
    const options = buildEntryConflictPositions(
      [
        conflict("c1", "2026-09-27T12:00:00.000Z"),
        conflict("c2", "2026-09-27T18:00:00.000Z")
      ],
      "2026-09-27T14:00:00.000Z"
    );

    expect(options.some((item) => item.id === "between-0")).toBe(true);
  });
});

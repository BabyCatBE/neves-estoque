import { describe, expect, it } from "vitest";
import { isNextEntryShortcut } from "./entrySequenceShortcut";

type ShortcutEvent = Parameters<typeof isNextEntryShortcut>[0];

function keyEvent(overrides: Partial<ShortcutEvent> = {}): ShortcutEvent {
  return {
    key: "Enter",
    ctrlKey: true,
    metaKey: false,
    shiftKey: true,
    altKey: false,
    repeat: false,
    ...overrides
  };
}

describe("atalho para lançar outra Entrada", () => {
  it("aceita Ctrl + Shift + Enter no PC", () => {
    expect(isNextEntryShortcut(keyEvent())).toBe(true);
  });

  it("aceita Cmd + Shift + Enter no Mac", () => {
    expect(isNextEntryShortcut(keyEvent({ ctrlKey: false, metaKey: true }))).toBe(true);
  });

  it("não confunde com Ctrl + Enter, usado para salvar", () => {
    expect(isNextEntryShortcut(keyEvent({ shiftKey: false }))).toBe(false);
  });

  it("ignora repetição de tecla e outras combinações", () => {
    expect(isNextEntryShortcut(keyEvent({ repeat: true }))).toBe(false);
    expect(isNextEntryShortcut(keyEvent({ altKey: true }))).toBe(false);
    expect(isNextEntryShortcut(keyEvent({ key: "N" }))).toBe(false);
    expect(isNextEntryShortcut(keyEvent({ ctrlKey: false, metaKey: false }))).toBe(false);
  });
});

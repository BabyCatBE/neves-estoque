import { describe, expect, it } from "vitest";
import { isTrashConfirmationValid } from "./trashConfirmation";

describe("trashConfirmation", () => {
  it("aceita a frase exata ignorando maiúsculas e espaços externos", () => {
    expect(isTrashConfirmationValid("  excluir  ", "EXCLUIR")).toBe(true);
  });

  it("rejeita frase incompleta", () => {
    expect(isTrashConfirmationValid("exclui", "EXCLUIR")).toBe(false);
  });

  it("diferencia ações destrutivas diferentes", () => {
    expect(isTrashConfirmationValid("EXCLUIR", "ESVAZIAR")).toBe(false);
  });
});

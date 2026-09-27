import { describe, expect, it } from "vitest";
import { z } from "zod";
import {
  getValidationErrorMessage,
  looksTechnicalErrorMessage
} from "./friendlyError";

describe("friendlyError", () => {
  it("extrai a primeira mensagem amigável de um ZodError", () => {
    const schema = z.string().min(1, "Informe o valor.");

    try {
      schema.parse("");
      throw new Error("O teste deveria falhar na validação.");
    } catch (error) {
      expect(getValidationErrorMessage(error)).toBe("Informe o valor.");
    }
  });

  it("extrai mensagem de erro Zod serializado como JSON", () => {
    const error = new Error(
      '[{"origin":"string","code":"too_small","minimum":1,"path":[],"message":"Informe a empresa."}]'
    );

    expect(getValidationErrorMessage(error)).toBe("Informe a empresa.");
  });

  it("reconhece mensagens técnicas que não devem aparecer para o usuário", () => {
    expect(looksTechnicalErrorMessage("PGRST116 schema cache error")).toBe(true);
    expect(looksTechnicalErrorMessage("Informe a empresa.")).toBe(false);
  });
});

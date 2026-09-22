import { describe, expect, it } from "vitest";
import { categoryNameSchema, getCategoryErrorMessage } from "./categoryValidation";

describe("categoryNameSchema", () => {
  it("remove espaços externos e aceita um nome válido", () => {
    expect(categoryNameSchema.parse("  CONFEITARIA  ")).toBe("CONFEITARIA");
  });

  it("rejeita nome vazio", () => {
    const result = categoryNameSchema.safeParse("   ");
    expect(result.success).toBe(false);
  });

  it("rejeita nomes acima de 120 caracteres", () => {
    const result = categoryNameSchema.safeParse("a".repeat(121));
    expect(result.success).toBe(false);
  });
});

describe("getCategoryErrorMessage", () => {
  it("traduz conflito de nome duplicado", () => {
    expect(getCategoryErrorMessage({ code: "23505" })).toBe(
      "Já existe uma categoria ativa com esse nome."
    );
  });
});

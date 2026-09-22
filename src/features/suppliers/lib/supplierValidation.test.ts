import { describe, expect, it } from "vitest";
import {
  parseOptionalInteger,
  supplierCompanySchema,
  supplierNameSchema,
  supplierPhoneSchema,
  weekdayLabel
} from "./supplierValidation";

describe("supplierValidation", () => {
  it("normaliza campos obrigatórios", () => {
    expect(supplierNameSchema.parse("  João  ")).toBe("João");
    expect(supplierCompanySchema.parse("  Empresa X  ")).toBe("Empresa X");
    expect(supplierPhoneSchema.parse("  (75) 99999-0000  ")).toBe("(75) 99999-0000");
  });

  it("aceita inteiro opcional vazio", () => {
    expect(parseOptionalInteger("", "Frequência", 1, 3650)).toBeNull();
  });

  it("valida limites inteiros", () => {
    expect(parseOptionalInteger("14", "Frequência", 1, 3650)).toBe(14);
    expect(() => parseOptionalInteger("0", "Frequência", 1, 3650)).toThrow();
    expect(() => parseOptionalInteger("1,5", "Frequência", 1, 3650)).toThrow();
  });

  it("formata dia preferencial", () => {
    expect(weekdayLabel(1)).toBe("Segunda-feira");
    expect(weekdayLabel(null)).toBe("Não informado");
  });
});

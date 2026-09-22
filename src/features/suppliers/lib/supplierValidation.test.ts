import { describe, expect, it } from "vitest";
import {
  formatSupplierPhoneInput,
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
    expect(supplierPhoneSchema.parse("  (75) 9 9999-0000  ")).toBe("75999990000");
  });

  it("formata telefone durante o preenchimento", () => {
    expect(formatSupplierPhoneInput("75")).toBe("(75");
    expect(formatSupplierPhoneInput("759")).toBe("(75) 9");
    expect(formatSupplierPhoneInput("7599999")).toBe("(75) 9 9999");
    expect(formatSupplierPhoneInput("75999990000")).toBe("(75) 9 9999-0000");
    expect(formatSupplierPhoneInput("759999900001234")).toBe("(75) 9 9999-0000");
  });

  it("exige exatamente 11 dígitos no telefone", () => {
    expect(() => supplierPhoneSchema.parse("7599999000")).toThrow("11 dígitos");
    expect(() => supplierPhoneSchema.parse("759999900000")).toThrow("11 dígitos");
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

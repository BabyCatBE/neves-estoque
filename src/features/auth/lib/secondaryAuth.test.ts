import { describe, expect, it } from "vitest";
import {
  isValidSecondaryUsername,
  normalizeSecondaryUsername,
  secondaryAuthEmail
} from "./secondaryAuth";

describe("secondaryAuth", () => {
  it("normaliza nome de usuário para minúsculas e sem acentos", () => {
    expect(normalizeSecondaryUsername("  TéstE  ")).toBe("teste");
  });

  it("aceita nomes simples de 3 a 32 caracteres", () => {
    expect(isValidSecondaryUsername("teste")).toBe(true);
    expect(isValidSecondaryUsername("marcio.01")).toBe(true);
    expect(isValidSecondaryUsername("user_name")).toBe(true);
  });

  it("rejeita espaços e formatos fora do padrão", () => {
    expect(isValidSecondaryUsername("ab")).toBe(false);
    expect(isValidSecondaryUsername("meu usuario")).toBe(false);
    expect(isValidSecondaryUsername("-teste")).toBe(false);
  });

  it("gera identificador interno sem expor e-mail real", () => {
    expect(secondaryAuthEmail("  TéstE  ")).toBe("teste@usuarios.neves.invalid");
  });
});

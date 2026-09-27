import { z } from "zod";
import {
  getValidationErrorMessage,
  looksTechnicalErrorMessage
} from "../../../shared/lib/friendlyError";

export const PRODUCT_UNITS = [
  "UN",
  "KG",
  "SC",
  "CX",
  "PCT",
  "FD",
  "BL",
  "GL",
  "PET",
  "ROLO",
  "LATA",
  "BARRA",
  "PT"
] as const;

export type ProductUnit = (typeof PRODUCT_UNITS)[number];

export const productNameSchema = z
  .string()
  .transform((value) => value.trim().replace(/\s+/g, " "))
  .pipe(
    z
      .string()
      .min(1, "Informe o nome do produto.")
      .max(200, "O nome do produto pode ter no máximo 200 caracteres.")
  );

export function parseOptionalNonNegativeDecimal(value: string, label: string) {
  const normalized = value.trim().replace(",", ".");
  if (!normalized) return null;

  if (!/^\d+(?:\.\d+)?$/.test(normalized)) {
    throw new Error(`${label} inválido.`);
  }

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`${label} não pode ser negativo.`);
  }

  return parsed;
}

export function getProductErrorMessage(error: unknown) {
  const validationMessage = getValidationErrorMessage(error);
  if (validationMessage) return validationMessage;

  if (typeof error === "object" && error !== null) {
    const candidate = error as { code?: string; message?: string };

    if (candidate.code === "23505") {
      return "Já existe um produto ativo com esse nome.";
    }

    if (typeof candidate.message === "string") {
      const knownMessages = [
        "Nome do produto inválido.",
        "Categoria é obrigatória.",
        "Categoria inválida ou excluída.",
        "Unidade inválida.",
        "Estoque inicial não pode ser negativo.",
        "Preço inicial não pode ser negativo.",
        "Estoque inicial inválido.",
        "Preço inicial inválido."
      ];

      const known = knownMessages.find((message) => candidate.message?.includes(message));
      if (known) return known;
    }
  }

  if (error instanceof Error && error.message && !looksTechnicalErrorMessage(error.message)) {
    return error.message;
  }
  return "Não foi possível salvar o produto. Tente novamente.";
}

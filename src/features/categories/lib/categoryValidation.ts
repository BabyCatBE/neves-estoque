import { z } from "zod";

export const categoryNameSchema = z
  .string()
  .trim()
  .min(1, "Informe o nome da categoria.")
  .max(120, "O nome da categoria pode ter no máximo 120 caracteres.");

export function getCategoryErrorMessage(error: unknown) {
  if (typeof error === "object" && error !== null) {
    const candidate = error as { code?: unknown; message?: unknown };

    if (candidate.code === "23505") {
      return "Já existe uma categoria ativa com esse nome.";
    }

    if (
      typeof candidate.message === "string" &&
      candidate.message.includes("Categoria possui produtos ativos")
    ) {
      return "A categoria só pode ser excluída quando estiver vazia.";
    }

    if (
      typeof candidate.message === "string" &&
      candidate.message.includes("Prazo de restauração expirado")
    ) {
      return "O prazo de 7 dias para restaurar esta categoria expirou.";
    }
  }

  return "Não foi possível salvar a categoria. Tente novamente.";
}

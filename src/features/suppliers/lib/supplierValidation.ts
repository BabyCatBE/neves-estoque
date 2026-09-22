import { z } from "zod";

export const supplierNameSchema = z.string().trim().min(1, "Informe o contato ou vendedor.").max(200, "O contato deve ter no máximo 200 caracteres.");
export const supplierCompanySchema = z.string().trim().min(1, "Informe a empresa.").max(200, "A empresa deve ter no máximo 200 caracteres.");
export const supplierPhoneSchema = z.string().trim().min(5, "Informe um telefone válido.").max(40, "O telefone deve ter no máximo 40 caracteres.");
export const supplierObservationSchema = z.string().max(2000, "A observação deve ter no máximo 2.000 caracteres.");

export const SUPPLIER_WEEKDAYS = [
  { value: 1, label: "Segunda-feira" },
  { value: 2, label: "Terça-feira" },
  { value: 3, label: "Quarta-feira" },
  { value: 4, label: "Quinta-feira" },
  { value: 5, label: "Sexta-feira" },
  { value: 6, label: "Sábado" },
  { value: 7, label: "Domingo" }
] as const;

export function parseOptionalInteger(raw: string, label: string, min: number, max: number): number | null {
  const value = raw.trim();
  if (!value) return null;
  if (!/^\d+$/.test(value)) throw new Error(`${label} deve ser um número inteiro.`);
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < min || parsed > max) {
    throw new Error(`${label} deve ficar entre ${min} e ${max}.`);
  }
  return parsed;
}

export function weekdayLabel(value: number | null) {
  if (value === null) return "Não informado";
  return SUPPLIER_WEEKDAYS.find((day) => day.value === value)?.label ?? "Não informado";
}

export function getSupplierErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    if (error.message.includes("Já existe um fornecedor ativo com este contato")) return "Já existe um fornecedor ativo com este contato.";
    if (error.message.includes("Fornecedor não encontrado")) return "Este fornecedor não está mais disponível.";
    if (error.message.includes("Prazo de restauração expirado")) return "O prazo de 7 dias para restaurar este fornecedor expirou.";
    return error.message;
  }

  if (typeof error === "object" && error !== null) {
    const message = (error as { message?: string }).message;
    if (typeof message === "string") {
      if (message.includes("suppliers_active_name_uq")) return "Já existe um fornecedor ativo com este contato.";
      if (message.includes("Acesso não autorizado")) return "Sua sessão não tem autorização para executar esta ação.";
      return message;
    }
  }

  return "Não foi possível concluir a operação com o fornecedor.";
}

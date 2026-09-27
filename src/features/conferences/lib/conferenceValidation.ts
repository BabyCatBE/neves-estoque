import { looksTechnicalErrorMessage } from "../../../shared/lib/friendlyError";
import {
  buildEffectiveAt,
  getFutureOperationalDateError,
  localDateInputValue
} from "../../entries/lib/entryValidation";

export { getFutureOperationalDateError, localDateInputValue };

export function parseConferenceQuantity(value: string, label = "Quantidade") {
  const normalized = value.trim().replace(",", ".");
  if (!normalized) throw new Error(`${label} é obrigatória.`);
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) throw new Error(`${label} inválida.`);

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`${label} não pode ser negativa.`);
  }
  return parsed;
}

export function buildConferenceEffectiveAt(dateValue: string, now = new Date()) {
  return buildEffectiveAt(dateValue, now, now);
}

export function buildEditedConferenceEffectiveAt(
  dateValue: string,
  originalIso: string,
  now = new Date()
) {
  const original = new Date(originalIso);
  return buildEffectiveAt(
    dateValue,
    Number.isNaN(original.getTime()) ? now : original,
    now
  );
}

export function dateInputFromIso(value: string) {
  return localDateInputValue(new Date(value));
}

export function localDayRange(dateValue: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateValue);
  if (!match) throw new Error("Informe uma data válida.");

  const start = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 0, 0, 0, 0);
  const end = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]) + 1, 0, 0, 0, 0);
  return { start: start.toISOString(), end: end.toISOString() };
}

export function localDateKey(value: string | Date) {
  const date = typeof value === "string" ? new Date(value) : value;
  return localDateInputValue(date);
}

export function getConferenceErrorMessage(error: unknown) {
  const message =
    error instanceof Error
      ? error.message
      : typeof error === "object" && error !== null
        ? (error as { message?: string }).message
        : undefined;

  if (!message) return "Não foi possível concluir a Conferência. Tente novamente.";

  const known = [
    "Categoria inválida ou excluída.",
    "Produto inválido ou excluído.",
    "Conferência não encontrada ou já excluída.",
    "Conferência não encontrada na lixeira.",
    "Dispositivo não autorizado.",
    "A categoria não possui produtos ativos para conferir.",
    "A Conferência da categoria precisa conter todos os produtos ativos.",
    "Conferência de categoria não encontrada.",
    "A correção precisa manter todos os produtos da Conferência original.",
    "Responsável físico inválido.",
    "A data da Conferência não pode ser futura."
  ].find((candidate) => message.includes(candidate));

  if (known) return known;
  if (message.includes("Quantidade não pode ser negativa")) {
    return "As quantidades da Conferência não podem ser negativas.";
  }
  return looksTechnicalErrorMessage(message)
    ? "Não foi possível concluir a Conferência. Tente novamente."
    : message;
}

export function formatConferenceDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(value));
}

export function formatConferenceTime(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" }).format(
    new Date(value)
  );
}

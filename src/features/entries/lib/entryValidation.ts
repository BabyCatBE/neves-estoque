export function parsePositiveDecimal(value: string, label: string) {
  const normalized = value.trim().replace(",", ".");
  if (!normalized) throw new Error(`${label} é obrigatória.`);
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) throw new Error(`${label} inválida.`);

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`${label} deve ser maior que zero.`);
  }
  return parsed;
}

export function parseOptionalPrice(value: string) {
  const normalized = value.trim().replace(",", ".");
  if (!normalized) return null;
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) throw new Error("Preço unitário inválido.");

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed < 0) throw new Error("Preço unitário não pode ser negativo.");
  return parsed;
}

export function localDateInputValue(date = new Date()) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function buildEffectiveAt(dateValue: string, now = new Date()) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateValue);
  if (!match) throw new Error("Informe uma data válida.");

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const effective = new Date(
    year,
    month - 1,
    day,
    now.getHours(),
    now.getMinutes(),
    now.getSeconds(),
    now.getMilliseconds()
  );

  if (
    effective.getFullYear() !== year ||
    effective.getMonth() !== month - 1 ||
    effective.getDate() !== day
  ) {
    throw new Error("Informe uma data válida.");
  }

  return effective.toISOString();
}

export function formatMoney(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
  }).format(value);
}

export function getEntryErrorMessage(error: unknown) {
  const message =
    error instanceof Error
      ? error.message
      : typeof error === "object" && error !== null
        ? (error as { message?: string }).message
        : undefined;

  if (!message) return "Não foi possível concluir a Entrada. Tente novamente.";

  const known = [
    "Fornecedor inválido ou excluído.",
    "Dispositivo não autorizado.",
    "A Entrada precisa de pelo menos um item.",
    "Chave de idempotência já utilizada.",
    "Data/hora efetiva da Entrada é obrigatória."
  ].find((candidate) => message.includes(candidate));

  if (known) return known;
  if (message.includes("Quantidade deve ser maior que zero")) return "Todas as quantidades devem ser maiores que zero.";
  if (message.includes("Preço não pode ser negativo")) return "Preço unitário não pode ser negativo.";
  return message;
}

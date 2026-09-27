export function parsePositiveConversionQuantity(value: string, label: string) {
  const normalized = value.trim().replace(",", ".");

  if (!/^\d+(?:\.\d+)?$/.test(normalized)) {
    throw new Error(`${label} inválida.`);
  }

  const parsed = Number(normalized);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`${label} deve ser maior que zero.`);
  }

  return parsed;
}

export function calculateUnitConversionFactor(oldQuantity: number, newQuantity: number) {
  if (!Number.isFinite(oldQuantity) || oldQuantity <= 0) {
    throw new Error("A quantidade da unidade atual deve ser maior que zero.");
  }
  if (!Number.isFinite(newQuantity) || newQuantity <= 0) {
    throw new Error("A quantidade da nova unidade deve ser maior que zero.");
  }
  return newQuantity / oldQuantity;
}

export function convertQuantityForUnit(value: number | null, factor: number) {
  return value === null ? null : value * factor;
}

export function convertPriceForUnit(value: number | null, factor: number) {
  return value === null ? null : value / factor;
}

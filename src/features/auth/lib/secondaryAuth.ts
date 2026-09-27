export function normalizeSecondaryUsername(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase();
}

export function isValidSecondaryUsername(value: string) {
  return /^[a-z0-9][a-z0-9._-]{2,31}$/.test(normalizeSecondaryUsername(value));
}

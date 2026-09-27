export function normalizeSearchText(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLocaleLowerCase("pt-BR")
    .trim()
    .replace(/\s+/g, " ");
}

export function matchesSearchText(value: string, search: string) {
  const term = normalizeSearchText(search);
  return !term || normalizeSearchText(value).includes(term);
}

export function matchesAnySearchText(values: string[], search: string) {
  const term = normalizeSearchText(search);
  return !term || values.some((value) => normalizeSearchText(value).includes(term));
}

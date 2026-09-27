export function isTrashConfirmationValid(value: string, phrase: string) {
  return value.trim().toLocaleUpperCase("pt-BR") === phrase.trim().toLocaleUpperCase("pt-BR");
}

type EntrySequenceShortcutEvent = Pick<
  KeyboardEvent,
  "key" | "ctrlKey" | "metaKey" | "shiftKey" | "altKey" | "repeat"
>;

export function isNextEntryShortcut(event: EntrySequenceShortcutEvent): boolean {
  return (
    event.key === "Enter" &&
    event.shiftKey &&
    (event.ctrlKey || event.metaKey) &&
    !event.altKey &&
    !event.repeat
  );
}

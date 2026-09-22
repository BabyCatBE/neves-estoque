export function moveItemById<T extends { id: string }>(
  items: T[],
  activeId: string,
  overId: string
) {
  const fromIndex = items.findIndex((item) => item.id === activeId);
  const toIndex = items.findIndex((item) => item.id === overId);

  if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return items;

  const next = [...items];
  const moved = next.splice(fromIndex, 1)[0];

  if (!moved) return items;

  next.splice(toIndex, 0, moved);
  return next;
}

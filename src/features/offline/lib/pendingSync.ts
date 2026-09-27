import type { EntryConferenceConflict } from "../../entries/api/entries";
import {
  localDateKey,
  localDayRange
} from "../../conferences/lib/conferenceValidation";

export type EntryConflictPosition = {
  id: string;
  label: string;
  description: string;
  effectiveAt: string;
};

function midpointIso(leftMs: number, rightMs: number) {
  const gap = rightMs - leftMs;
  if (gap <= 1) return null;
  return new Date(leftMs + Math.floor(gap / 2)).toISOString();
}

export function buildEntryConflictPositions(
  conflicts: EntryConferenceConflict[],
  originalEffectiveAt: string
): EntryConflictPosition[] {
  if (!conflicts.length) return [];

  const sorted = [...conflicts].sort(
    (a, b) => new Date(a.effectiveAt).getTime() - new Date(b.effectiveAt).getTime()
  );
  const { start, end } = localDayRange(localDateKey(originalEffectiveAt));
  const dayStart = new Date(start).getTime();
  const dayEnd = new Date(end).getTime();
  const positions: EntryConflictPosition[] = [];

  const first = sorted[0];
  const last = sorted.at(-1);
  if (!first || !last) return [];

  const firstTime = new Date(first.effectiveAt).getTime();
  const before = midpointIso(dayStart, firstTime);
  if (before) {
    positions.push({
      id: "before-first",
      label: sorted.length === 1 ? "Antes da Conferência" : "Antes de todas",
      description: `Antes da Conferência das ${formatTime(first.effectiveAt)}`,
      effectiveAt: before
    });
  }

  for (let index = 0; index < sorted.length - 1; index += 1) {
    const left = sorted[index];
    const right = sorted[index + 1];
    if (!left || !right) continue;

    const value = midpointIso(
      new Date(left.effectiveAt).getTime(),
      new Date(right.effectiveAt).getTime()
    );
    if (!value) continue;

    positions.push({
      id: `between-${index}`,
      label: `Entre ${formatTime(left.effectiveAt)} e ${formatTime(right.effectiveAt)}`,
      description: "A Entrada aconteceu depois da primeira e antes da segunda Conferência.",
      effectiveAt: value
    });
  }

  const lastTime = new Date(last.effectiveAt).getTime();
  const after = midpointIso(lastTime, dayEnd);
  if (after) {
    positions.push({
      id: "after-last",
      label: sorted.length === 1 ? "Depois da Conferência" : "Depois de todas",
      description: `Depois da Conferência das ${formatTime(last.effectiveAt)}`,
      effectiveAt: after
    });
  }

  return positions;
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

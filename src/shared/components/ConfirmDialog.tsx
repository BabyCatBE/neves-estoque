import { useEffect, useId, type ReactNode } from "react";
import { handleDialogButtonArrowNavigation } from "../lib/dialogKeyboard";
import { Button } from "./ui/Button";
import { Card } from "./ui/Card";

export type ConfirmDialogVariant = "default" | "warning" | "danger";

type Props = {
  open: boolean;
  title: string;
  description: ReactNode;
  confirmLabel?: string;
  pendingLabel?: string;
  cancelLabel?: string;
  variant?: ConfirmDialogVariant;
  isPending?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

const toneClasses: Record<
  ConfirmDialogVariant,
  { badge: string; icon: string; label: string; symbol: string }
> = {
  default: {
    badge: "bg-red-50 text-red-700",
    icon: "border-red-100 bg-red-50 text-red-700",
    label: "Confirmação",
    symbol: "?"
  },
  warning: {
    badge: "bg-amber-50 text-amber-800",
    icon: "border-amber-200 bg-amber-50 text-amber-800",
    label: "Atenção",
    symbol: "!"
  },
  danger: {
    badge: "bg-red-50 text-red-700",
    icon: "border-red-200 bg-red-50 text-red-700",
    label: "Ação importante",
    symbol: "!"
  }
};

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "Confirmar",
  pendingLabel = "Confirmando…",
  cancelLabel = "Cancelar",
  variant = "default",
  isPending = false,
  onConfirm,
  onCancel
}: Props) {
  const titleId = useId();
  const descriptionId = useId();
  const tone = toneClasses[variant];

  useEffect(() => {
    if (!open) return;

    const previousActiveElement =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;

    document.body.style.overflow = "hidden";

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !isPending) {
        onCancel();
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousActiveElement?.focus();
    };
  }, [isPending, onCancel, open]);

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
      onKeyDown={handleDialogButtonArrowNavigation}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isPending) {
          onCancel();
        }
      }}
    >
      <Card className="w-full max-w-lg p-5 shadow-xl">
        <div className="flex items-start gap-3">
          <div
            className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border text-lg font-bold ${tone.icon}`}
            aria-hidden="true"
          >
            {tone.symbol}
          </div>

          <div className="min-w-0 flex-1">
            <span
              className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] ${tone.badge}`}
            >
              {tone.label}
            </span>
            <h3 id={titleId} className="mt-2 text-xl font-semibold text-zinc-950">
              {title}
            </h3>
            <div id={descriptionId} className="mt-2 text-sm leading-6 text-zinc-600">
              {description}
            </div>
          </div>
        </div>

        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button
            autoFocus
            variant="ghost"
            disabled={isPending}
            onClick={onCancel}
          >
            {cancelLabel}
          </Button>
          <Button
            variant={variant === "danger" ? "danger" : "primary"}
            disabled={isPending}
            onClick={onConfirm}
          >
            {isPending ? pendingLabel : confirmLabel}
          </Button>
        </div>
      </Card>
    </div>
  );
}

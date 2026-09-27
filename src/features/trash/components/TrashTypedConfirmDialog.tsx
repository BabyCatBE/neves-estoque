import { useState } from "react";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { handleDialogButtonArrowNavigation } from "../../../shared/lib/dialogKeyboard";
import { isTrashConfirmationValid } from "../lib/trashConfirmation";

export function TrashTypedConfirmDialog({
  title,
  description,
  phrase,
  confirmLabel,
  pendingLabel,
  isPending,
  onCancel,
  onConfirm
}: {
  title: string;
  description: string;
  phrase: string;
  confirmLabel: string;
  pendingLabel: string;
  isPending: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const [value, setValue] = useState("");
  const valid = isTrashConfirmationValid(value, phrase);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="trash-destructive-dialog-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
      onKeyDown={handleDialogButtonArrowNavigation}
    >
      <Card className="w-full max-w-lg p-5 shadow-xl">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
          Ação irreversível
        </p>
        <h3 id="trash-destructive-dialog-title" className="mt-1 text-xl font-semibold text-zinc-950">
          {title}
        </h3>
        <p className="mt-2 text-sm leading-6 text-zinc-600">{description}</p>

        <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm leading-6 text-red-900">
          Esta ação não possui restauração pela interface. Digite <strong>{phrase}</strong> para
          liberar a confirmação.
        </div>

        <div className="mt-4">
          <TextField
            id="trash-destructive-confirmation"
            label={`Digite ${phrase} para confirmar`}
            value={value}
            autoFocus
            autoComplete="off"
            onChange={(event) => setValue(event.target.value)}
            onKeyDown={(event) => {
              if (event.key !== "Enter" || !valid || event.ctrlKey || event.metaKey) return;
              event.preventDefault();
              document.getElementById("trash-destructive-confirm")?.focus();
            }}
          />
        </div>

        <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="ghost" disabled={isPending} onClick={onCancel}>
            Cancelar
          </Button>
          <Button
            id="trash-destructive-confirm"
            variant="danger"
            disabled={!valid || isPending}
            isLoading={isPending}
            loadingLabel={pendingLabel}
            onClick={onConfirm}
          >
            {confirmLabel}
          </Button>
        </div>
      </Card>
    </div>
  );
}

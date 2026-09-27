import { useState } from "react";
import nevesPrintLogo from "../../../assets/neves-logo-print.webp";
import { CategoryIllustrationVisual } from "./CategoryIllustrationVisual";
import {
  CATEGORY_ILLUSTRATION_OPTIONS,
  CategoryLibraryIllustration
} from "./CategoryIllustrationLibrary";

export type CategoryIllustrationDraft = {
  source: "library" | "upload" | null;
  key: string | null;
  file: File | null;
  previewUrl: string | null;
  positionX: number;
  positionY: number;
};

export function emptyCategoryIllustrationDraft(): CategoryIllustrationDraft {
  return {
    source: null,
    key: null,
    file: null,
    previewUrl: null,
    positionX: 50,
    positionY: 50
  };
}

type Props = {
  value: CategoryIllustrationDraft;
  onChange: (value: CategoryIllustrationDraft) => void;
  categoryName: string;
};

const allowedMimeTypes = new Set(["image/jpeg", "image/png", "image/webp"]);
const maxFileSize = 5 * 1024 * 1024;

export function CategoryIllustrationPicker({ value, onChange, categoryName }: Props) {
  const [fileError, setFileError] = useState<string | null>(null);

  const selectLibrary = (key: string) => {
    setFileError(null);
    onChange({
      source: "library",
      key,
      file: null,
      previewUrl: null,
      positionX: 50,
      positionY: 50
    });
  };

  const selectUpload = (file: File | null) => {
    setFileError(null);
    if (!file) return;

    if (!allowedMimeTypes.has(file.type)) {
      setFileError("Use uma imagem JPG, PNG ou WEBP.");
      return;
    }

    if (file.size > maxFileSize) {
      setFileError("A imagem pode ter no máximo 5 MB.");
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result !== "string") {
        setFileError("Não foi possível preparar a prévia da imagem.");
        return;
      }

      onChange({
        source: "upload",
        key: null,
        file,
        previewUrl: reader.result,
        positionX: 50,
        positionY: 50
      });
    };
    reader.onerror = () => setFileError("Não foi possível ler a imagem selecionada.");
    reader.readAsDataURL(file);
  };

  const displayName = categoryName.trim() || "NOME DA CATEGORIA";

  return (
    <div className="mt-5 border-t border-zinc-100 pt-5">
      <div>
        <h3 className="text-sm font-semibold text-zinc-900">Ilustração</h3>
        <p className="mt-1 text-xs leading-5 text-zinc-500">
          Opcional. Escolha uma ilustração da biblioteca ou envie uma imagem própria.
        </p>
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4 lg:grid-cols-6">
        {CATEGORY_ILLUSTRATION_OPTIONS.map((option) => {
          const selected = value.source === "library" && value.key === option.key;
          return (
            <button
              key={option.key}
              type="button"
              onClick={() => selectLibrary(option.key)}
              className={`rounded-xl border p-2 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2 ${
                selected
                  ? "border-red-500 bg-red-50 text-red-800"
                  : "border-zinc-200 bg-white text-zinc-700 hover:bg-zinc-50"
              }`}
            >
              <div className="flex h-12 items-center justify-center rounded-lg bg-red-50 text-red-700">
                <CategoryLibraryIllustration illustrationKey={option.key} className="h-8 w-8" />
              </div>
              <span className="mt-2 block text-[11px] font-medium leading-4">{option.label}</span>
            </button>
          );
        })}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <label className="inline-flex min-h-10 cursor-pointer items-center justify-center rounded-xl border border-red-200 bg-white px-3 py-2 text-xs font-semibold text-red-700 transition hover:bg-red-50">
          Enviar imagem
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="sr-only"
            onChange={(event) => {
              selectUpload(event.target.files?.[0] ?? null);
              event.currentTarget.value = "";
            }}
          />
        </label>
        <button
          type="button"
          className="min-h-10 rounded-xl px-3 py-2 text-xs font-semibold text-zinc-600 transition hover:bg-zinc-100"
          onClick={() => {
            setFileError(null);
            onChange(emptyCategoryIllustrationDraft());
          }}
        >
          Sem ilustração
        </button>
        {value.source ? (
          <span className="text-xs text-zinc-500">
            {value.source === "library" ? "Biblioteca selecionada" : "Imagem própria selecionada"}
          </span>
        ) : null}
      </div>

      {fileError ? (
        <p className="mt-2 text-xs font-medium text-red-700">{fileError}</p>
      ) : null}

      {value.source === "upload" && value.previewUrl ? (
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <label className="text-xs font-medium text-zinc-700">
            Ajuste horizontal
            <input
              type="range"
              min="0"
              max="100"
              value={value.positionX}
              onChange={(event) =>
                onChange({ ...value, positionX: Number(event.target.value) })
              }
              className="mt-2 w-full accent-red-700"
            />
          </label>
          <label className="text-xs font-medium text-zinc-700">
            Ajuste vertical
            <input
              type="range"
              min="0"
              max="100"
              value={value.positionY}
              onChange={(event) =>
                onChange({ ...value, positionY: Number(event.target.value) })
              }
              className="mt-2 w-full accent-red-700"
            />
          </label>
        </div>
      ) : null}

      <div className="mt-5 grid gap-4 lg:grid-cols-2">
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-500">
            Prévia no cartão
          </p>
          <div className="flex items-center gap-3 rounded-2xl border border-zinc-200 bg-white p-4 shadow-sm">
            <CategoryIllustrationVisual
              source={value.source}
              illustrationKey={value.key}
              illustrationUrl={value.previewUrl}
              positionX={value.positionX}
              positionY={value.positionY}
              className="h-14 w-14"
            />
            <div>
              <p className="font-semibold text-zinc-900">{displayName}</p>
              <p className="mt-1 text-xs text-zinc-500">0 produtos</p>
            </div>
          </div>
        </div>

        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-500">
            Prévia no A4
          </p>
          <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
            <div className="flex items-center gap-3 border-b border-red-100 p-3">
              <CategoryIllustrationVisual
                source={value.source}
                illustrationKey={value.key}
                illustrationUrl={value.previewUrl}
                positionX={value.positionX}
                positionY={value.positionY}
                monochrome
                className="h-12 w-12"
              />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold text-red-800">{displayName}</p>
                <p className="text-[10px] uppercase tracking-wide text-zinc-500">
                  Conferência de Estoque
                </p>
              </div>
              <img src={nevesPrintLogo} alt="" aria-hidden="true" className="h-7 w-14 object-contain" />
            </div>
            <div className="space-y-2 p-3">
              <div className="h-3 rounded bg-red-700" />
              <div className="h-3 rounded bg-red-50" />
              <div className="h-3 rounded bg-zinc-100" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

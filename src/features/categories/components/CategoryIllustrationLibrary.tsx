import type { ReactNode } from "react";

export const CATEGORY_ILLUSTRATION_OPTIONS = [
  { key: "panificacao", label: "Panificação" },
  { key: "boleria", label: "Boleria" },
  { key: "confeitaria", label: "Confeitaria" },
  { key: "frios", label: "Frios e embutidos" },
  { key: "manteigas", label: "Manteigas e requeijões" },
  { key: "embalagens", label: "Embalagens" },
  { key: "etiquetas", label: "Bobinas e etiquetas" },
  { key: "descartaveis", label: "Descartáveis" },
  { key: "higiene", label: "Higiene e proteção" },
  { key: "flexiveis", label: "Embalagens flexíveis" },
  { key: "conveniencia", label: "Conveniência" },
  { key: "limpeza", label: "Limpeza e descarte" }
] as const;

export type CategoryIllustrationLibraryKey =
  (typeof CATEGORY_ILLUSTRATION_OPTIONS)[number]["key"];

type Props = {
  illustrationKey: string;
  className?: string;
};

export function CategoryLibraryIllustration({ illustrationKey, className = "h-8 w-8" }: Props) {
  const common = {
    className,
    viewBox: "0 0 48 48",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2.2,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true
  };

  const drawings: Record<string, ReactNode> = {
    panificacao: (
      <>
        <path d="M10 31c0-8 6-15 14-15s14 7 14 15c0 3-2 5-5 5H15c-3 0-5-2-5-5Z" />
        <path d="M17 20c2 2 3 4 3 7M24 17c2 3 3 6 2 10M31 20c-1 2-1 4-1 7" />
      </>
    ),
    boleria: (
      <>
        <path d="M12 22h24v14H12z" />
        <path d="M15 22c0-5 4-9 9-9s9 4 9 9" />
        <path d="M18 28h12M20 13v-3M28 13v-3" />
      </>
    ),
    confeitaria: (
      <>
        <path d="M14 35h20l-2-13H16l-2 13Z" />
        <path d="M18 22c0-5 3-9 6-9s6 4 6 9" />
        <path d="M20 29h8M24 13V9" />
      </>
    ),
    frios: (
      <>
        <path d="M11 17c8-5 18-5 26 0v18H11V17Z" />
        <circle cx="18" cy="25" r="2" />
        <circle cx="29" cy="29" r="2" />
        <path d="M11 20h26" />
      </>
    ),
    manteigas: (
      <>
        <path d="M11 22h26v13H11z" />
        <path d="M15 18h18l4 4H11l4-4Z" />
        <path d="M18 27h12" />
      </>
    ),
    embalagens: (
      <>
        <path d="M13 16h22v22H13z" />
        <path d="m13 16 11 7 11-7M24 23v15" />
        <path d="M18 13h12" />
      </>
    ),
    etiquetas: (
      <>
        <path d="M11 14h18l8 10-15 15-11-11V14Z" />
        <circle cx="18" cy="21" r="2.5" />
        <path d="M27 17h7" />
      </>
    ),
    descartaveis: (
      <>
        <path d="M15 13h18l-2 25H17l-2-25Z" />
        <path d="M13 18h22M20 9h8M24 9v4" />
      </>
    ),
    higiene: (
      <>
        <path d="M15 17h18v21H15z" />
        <path d="M19 17v-5h10v5M20 25h8M24 21v8" />
        <path d="M12 32h3M33 32h3" />
      </>
    ),
    flexiveis: (
      <>
        <path d="M14 11h20l2 27H12l2-27Z" />
        <path d="M15 18h18M18 25c4-3 8-3 12 0" />
      </>
    ),
    conveniencia: (
      <>
        <path d="M13 17h22v21H13z" />
        <path d="M17 17v-6h14v6M18 25h12M18 31h8" />
      </>
    ),
    limpeza: (
      <>
        <path d="M20 11h8v7h-8zM17 18h14l4 20H13l4-20Z" />
        <path d="M24 11V8M19 27h10M21 32h6" />
      </>
    )
  };

  return <svg {...common}>{drawings[illustrationKey] ?? drawings.embalagens}</svg>;
}

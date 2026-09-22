import type { PropsWithChildren } from "react";
import { Link } from "react-router-dom";

type Props = PropsWithChildren<{ title?: string; showBack?: boolean }>;

export function AppShell({ children, title = "Neves Estoque", showBack = false }: Props) {
  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-900">
      <header className="border-b border-zinc-200 bg-white">
        <div className="mx-auto flex min-h-16 max-w-6xl items-center gap-3 px-4 sm:px-6">
          {showBack ? (
            <Link
              to="/"
              className="rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100"
            >
              Voltar
            </Link>
          ) : null}
          <img
            src="/brand/neves-logo.webp"
            alt=""
            aria-hidden="true"
            className="h-10 w-10 rounded-xl object-cover shadow-sm"
          />
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-red-700">
              Panificadora Neves
            </p>
            <h1 className="text-lg font-semibold">{title}</h1>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">{children}</main>
    </div>
  );
}

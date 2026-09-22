import type { PropsWithChildren } from "react";
import { Link } from "react-router-dom";
import nevesLogo from "../../assets/neves-logo.webp";

type Props = PropsWithChildren<{
  title?: string;
  showBack?: boolean;
  backTo?: string;
}>;

export function AppShell({
  children,
  title = "Neves Estoque",
  showBack = false,
  backTo = "/"
}: Props) {
  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-900">
      <header className="border-b border-zinc-200 bg-white">
        <div className="mx-auto flex min-h-16 max-w-6xl items-center gap-3 px-4 sm:px-6">
          {showBack ? (
            <Link
              to={backTo}
              className="rounded-lg px-3 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100"
            >
              Voltar
            </Link>
          ) : null}
          <img
            src={nevesLogo}
            alt=""
            aria-hidden="true"
            className="h-10 w-20 rounded-lg object-contain shadow-sm"
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

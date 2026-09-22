import type { PropsWithChildren } from "react";
import { Link } from "react-router-dom";
import nevesLogo from "../../assets/neves-logo.webp";

type Props = PropsWithChildren<{
  title?: string;
  showBack?: boolean;
  backTo?: string;
  onBack?: () => void;
}>;

export function AppShell({
  children,
  title = "Neves Estoque",
  showBack = false,
  backTo = "/",
  onBack
}: Props) {
  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-900">
      <header className="sticky top-0 z-30 bg-zinc-950 text-white shadow-md">
        <div className="mx-auto flex min-h-20 max-w-6xl items-center gap-3 px-4 py-2 sm:px-6">
          {showBack ? (
            onBack ? (
              <button
                type="button"
                onClick={onBack}
                className="inline-flex min-h-10 items-center gap-1 rounded-xl px-3 py-2 text-sm font-semibold text-zinc-200 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-950"
              >
                <span aria-hidden="true">‹</span>
                Voltar
              </button>
            ) : (
              <Link
                to={backTo}
                className="inline-flex min-h-10 items-center gap-1 rounded-xl px-3 py-2 text-sm font-semibold text-zinc-200 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-950"
              >
                <span aria-hidden="true">‹</span>
                Voltar
              </Link>
            )
          ) : null}

          <img
            src={nevesLogo}
            alt=""
            aria-hidden="true"
            className="h-12 w-24 rounded-xl bg-white/5 object-contain shadow-sm ring-1 ring-white/10"
          />

          <div className="min-w-0">
            <p className="truncate text-xs font-extrabold uppercase tracking-[0.16em] text-red-500 sm:text-sm">
              Neves <span className="text-amber-500">•</span> Estoque
            </p>
            <h1 className="truncate text-lg font-semibold text-white sm:text-xl">{title}</h1>
          </div>
        </div>

        <div className="relative h-1 overflow-hidden bg-red-700">
          <div className="absolute inset-y-0 left-0 w-24 bg-[repeating-linear-gradient(135deg,#b91c1c_0px,#b91c1c_10px,#f59e0b_10px,#f59e0b_16px,#ffffff_16px,#ffffff_20px)] sm:w-36" />
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">{children}</main>
    </div>
  );
}

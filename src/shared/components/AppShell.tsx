import type { PropsWithChildren } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import nevesLogo from "../../assets/neves-logo.webp";

type Props = PropsWithChildren<{
  title?: string;
  showBack?: boolean;
  backTo?: string;
  onBack?: () => void;
}>;

type Breadcrumb = {
  label: string;
  to?: string;
};

export function AppShell({
  children,
  title = "Neves Estoque",
  showBack = false,
  backTo = "/",
  onBack
}: Props) {
  const location = useLocation();
  const navigate = useNavigate();
  const isHome = location.pathname === "/";
  const breadcrumbs = buildBreadcrumbs(location.pathname);

  const handleBack = () => {
    if (onBack) {
      onBack();
      return;
    }

    const state = window.history.state as { idx?: number } | null;
    if (typeof state?.idx === "number" && state.idx > 0) {
      navigate(-1);
      return;
    }

    navigate(backTo);
  };

  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-900">
      <header className="sticky top-0 z-30 bg-zinc-950 text-white shadow-md">
        <div className="mx-auto flex min-h-20 max-w-6xl items-center gap-2 px-4 py-2 sm:gap-3 sm:px-6">
          {showBack ? (
            <button
              type="button"
              onClick={handleBack}
              className="inline-flex min-h-10 shrink-0 items-center gap-1 rounded-xl px-2 py-2 text-sm font-semibold text-zinc-200 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-950 sm:px-3"
            >
              <BackIcon />
              <span>Voltar</span>
            </button>
          ) : null}

          <img
            src={nevesLogo}
            alt=""
            aria-hidden="true"
            className="h-12 w-20 shrink-0 rounded-xl bg-white/5 object-contain shadow-sm ring-1 ring-white/10 sm:w-24"
          />

          <div className="min-w-0">
            <p className="truncate text-xs font-extrabold uppercase tracking-[0.16em] text-red-500 sm:text-sm">
              Neves <span className="text-amber-500">•</span> Estoque
            </p>
            <h1 className="truncate text-lg font-semibold text-white sm:text-xl">{title}</h1>
          </div>

          {!isHome ? (
            <Link
              to="/"
              className="ml-auto inline-flex min-h-10 shrink-0 items-center gap-1.5 rounded-xl px-2 py-2 text-sm font-semibold text-zinc-200 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-950 sm:px-3"
            >
              <HomeIcon />
              <span>Home</span>
            </Link>
          ) : null}
        </div>

        {breadcrumbs.length > 1 ? (
          <nav
            aria-label="Caminho da página"
            className="mx-auto flex max-w-6xl items-center gap-1 overflow-x-auto px-4 pb-2 text-xs text-zinc-400 sm:px-6"
          >
            {breadcrumbs.map((item, index) => {
              const current = index === breadcrumbs.length - 1;
              return (
                <span key={`${item.label}-${index}`} className="inline-flex shrink-0 items-center gap-1">
                  {index > 0 ? <span aria-hidden="true" className="text-zinc-600">›</span> : null}
                  {item.to && !current ? (
                    <Link
                      to={item.to}
                      className="rounded px-1 py-0.5 transition hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400"
                    >
                      {item.label}
                    </Link>
                  ) : (
                    <span className={current ? "px-1 py-0.5 font-semibold text-zinc-200" : "px-1 py-0.5"}>
                      {item.label}
                    </span>
                  )}
                </span>
              );
            })}
          </nav>
        ) : null}

        <div className="relative h-1 overflow-hidden bg-red-700">
          <div className="absolute inset-y-0 left-0 w-24 bg-[repeating-linear-gradient(135deg,#b91c1c_0px,#b91c1c_10px,#f59e0b_10px,#f59e0b_16px,#ffffff_16px,#ffffff_20px)] sm:w-36" />
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6">{children}</main>
    </div>
  );
}

function buildBreadcrumbs(pathname: string): Breadcrumb[] {
  if (pathname === "/produtos/categorias/lixeira") {
    return [
      { label: "Produtos", to: "/produtos" },
      { label: "Categorias", to: "/produtos/categorias" },
      { label: "Lixeira" }
    ];
  }

  if (pathname === "/produtos/categorias") {
    return [
      { label: "Produtos", to: "/produtos" },
      { label: "Categorias" }
    ];
  }

  if (pathname === "/produtos/lista") {
    return [
      { label: "Produtos", to: "/produtos" },
      { label: "Lista" }
    ];
  }

  if (/^\/produtos\/[^/]+$/.test(pathname)) {
    return [
      { label: "Produtos", to: "/produtos" },
      { label: "Lista", to: "/produtos/lista" },
      { label: "Produto" }
    ];
  }

  if (pathname === "/produtos") return [{ label: "Produtos" }];

  if (pathname === "/entradas/nova") {
    return [
      { label: "Entradas", to: "/entradas" },
      { label: "Nova Entrada" }
    ];
  }

  if (pathname === "/entradas/historico") {
    return [
      { label: "Entradas", to: "/entradas" },
      { label: "Histórico" }
    ];
  }

  if (/^\/entradas\/[^/]+\/editar$/.test(pathname)) {
    const entryId = pathname.split("/")[2];
    return [
      { label: "Entradas", to: "/entradas" },
      { label: "Histórico", to: "/entradas/historico" },
      { label: "Entrada", to: `/entradas/${entryId}` },
      { label: "Editar" }
    ];
  }

  if (/^\/entradas\/[^/]+$/.test(pathname)) {
    return [
      { label: "Entradas", to: "/entradas" },
      { label: "Histórico", to: "/entradas/historico" },
      { label: "Entrada" }
    ];
  }

  if (pathname === "/entradas") return [{ label: "Entradas" }];

  if (pathname === "/alertas/lixeira") {
    return [
      { label: "Alertas", to: "/alertas" },
      { label: "Lixeira" }
    ];
  }

  if (pathname === "/alertas") return [{ label: "Alertas" }];

  if (/^\/fornecedores\/[^/]+$/.test(pathname)) {
    return [
      { label: "Fornecedores", to: "/fornecedores" },
      { label: "Fornecedor" }
    ];
  }

  if (pathname === "/fornecedores") return [{ label: "Fornecedores" }];
  if (pathname === "/estoque") return [{ label: "Estoque Atual" }];

  if (pathname === "/conferencias/imprimir") {
    return [
      { label: "Conferência", to: "/conferencias" },
      { label: "Imprimir papéis" }
    ];
  }

  if (/^\/conferencias\/fazer\/[^/]+$/.test(pathname)) {
    return [
      { label: "Conferência", to: "/conferencias" },
      { label: "Fazer conferência", to: "/conferencias/fazer" },
      { label: "Categoria" }
    ];
  }

  if (pathname === "/conferencias/fazer") {
    return [
      { label: "Conferência", to: "/conferencias" },
      { label: "Fazer conferência" }
    ];
  }

  if (/^\/conferencias\/historico\/[^/]+$/.test(pathname)) {
    return [
      { label: "Conferência", to: "/conferencias" },
      { label: "Histórico", to: "/conferencias/historico" },
      { label: "Categoria" }
    ];
  }

  if (pathname === "/conferencias/historico") {
    return [
      { label: "Conferência", to: "/conferencias" },
      { label: "Histórico" }
    ];
  }

  if (/^\/conferencias\/[^/]+\/editar$/.test(pathname)) {
    const conferenceId = pathname.split("/")[2];
    return [
      { label: "Conferência", to: "/conferencias" },
      { label: "Histórico", to: "/conferencias/historico" },
      { label: "Registro", to: `/conferencias/${conferenceId}` },
      { label: "Corrigir" }
    ];
  }

  if (/^\/conferencias\/[^/]+$/.test(pathname)) {
    return [
      { label: "Conferência", to: "/conferencias" },
      { label: "Histórico", to: "/conferencias/historico" },
      { label: "Registro" }
    ];
  }

  if (pathname === "/conferencias") return [{ label: "Conferência" }];
  if (pathname === "/compras") return [{ label: "Compras" }];

  return [];
}

function BackIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-4 w-4" fill="none">
      <path d="M15 5 8 12l7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function HomeIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-4 w-4" fill="none">
      <path d="m4 11 8-7 8 7v9H5v-9Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <path d="M9 20v-6h6v6" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </svg>
  );
}

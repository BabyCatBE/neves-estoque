import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useAuth } from "../../auth/context/AuthContext";
import { AppShell } from "../../../shared/components/AppShell";
import { APP_VERSION, SHOW_DEVELOPMENT_VERSION } from "../../../shared/config/appVersion";
import { listRestorableTrashItems } from "../../trash/api/trash";

const modules = [
  ["Estoque Atual", "/estoque", "Posição derivada da última conferência válida e entradas posteriores."],
  ["Conferência", "/conferencias", "Registrar contagens físicas por categoria ou produto."],
  ["Entrada", "/entradas", "Registrar mercadorias efetivamente recebidas."],
  ["Produtos", "/produtos", "Catálogo operacional de produtos e categorias."],
  ["Fornecedores", "/fornecedores", "Cadastro e histórico de fornecedores."],
  ["Compras", "/compras", "Simulação de necessidade de compra."]
] as const;

export function HomePage() {
  const { session, roleName, deviceId, signOut } = useAuth();
  const alertsQuery = useQuery({
    queryKey: ["trash", "restorable"],
    queryFn: listRestorableTrashItems,
    staleTime: 30_000
  });
  const hasAlerts = (alertsQuery.data?.length ?? 0) > 0;

  return (
    <AppShell>
      <section>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-red-50 px-3 py-1 text-xs font-semibold text-red-700">
                <span>DEV</span>
                {SHOW_DEVELOPMENT_VERSION ? (
                  <>
                    <span aria-hidden="true" className="text-amber-600">
                      •
                    </span>
                    <span>v{APP_VERSION}</span>
                  </>
                ) : null}
              </span>
              <Link
                to="/alertas"
                aria-label={hasAlerts ? "Abrir Alertas" : "Abrir Alertas — nenhum alerta ativo"}
                title={hasAlerts ? "Alertas" : "Nenhum alerta ativo"}
                className={
                  hasAlerts
                    ? "inline-flex min-h-8 items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-900 transition hover:border-amber-300 hover:bg-amber-100"
                    : "inline-flex h-8 w-8 items-center justify-center rounded-full border border-zinc-200 bg-zinc-100 text-zinc-500 transition hover:border-zinc-300 hover:bg-zinc-200 hover:text-zinc-700"
                }
              >
                <AlertIcon />
                {hasAlerts ? <span>Alertas</span> : null}
              </Link>
            </div>
            <h2 className="mt-3 text-2xl font-semibold tracking-tight">Controle de estoque</h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-zinc-600">
              Implementação incremental em ambiente de desenvolvimento. Categorias, Produtos, Fornecedores e
              Entradas já possuem fluxos operacionais; navegação e alertas estão no bloco atual.
            </p>
          </div>

          <div className="rounded-2xl border border-zinc-200 bg-white p-4 text-sm shadow-sm sm:min-w-64">
            <p className="font-medium text-zinc-900">{session?.user.email ?? "Conta Google"}</p>
            <p className="mt-1 text-xs text-zinc-500">
              {roleName === "admin" ? "Administrador" : roleName ?? "Usuário autorizado"}
              {deviceId ? " · dispositivo registrado" : ""}
            </p>
            <button
              type="button"
              onClick={() => void signOut()}
              className="mt-3 rounded-lg border border-zinc-200 px-3 py-2 text-xs font-semibold text-zinc-700 transition hover:bg-zinc-50"
            >
              Sair
            </button>
          </div>
        </div>

        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {modules.map(([title, href, description]) => (
            <Link
              key={href}
              to={href}
              className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-red-200 hover:shadow-md"
            >
              <h3 className="font-semibold">{title}</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">{description}</p>
            </Link>
          ))}
        </div>
      </section>
    </AppShell>
  );
}


function AlertIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-4 w-4" fill="none">
      <path d="M12 4 3 20h18L12 4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      <path d="M12 9v5M12 17.2v.1" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

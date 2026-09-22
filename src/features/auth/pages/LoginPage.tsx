import { Navigate } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { hasSupabaseConfig } from "../../../shared/lib/env";
import { useAuth } from "../context/AuthContext";

export function LoginPage() {
  const { status, errorMessage, signInWithGoogle } = useAuth();

  if (status === "ready") return <Navigate to="/" replace />;

  const isBusy = status === "loading";
  const isUnavailable = status === "config-missing" || !hasSupabaseConfig;

  return (
    <AppShell title="Acesso">
      <section className="mx-auto max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-red-50 text-xl font-bold text-red-700">
          N
        </div>

        <h2 className="mt-5 text-xl font-semibold">Entrar no Neves Estoque</h2>
        <p className="mt-2 text-sm leading-6 text-zinc-600">
          Use uma das contas Google autorizadas pela Panificadora Neves.
        </p>

        {status === "unauthorized" ? (
          <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-800">
            Esta conta Google não está autorizada a acessar o Neves Estoque.
          </div>
        ) : null}

        {status === "device-blocked" ? (
          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
            {errorMessage ?? "Este dispositivo não está autorizado."}
          </div>
        ) : null}

        {isUnavailable ? (
          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
            O ambiente ainda não possui as variáveis públicas necessárias do Supabase.
          </div>
        ) : null}

        {errorMessage && status !== "unauthorized" && status !== "device-blocked" ? (
          <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-800">
            {errorMessage}
          </div>
        ) : null}

        <button
          type="button"
          disabled={isBusy || isUnavailable}
          onClick={() => void signInWithGoogle()}
          className="mt-6 w-full rounded-xl bg-red-700 px-4 py-3 text-sm font-semibold text-white transition hover:bg-red-800 disabled:cursor-not-allowed disabled:bg-zinc-200 disabled:text-zinc-500"
        >
          {isBusy ? "Verificando…" : "Entrar com Google"}
        </button>

        <p className="mt-4 text-xs leading-5 text-zinc-500">
          O Google confirma sua identidade. O acesso ao estoque só é liberado quando a conta também
          está autorizada internamente no sistema.
        </p>
      </section>
    </AppShell>
  );
}

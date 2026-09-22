import { Navigate } from "react-router-dom";
import nevesLogo from "../../../assets/neves-logo.webp";
import { AppShell } from "../../../shared/components/AppShell";
import { hasSupabaseConfig } from "../../../shared/lib/env";
import { useAuth } from "../context/AuthContext";

function GoogleIcon() {
  return (
    <span
      aria-hidden="true"
      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white shadow-sm"
    >
      <svg viewBox="0 0 24 24" className="h-4 w-4" focusable="false">
        <path
          fill="#4285F4"
          d="M21.6 12.23c0-.71-.06-1.4-.18-2.07H12v3.91h5.38a4.6 4.6 0 0 1-2 3.02v2.54h3.24c1.9-1.75 2.98-4.33 2.98-7.4Z"
        />
        <path
          fill="#34A853"
          d="M12 22c2.7 0 4.98-.9 6.64-2.43l-3.24-2.54c-.9.6-2.05.96-3.4.96-2.61 0-4.82-1.76-5.61-4.13H3.04v2.62A10 10 0 0 0 12 22Z"
        />
        <path
          fill="#FBBC05"
          d="M6.39 13.86A6 6 0 0 1 6.08 12c0-.65.11-1.28.31-1.86V7.52H3.04A10 10 0 0 0 2 12c0 1.61.38 3.14 1.04 4.48l3.35-2.62Z"
        />
        <path
          fill="#EA4335"
          d="M12 6.01c1.47 0 2.79.51 3.83 1.5l2.87-2.87A9.63 9.63 0 0 0 12 2a10 10 0 0 0-8.96 5.52l3.35 2.62C7.18 7.77 9.39 6.01 12 6.01Z"
        />
      </svg>
    </span>
  );
}

export function LoginPage() {
  const { status, errorMessage, signInWithGoogle } = useAuth();

  if (status === "ready") return <Navigate to="/" replace />;

  const isBusy = status === "loading";
  const isUnavailable = status === "config-missing" || !hasSupabaseConfig;

  return (
    <AppShell title="Acesso">
      <section className="mx-auto max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
        <img
          src={nevesLogo}
          alt="Panificadora Neves"
          className="mx-auto w-full max-w-xs rounded-2xl shadow-sm"
        />

        <h2 className="mt-6 text-xl font-semibold">Entrar no Estoque Neves</h2>
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
          className="mt-6 inline-flex w-full items-center justify-center gap-3 rounded-xl bg-red-700 px-4 py-3 text-sm font-semibold text-white transition hover:bg-red-800 disabled:cursor-not-allowed disabled:bg-zinc-200 disabled:text-zinc-500"
        >
          <GoogleIcon />
          <span>{isBusy ? "Verificando…" : "Entrar com Google"}</span>
        </button>

        <p className="mt-4 text-xs leading-5 text-zinc-500">
          O Google confirma sua identidade. O acesso ao estoque só é liberado quando a conta também
          está autorizada internamente no sistema.
        </p>
      </section>
    </AppShell>
  );
}

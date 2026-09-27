import { useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import nevesLogo from "../../../assets/neves-logo.webp";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { TextField } from "../../../shared/components/ui/TextField";
import { hasSupabaseConfig } from "../../../shared/lib/env";
import { useAuth } from "../context/AuthContext";

function GoogleIcon() {
  return (
    <span
      aria-hidden="true"
      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white shadow-sm"
    >
      <svg viewBox="0 0 24 24" className="h-4 w-4" focusable="false">
        <path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.18-2.07H12v3.91h5.38a4.6 4.6 0 0 1-2 3.02v2.54h3.24c1.9-1.75 2.98-4.33 2.98-7.4Z" />
        <path fill="#34A853" d="M12 22c2.7 0 4.98-.9 6.64-2.43l-3.24-2.54c-.9.6-2.05.96-3.4.96-2.61 0-4.82-1.76-5.61-4.13H3.04v2.62A10 10 0 0 0 12 22Z" />
        <path fill="#FBBC05" d="M6.39 13.86A6 6 0 0 1 6.08 12c0-.65.11-1.28.31-1.86V7.52H3.04A10 10 0 0 0 2 12c0 1.61.38 3.14 1.04 4.48l3.35-2.62Z" />
        <path fill="#EA4335" d="M12 6.01c1.47 0 2.79.51 3.83 1.5l2.87-2.87A9.63 9.63 0 0 0 12 2a10 10 0 0 0-8.96 5.52l3.35 2.62C7.18 7.77 9.39 6.01 12 6.01Z" />
      </svg>
    </span>
  );
}

export function LoginPage() {
  const { status, errorMessage, signInWithGoogle, signInWithUsername } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  if (status === "ready") return <Navigate to="/" replace />;

  const isBusy = status === "loading";
  const isOfflineUnavailable = status === "offline-unavailable";
  const isUnavailable =
    status === "config-missing" || isOfflineUnavailable || !hasSupabaseConfig;

  const submitSecondaryAccess = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isBusy || isUnavailable) return;

    const cleanUsername = username.trim();
    if (!cleanUsername) {
      setUsernameError("Informe o nome de usuário.");
      setPasswordError(null);
      window.setTimeout(() => document.getElementById("secondary-username")?.focus(), 0);
      return;
    }

    if (!password) {
      setUsernameError(null);
      setPasswordError("Informe a senha.");
      window.setTimeout(() => document.getElementById("secondary-password")?.focus(), 0);
      return;
    }

    setUsernameError(null);
    setPasswordError(null);
    void signInWithUsername(cleanUsername, password);
  };

  return (
    <AppShell title="Acesso">
      <section className="mx-auto max-w-md overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm">
        <div className="border-b border-zinc-100 bg-zinc-950 px-6 py-5">
          <img
            src={nevesLogo}
            alt="Panificadora Neves"
            className="mx-auto w-full max-w-xs rounded-2xl bg-white/5 object-contain shadow-sm ring-1 ring-white/10"
          />
        </div>

        <div className="p-6">
          <p className="text-xs font-extrabold uppercase tracking-[0.16em] text-red-700">
            Neves <span className="text-amber-600">•</span> Estoque
          </p>
          <h2 className="mt-2 text-2xl font-semibold tracking-tight">Entrar no sistema</h2>
          <p className="mt-2 text-sm leading-6 text-zinc-600">
            Use seu usuário e senha para acessos secundários. Contas mestre continuam entrando pelo Google.
          </p>

          {status === "unauthorized" ? (
            <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-800">
              {errorMessage ?? "Este acesso não está autorizado para o Neves Estoque."}
            </div>
          ) : null}

          {status === "device-blocked" ? (
            <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
              {errorMessage ?? "Este dispositivo não está autorizado."}
            </div>
          ) : null}

          {isOfflineUnavailable ? (
            <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
              {errorMessage ??
                "Sem internet. Este aparelho ainda precisa ser validado online antes do primeiro uso offline."}
            </div>
          ) : null}

          {status === "config-missing" || !hasSupabaseConfig ? (
            <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
              O ambiente ainda não possui as variáveis públicas necessárias do Supabase.
            </div>
          ) : null}

          {errorMessage &&
          status !== "unauthorized" &&
          status !== "device-blocked" &&
          status !== "offline-unavailable" ? (
            <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm leading-6 text-red-800">
              {errorMessage}
            </div>
          ) : null}

          <form onSubmit={submitSecondaryAccess} className="mt-6">
            <div className="rounded-2xl border border-red-100 bg-red-50/40 p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
                Usuário e senha
              </p>
              <div className="mt-4 space-y-4">
                <TextField
                  id="secondary-username"
                  label="Nome de usuário"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  value={username}
                  error={usernameError}
                  onChange={(event) => {
                    setUsername(event.target.value);
                    if (usernameError) setUsernameError(null);
                  }}
                  placeholder="Ex.: marcio"
                  disabled={isBusy || isUnavailable}
                />
                <TextField
                  id="secondary-password"
                  label="Senha"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  error={passwordError}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    if (passwordError) setPasswordError(null);
                  }}
                  placeholder="Digite sua senha"
                  disabled={isBusy || isUnavailable}
                />
              </div>

              <Button
                type="submit"
                className="mt-5 w-full"
                disabled={isUnavailable}
                isLoading={isBusy}
                loadingLabel="Verificando…"
              >
                Entrar
              </Button>
            </div>
          </form>

          <div className="my-6 flex items-center gap-3" aria-hidden="true">
            <div className="h-px flex-1 bg-zinc-200" />
            <span className="text-xs font-semibold uppercase tracking-[0.14em] text-zinc-400">ou</span>
            <div className="h-px flex-1 bg-zinc-200" />
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-zinc-500">
              Conta mestre
            </p>
            <button
              type="button"
              disabled={isBusy || isUnavailable}
              onClick={() => void signInWithGoogle()}
              className="mt-3 inline-flex min-h-11 w-full items-center justify-center gap-3 rounded-xl border border-zinc-300 bg-zinc-950 px-4 py-3 text-sm font-semibold text-white transition hover:bg-zinc-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:border-zinc-200 disabled:bg-zinc-200 disabled:text-zinc-500"
            >
              <GoogleIcon />
              <span>Entrar com Google</span>
            </button>
            <p className="mt-3 text-xs leading-5 text-zinc-500">
              O Google permanece como acesso principal das contas administradoras.
            </p>
          </div>
        </div>
      </section>
    </AppShell>
  );
}

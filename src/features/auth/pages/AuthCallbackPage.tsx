import { Navigate } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { useAuth } from "../context/AuthContext";

export function AuthCallbackPage() {
  const { status } = useAuth();

  if (status === "ready") return <Navigate to="/" replace />;
  if (status !== "loading") return <Navigate to="/login" replace />;

  return (
    <AppShell title="Acesso">
      <section className="mx-auto max-w-md rounded-2xl border border-zinc-200 bg-white p-6 text-center shadow-sm">
        <div
          aria-hidden="true"
          className="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-zinc-200 border-t-red-700"
        />
        <h2 className="mt-4 text-lg font-semibold">Confirmando seu acesso</h2>
        <p className="mt-2 text-sm leading-6 text-zinc-600">
          Estamos validando sua conta Google e este dispositivo no Neves Estoque.
        </p>
      </section>
    </AppShell>
  );
}

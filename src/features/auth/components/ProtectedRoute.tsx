import type { PropsWithChildren } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function ProtectedRoute({ children }: PropsWithChildren) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-zinc-50 px-4">
        <div className="rounded-2xl border border-zinc-200 bg-white px-6 py-5 text-sm text-zinc-600 shadow-sm">
          Verificando acesso…
        </div>
      </div>
    );
  }

  if (status !== "ready") {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}

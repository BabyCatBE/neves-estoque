import { createBrowserRouter } from "react-router-dom";
import { ProtectedRoute } from "../../features/auth/components/ProtectedRoute";
import { AuthCallbackPage } from "../../features/auth/pages/AuthCallbackPage";
import { LoginPage } from "../../features/auth/pages/LoginPage";
import { HomePage } from "../../features/home/pages/HomePage";
import { ModulePlaceholder } from "../../shared/components/ModulePlaceholder";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/auth/callback", element: <AuthCallbackPage /> },
  {
    path: "/",
    element: (
      <ProtectedRoute>
        <HomePage />
      </ProtectedRoute>
    )
  },
  {
    path: "/estoque",
    element: (
      <ProtectedRoute>
        <ModulePlaceholder title="Estoque Atual" />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias",
    element: (
      <ProtectedRoute>
        <ModulePlaceholder title="Conferências" />
      </ProtectedRoute>
    )
  },
  {
    path: "/entradas",
    element: (
      <ProtectedRoute>
        <ModulePlaceholder title="Entradas" />
      </ProtectedRoute>
    )
  },
  {
    path: "/produtos",
    element: (
      <ProtectedRoute>
        <ModulePlaceholder title="Produtos" />
      </ProtectedRoute>
    )
  },
  {
    path: "/fornecedores",
    element: (
      <ProtectedRoute>
        <ModulePlaceholder title="Fornecedores" />
      </ProtectedRoute>
    )
  },
  {
    path: "/compras",
    element: (
      <ProtectedRoute>
        <ModulePlaceholder title="Compras" />
      </ProtectedRoute>
    )
  }
]);

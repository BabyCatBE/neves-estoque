import { createBrowserRouter } from "react-router-dom";
import { ProtectedRoute } from "../../features/auth/components/ProtectedRoute";
import { AuthCallbackPage } from "../../features/auth/pages/AuthCallbackPage";
import { LoginPage } from "../../features/auth/pages/LoginPage";
import { CategoriesPage } from "../../features/categories/pages/CategoriesPage";
import { CategoryTrashPage } from "../../features/categories/pages/CategoryTrashPage";
import { HomePage } from "../../features/home/pages/HomePage";
import { ProductsHubPage } from "../../features/products/pages/ProductsHubPage";
import { ProductDetailPage } from "../../features/products/pages/ProductDetailPage";
import { ProductsPage } from "../../features/products/pages/ProductsPage";
import { TrashPage } from "../../features/trash/pages/TrashPage";
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
        <ProductsHubPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/produtos/lista",
    element: (
      <ProtectedRoute>
        <ProductsPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/produtos/:productId",
    element: (
      <ProtectedRoute>
        <ProductDetailPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/produtos/categorias",
    element: (
      <ProtectedRoute>
        <CategoriesPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/produtos/categorias/lixeira",
    element: (
      <ProtectedRoute>
        <CategoryTrashPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/alertas/lixeira",
    element: (
      <ProtectedRoute>
        <TrashPage />
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

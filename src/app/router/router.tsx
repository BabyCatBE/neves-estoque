import { createBrowserRouter } from "react-router-dom";
import { ProtectedRoute } from "../../features/auth/components/ProtectedRoute";
import { AuthCallbackPage } from "../../features/auth/pages/AuthCallbackPage";
import { LoginPage } from "../../features/auth/pages/LoginPage";
import { CategoriesPage } from "../../features/categories/pages/CategoriesPage";
import { CategoryTrashPage } from "../../features/categories/pages/CategoryTrashPage";
import { HomePage } from "../../features/home/pages/HomePage";
import { AlertsHubPage } from "../../features/alerts/pages/AlertsHubPage";
import { ProductsHubPage } from "../../features/products/pages/ProductsHubPage";
import { ProductDetailPage } from "../../features/products/pages/ProductDetailPage";
import { ProductsPage } from "../../features/products/pages/ProductsPage";
import { TrashPage } from "../../features/trash/pages/TrashPage";
import { SupplierDetailPage } from "../../features/suppliers/pages/SupplierDetailPage";
import { SuppliersPage } from "../../features/suppliers/pages/SuppliersPage";
import { EntriesHubPage } from "../../features/entries/pages/EntriesHubPage";
import { NewEntryPage } from "../../features/entries/pages/NewEntryPage";
import { EntriesHistoryPage } from "../../features/entries/pages/EntriesHistoryPage";
import { EntryDetailPage } from "../../features/entries/pages/EntryDetailPage";
import { EditEntryPage } from "../../features/entries/pages/EditEntryPage";
import { ConferencesHubPage } from "../../features/conferences/pages/ConferencesHubPage";
import { ConferencePrintPage } from "../../features/conferences/pages/ConferencePrintPage";
import { ConferenceCategoriesPage } from "../../features/conferences/pages/ConferenceCategoriesPage";
import { NewCategoryConferencePage } from "../../features/conferences/pages/NewCategoryConferencePage";
import { ConferenceHistoryCategoriesPage } from "../../features/conferences/pages/ConferenceHistoryCategoriesPage";
import { CategoryConferenceHistoryPage } from "../../features/conferences/pages/CategoryConferenceHistoryPage";
import { ConferenceDetailPage } from "../../features/conferences/pages/ConferenceDetailPage";
import { EditConferencePage } from "../../features/conferences/pages/EditConferencePage";
import { StockCurrentPage } from "../../features/stock/pages/StockCurrentPage";
import { PurchasesHubPage } from "../../features/purchases/pages/PurchasesHubPage";
import { PurchaseSupplierSelectPage } from "../../features/purchases/pages/PurchaseSupplierSelectPage";
import { PurchaseSupplierPage } from "../../features/purchases/pages/PurchaseSupplierPage";

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
        <StockCurrentPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias",
    element: (
      <ProtectedRoute>
        <ConferencesHubPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias/imprimir",
    element: (
      <ProtectedRoute>
        <ConferencePrintPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias/fazer",
    element: (
      <ProtectedRoute>
        <ConferenceCategoriesPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias/fazer/:categoryId",
    element: (
      <ProtectedRoute>
        <NewCategoryConferencePage />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias/historico",
    element: (
      <ProtectedRoute>
        <ConferenceHistoryCategoriesPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias/historico/:categoryId",
    element: (
      <ProtectedRoute>
        <CategoryConferenceHistoryPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias/:conferenceId/editar",
    element: (
      <ProtectedRoute>
        <EditConferencePage />
      </ProtectedRoute>
    )
  },
  {
    path: "/conferencias/:conferenceId",
    element: (
      <ProtectedRoute>
        <ConferenceDetailPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/entradas",
    element: (
      <ProtectedRoute>
        <EntriesHubPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/entradas/nova",
    element: (
      <ProtectedRoute>
        <NewEntryPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/entradas/historico",
    element: (
      <ProtectedRoute>
        <EntriesHistoryPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/entradas/:entryId/editar",
    element: (
      <ProtectedRoute>
        <EditEntryPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/entradas/:entryId",
    element: (
      <ProtectedRoute>
        <EntryDetailPage />
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
    path: "/alertas",
    element: (
      <ProtectedRoute>
        <AlertsHubPage />
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
        <SuppliersPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/fornecedores/:supplierId",
    element: (
      <ProtectedRoute>
        <SupplierDetailPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/compras",
    element: (
      <ProtectedRoute>
        <PurchasesHubPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/compras/fornecedor",
    element: (
      <ProtectedRoute>
        <PurchaseSupplierSelectPage />
      </ProtectedRoute>
    )
  },
  {
    path: "/compras/fornecedor/:supplierId",
    element: (
      <ProtectedRoute>
        <PurchaseSupplierPage />
      </ProtectedRoute>
    )
  }
]);

import { createBrowserRouter } from "react-router-dom";
import { LoginPage } from "../../features/auth/pages/LoginPage";
import { HomePage } from "../../features/home/pages/HomePage";
import { ModulePlaceholder } from "../../shared/components/ModulePlaceholder";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/", element: <HomePage /> },
  { path: "/estoque", element: <ModulePlaceholder title="Estoque Atual" /> },
  { path: "/conferencias", element: <ModulePlaceholder title="Conferências" /> },
  { path: "/entradas", element: <ModulePlaceholder title="Entradas" /> },
  { path: "/produtos", element: <ModulePlaceholder title="Produtos" /> },
  { path: "/fornecedores", element: <ModulePlaceholder title="Fornecedores" /> },
  { path: "/compras", element: <ModulePlaceholder title="Compras" /> }
]);

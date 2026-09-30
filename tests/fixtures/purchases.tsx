import { createRoot } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { PurchaseListEditor } from "../../src/features/purchases/components/PurchaseListEditor";
import "../../src/styles/index.css";

const items = [
  { productId: "farinha", productName: "Farinha", unit: "KG", currentQuantity: 10 },
  { productId: "acucar", productName: "Açúcar", unit: "KG", currentQuantity: 5 },
  { productId: "oleo", productName: "Óleo", unit: "UN", currentQuantity: 2,
    projection: { status: "recommended" as const, risk: "risk" as const,
      suggestedQuantity: 3, daysUntilNextOrder: 3, targetStock: 5, coverageDays: 2, cycleDays: 5 } }
];
const router = createMemoryRouter([{ path: "/", element: (
  <PurchaseListEditor items={items} orderTitle="Lista de compras — Neves"
    listTitle="Produtos" intro="Teste do fluxo de Compras" emptyText="Sem produtos" />
) }]);
createRoot(document.getElementById("root")!).render(<RouterProvider router={router} />);

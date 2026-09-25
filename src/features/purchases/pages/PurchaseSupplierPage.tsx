import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { getSupplierDetails } from "../../suppliers/api/suppliers";
import { PurchaseListEditor } from "../components/PurchaseListEditor";
import { listSupplierPurchaseProducts } from "../api/purchases";

export function PurchaseSupplierPage() {
  const { supplierId } = useParams();

  const supplierQuery = useQuery({
    queryKey: ["suppliers", "detail", supplierId],
    queryFn: () => getSupplierDetails(supplierId ?? ""),
    enabled: Boolean(supplierId)
  });

  const productsQuery = useQuery({
    queryKey: ["purchases", "supplier-products", supplierId],
    queryFn: () => listSupplierPurchaseProducts(supplierId ?? ""),
    enabled: Boolean(supplierId)
  });

  return (
    <AppShell title="Compras · Por fornecedor" showBack backTo="/compras/fornecedor">
      <section>
        {supplierQuery.isPending || productsQuery.isPending ? (
          <Card className="p-5 text-sm text-zinc-600">Carregando simulação…</Card>
        ) : null}

        {supplierQuery.isError || productsQuery.isError ? (
          <Card className="border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar esta simulação de compra.
          </Card>
        ) : null}

        {supplierQuery.data && productsQuery.data ? (
          <>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
              Por fornecedor
            </p>
            <h2 className="mt-1 text-2xl font-semibold tracking-tight">{supplierQuery.data.name}</h2>
            <PurchaseListEditor
              items={productsQuery.data}
              orderTitle={`Pedido — ${supplierQuery.data.name}`}
              listTitle="Produtos já comprados deste fornecedor"
              intro="Selecione os itens e informe manualmente quanto deseja comprar."
              emptyText="Ainda não há Produto ativo com Entrada válida registrada para este fornecedor."
            />
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

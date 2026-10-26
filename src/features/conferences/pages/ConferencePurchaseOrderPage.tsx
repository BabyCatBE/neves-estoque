import { useEffect, useState } from "react";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { useAuth } from "../../auth/context/AuthContext";
import { PurchaseListEditor } from "../../purchases/components/PurchaseListEditor";
import {
  clearMarkedProducts,
  readMarkedProducts,
  toggleMarkedProduct,
  type MarkedPurchaseProduct
} from "../lib/conferencePurchaseSelection";

export function ConferencePurchaseOrderPage() {
  const { appUserId, session } = useAuth();
  const ownerId = appUserId ?? session?.user.id ?? "";
  const [products, setProducts] = useState<MarkedPurchaseProduct[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    setLoaded(false);
    if (!ownerId) {
      setError("Usuário indisponível para consultar os produtos marcados.");
      setLoaded(true);
      return;
    }
    void readMarkedProducts(ownerId).then((items) => {
      if (active) {
        setProducts(items);
        setError(null);
        setLoaded(true);
      }
    }).catch(() => {
      if (active) {
        setError("Não foi possível ler os produtos marcados neste navegador.");
        setLoaded(true);
      }
    });
    return () => { active = false; };
  }, [ownerId]);

  const removeProduct = (productId: string) => {
    const product = products.find((item) => item.productId === productId);
    if (!product) return;
    void toggleMarkedProduct(ownerId, product).then((updated) => {
      setProducts(updated);
      setError(null);
    }).catch(() => {
      setError("Não foi possível remover o produto da seleção local. Tente novamente.");
    });
  };

  return (
    <AppShell title="Pedido da Conferência" showBack backTo="/conferencias/fazer">
      <section>
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Produtos marcados</p>
        <h2 className="mt-1 text-2xl font-semibold tracking-tight">Preparar pedido</h2>
        {error ? <Card role="alert" className="mt-4 border-red-200 p-4 text-sm text-red-700">{error}</Card> : null}
        {!loaded ? <Card className="mt-4 p-4 text-sm text-zinc-600">Carregando seleção…</Card> : null}
        {loaded && !error ? (
          <>
            <PurchaseListEditor
              key={ownerId + ":" + (products.length === 0 ? "empty" : "active")}
              items={products.map((item) => ({
                productId: item.productId,
                productName: item.productName,
                unit: item.unit,
                currentQuantity: item.currentQuantity
              }))}
              initialSelectedIds={products.map((item) => item.productId)}
              onRemoveProduct={removeProduct}
              orderTitle="Lista de compras — Conferência"
              listTitle="Produtos selecionados"
              intro="Informe o quanto deseja comprar de cada produto. A contagem física não determina automaticamente o pedido."
              emptyText="Nenhum produto marcado. Volte à Conferência e selecione os que precisa pedir."
            />
            {products.length > 0 ? (
              <Button variant="secondary" className="mt-4" onClick={() => setConfirmClear(true)}>
                Limpar produtos marcados
              </Button>
            ) : null}
            <p className="mt-3 text-xs text-zinc-500">
              Esta é uma simulação local. Não altera estoque, Entradas nem fornecedores.
            </p>
          </>
        ) : null}
      </section>

      {confirmClear ? (
        <div role="dialog" aria-modal="true" aria-labelledby="conference-purchase-clear-title" className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4">
          <Card className="w-full max-w-md p-5 shadow-xl">
            <h3 id="conference-purchase-clear-title" className="text-xl font-semibold">Limpar produtos marcados?</h3>
            <p className="mt-2 text-sm text-zinc-600">
              Os produtos deixarão de aparecer neste pedido. A Conferência física e suas quantidades não serão alteradas.
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <Button variant="ghost" disabled={busy} onClick={() => setConfirmClear(false)}>Cancelar</Button>
              <Button isLoading={busy} onClick={() => {
                setBusy(true);
                void clearMarkedProducts(ownerId).then(() => {
                  setProducts([]);
                  setError(null);
                  setConfirmClear(false);
                }).catch(() => {
                  setError("Não foi possível limpar a seleção. Os dados foram preservados.");
                  setConfirmClear(false);
                }).finally(() => setBusy(false));
              }}>Limpar seleção</Button>
            </div>
          </Card>
        </div>
      ) : null}
    </AppShell>
  );
}

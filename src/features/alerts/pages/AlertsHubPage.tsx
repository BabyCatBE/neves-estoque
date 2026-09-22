import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { Card } from "../../../shared/components/ui/Card";
import { listActiveProducts } from "../../products/api/products";
import { listActiveSuppliers } from "../../suppliers/api/suppliers";
import { listRestorableTrashItems } from "../../trash/api/trash";

export function AlertsHubPage() {
  const trashQuery = useQuery({ queryKey: ["trash", "restorable"], queryFn: listRestorableTrashItems });
  const suppliersQuery = useQuery({ queryKey: ["suppliers", "active"], queryFn: listActiveSuppliers });
  const productsQuery = useQuery({ queryKey: ["products", "active"], queryFn: listActiveProducts });

  const trashCount = trashQuery.data?.length ?? 0;
  const pendingSuppliers = (suppliersQuery.data ?? []).filter((supplier) => supplier.isPending);
  const pendingProducts = (productsQuery.data ?? []).filter((product) => product.categoryId === null);
  const pendingCount = pendingSuppliers.length + pendingProducts.length;
  const loading = trashQuery.isPending || suppliersQuery.isPending || productsQuery.isPending;

  return (
    <AppShell title="Alertas" showBack backTo="/">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Alertas e recuperação</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          A Lixeira é uma área de recuperação. Abaixo ficam apenas cadastros e tarefas que exigem atenção.
        </p>

        {loading ? <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando alertas…</Card> : null}

        {!loading ? (
          <>
            <div className="mt-6">
              <p className="mb-2 text-xs font-semibold uppercase tracking-[0.14em] text-zinc-400">
                Recuperação
              </p>
              <Link to="/alertas/lixeira" className="block">
                <InteractiveCard className="p-5 sm:p-6">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                    <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-red-50 text-red-700">
                      <TrashIcon />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-lg font-semibold">Lixeira Universal</h3>
                        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                          trashCount > 0 ? "bg-red-50 text-red-800" : "bg-zinc-100 text-zinc-500"
                        }`}>
                          {trashCount}
                        </span>
                      </div>
                      <p className="mt-1 text-sm leading-6 text-zinc-600">
                        Produtos, Categorias, Fornecedores e Entradas restauráveis por 7 dias.
                      </p>
                    </div>
                    <span className="text-sm font-semibold text-red-700">Abrir Lixeira ›</span>
                  </div>
                </InteractiveCard>
              </Link>
            </div>

            <div className="mt-8">
              <div className="flex flex-wrap items-end justify-between gap-2">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Pendências</p>
                  <h3 className="mt-1 text-lg font-semibold">Cadastros que precisam de atenção</h3>
                </div>
                <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
                  pendingCount > 0 ? "bg-amber-50 text-amber-900" : "bg-zinc-100 text-zinc-500"
                }`}>
                  {pendingCount} pendência{pendingCount === 1 ? "" : "s"}
                </span>
              </div>

              {pendingCount === 0 ? (
                <Card className="mt-4 p-5 text-sm text-zinc-600">
                  Nenhum cadastro pendente no momento.
                </Card>
              ) : null}

              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <Link to="/fornecedores?filter=pending" className="block">
                  <PendingCard
                    icon={<SupplierIcon />}
                    title="Fornecedores pendentes"
                    count={pendingSuppliers.length}
                    description="Cadastros rápidos que ainda precisam de Empresa e/ou Telefone."
                    active={pendingSuppliers.length > 0}
                  />
                </Link>

                <Link to="/produtos/lista?filter=pending" className="block">
                  <PendingCard
                    icon={<ProductIcon />}
                    title="Produtos pendentes"
                    count={pendingProducts.length}
                    description="Produtos sem Categoria que ainda precisam ter o cadastro concluído."
                    active={pendingProducts.length > 0}
                  />
                </Link>
              </div>
            </div>
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function PendingCard({
  icon,
  title,
  count,
  description,
  active
}: {
  icon: ReactNode;
  title: string;
  count: number;
  description: string;
  active: boolean;
}) {
  return (
    <InteractiveCard className="h-full p-5 sm:min-h-48">
      <div className="flex items-start justify-between gap-3">
        <div className={`flex h-11 w-11 items-center justify-center rounded-xl ${
          active ? "bg-amber-50 text-amber-800" : "bg-zinc-100 text-zinc-500"
        }`}>
          {icon}
        </div>
        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
          active ? "bg-amber-50 text-amber-900" : "bg-zinc-100 text-zinc-500"
        }`}>
          {count}
        </span>
      </div>
      <h3 className="mt-4 text-lg font-semibold">{title}</h3>
      <p className="mt-2 text-sm leading-6 text-zinc-600">{description}</p>
    </InteractiveCard>
  );
}

function TrashIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none"><path d="M5 7h14M9 7V4h6v3M8 10v7M12 10v7M16 10v7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M7 7l1 13h8l1-13" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /></svg>;
}
function SupplierIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none"><circle cx="9" cy="8" r="3" stroke="currentColor" strokeWidth="1.8" /><path d="M3.5 19c.7-3.2 2.5-5 5.5-5s4.8 1.8 5.5 5M16 8h5M18.5 5.5v5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>;
}
function ProductIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none"><path d="M6 7.5 12 4l6 3.5v9L12 20l-6-3.5v-9Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /><path d="m6 7.5 6 3.5 6-3.5M12 11v9" stroke="currentColor" strokeWidth="1.8" /></svg>;
}

import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
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
  const total = trashCount + pendingSuppliers.length + pendingProducts.length;
  const loading = trashQuery.isPending || suppliersQuery.isPending || productsQuery.isPending;

  return (
    <AppShell title="Alertas" showBack backTo="/">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Alertas e recuperação</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          Pendências que precisam de atenção e itens que ainda podem ser restaurados.
        </p>

        {loading ? <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando alertas…</Card> : null}

        {!loading && total === 0 ? (
          <Card className="mt-5 p-7 text-center">
            <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-zinc-100 text-zinc-500">
              <AlertIcon />
            </div>
            <h3 className="mt-3 font-semibold">Nenhum alerta ativo</h3>
            <p className="mt-1 text-sm text-zinc-500">Não há cadastros pendentes nem itens restauráveis na Lixeira.</p>
          </Card>
        ) : null}

        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Link to="/alertas/lixeira" className="block">
            <AlertCard
              icon={<TrashIcon />}
              title="Lixeira Universal"
              count={trashCount}
              description="Produtos, Categorias, Fornecedores e Entradas restauráveis por 7 dias."
              active={trashCount > 0}
            />
          </Link>

          <Link to="/fornecedores?filter=pending" className="block">
            <AlertCard
              icon={<SupplierIcon />}
              title="Fornecedores pendentes"
              count={pendingSuppliers.length}
              description="Cadastros rápidos que ainda precisam de Empresa e/ou Telefone."
              active={pendingSuppliers.length > 0}
            />
          </Link>

          <Link to="/produtos/lista?filter=pending" className="block">
            <AlertCard
              icon={<ProductIcon />}
              title="Produtos pendentes"
              count={pendingProducts.length}
              description="Produtos sem Categoria que ainda precisam ter o cadastro concluído."
              active={pendingProducts.length > 0}
            />
          </Link>
        </div>
      </section>
    </AppShell>
  );
}

function AlertCard({
  icon,
  title,
  count,
  description,
  active
}: {
  icon: React.ReactNode;
  title: string;
  count: number;
  description: string;
  active: boolean;
}) {
  return (
    <Card className={`h-full p-6 transition hover:-translate-y-0.5 hover:shadow-md ${active ? "border-amber-200" : ""}`}>
      <div className="flex items-start justify-between gap-3">
        <div className={`flex h-11 w-11 items-center justify-center rounded-xl ${active ? "bg-amber-50 text-amber-800" : "bg-zinc-100 text-zinc-500"}`}>
          {icon}
        </div>
        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${active ? "bg-amber-50 text-amber-900" : "bg-zinc-100 text-zinc-500"}`}>
          {count}
        </span>
      </div>
      <h3 className="mt-4 text-lg font-semibold">{title}</h3>
      <p className="mt-2 text-sm leading-6 text-zinc-600">{description}</p>
    </Card>
  );
}

function AlertIcon() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none"><path d="M12 4 3 20h18L12 4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /><path d="M12 9v5M12 17.2v.1" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>;
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

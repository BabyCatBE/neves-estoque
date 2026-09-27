import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Card } from "../../../shared/components/ui/Card";
import { InteractiveCard } from "../../../shared/components/ui/InteractiveCard";
import { SearchClearButton } from "../../../shared/components/ui/SearchClearButton";
import { TextField } from "../../../shared/components/ui/TextField";
import { normalizeSearchText } from "../../../shared/lib/searchText";
import { listActiveSuppliers } from "../../suppliers/api/suppliers";

export function PurchaseSupplierSelectPage() {
  const [search, setSearch] = useState("");
  const suppliersQuery = useQuery({
    queryKey: ["suppliers", "active"],
    queryFn: listActiveSuppliers,
    staleTime: 30_000
  });

  const suppliers = useMemo(() => {
    const term = normalizeSearchText(search);
    return [...(suppliersQuery.data ?? [])]
      .filter((supplier) => {
        if (!term) return true;
        return normalizeSearchText(
          [supplier.name, supplier.company, supplier.phone]
            .filter(Boolean)
            .join(" ")
        ).includes(term);
      })
      .sort((a, b) =>
        a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" })
      );
  }, [search, suppliersQuery.data]);

  return (
    <AppShell title="Compras · Por fornecedor" showBack backTo="/compras">
      <section>
        <h2 className="text-xl font-semibold tracking-tight">Escolha o fornecedor</h2>
        <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
          A relação Produto–Fornecedor nasce das Entradas reais. Escolher um fornecedor aqui não altera nenhum cadastro.
        </p>

        <div className="relative mt-5 max-w-md">
          <TextField
            id="purchase-supplier-search"
            label="Pesquisar"
            placeholder="Fornecedor, empresa ou telefone"
            value={search}
            className={search ? "pr-11" : ""}
            onChange={(event) => setSearch(event.target.value)}
          />
          {search ? (
            <SearchClearButton
              onClear={() => {
                setSearch("");
                document.getElementById("purchase-supplier-search")?.focus();
              }}
            />
          ) : null}
        </div>

        {suppliersQuery.isPending ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando fornecedores…</Card>
        ) : null}

        {suppliersQuery.isError ? (
          <Card className="mt-5 border-red-200 p-5 text-sm text-red-800">
            Não foi possível carregar os fornecedores.
          </Card>
        ) : null}

        {!suppliersQuery.isPending && !suppliersQuery.isError && suppliers.length === 0 ? (
          <Card className="mt-5 p-6 text-center text-sm text-zinc-600">
            {search ? "Nenhum fornecedor encontrado." : "Nenhum fornecedor ativo cadastrado."}
          </Card>
        ) : null}

        {!suppliersQuery.isPending && !suppliersQuery.isError && suppliers.length > 0 ? (
          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {suppliers.map((supplier) => (
              <Link
                key={supplier.id}
                to={`/compras/fornecedor/${supplier.id}`}
                className="block"
              >
                <InteractiveCard className="h-full p-5">
                  <h3 className="font-semibold text-zinc-950">{supplier.name}</h3>
                  <p className="mt-1 text-sm text-zinc-600">
                    {supplier.company || "Empresa não informada"}
                  </p>
                  {supplier.isPending ? (
                    <p className="mt-3 text-xs font-semibold text-amber-700">Cadastro pendente</p>
                  ) : null}
                </InteractiveCard>
              </Link>
            ))}
          </div>
        ) : null}
      </section>
    </AppShell>
  );
}


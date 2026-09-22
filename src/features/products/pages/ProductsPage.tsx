import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import {
  createProduct,
  listActiveProducts,
  listProductCategories,
  type ProductListItem
} from "../api/products";
import {
  getProductErrorMessage,
  parseOptionalNonNegativeDecimal,
  PRODUCT_UNITS,
  productNameSchema,
  type ProductUnit
} from "../lib/productValidation";

type ProductForm = {
  name: string;
  categoryId: string;
  unit: ProductUnit | "";
  initialStock: string;
  initialPrice: string;
};

type ViewMode = "category" | "alphabetical";

const productsKey = ["products", "active"] as const;
const productCategoriesKey = ["products", "categories"] as const;

export function ProductsPage() {
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>("category");
  const [search, setSearch] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const productsQuery = useQuery({
    queryKey: productsKey,
    queryFn: listActiveProducts
  });

  const categoriesQuery = useQuery({
    queryKey: productCategoriesKey,
    queryFn: listProductCategories
  });

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors }
  } = useForm<ProductForm>({
    defaultValues: {
      name: "",
      categoryId: "",
      unit: "",
      initialStock: "",
      initialPrice: ""
    }
  });

  const createMutation = useMutation({
    mutationFn: createProduct,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: productsKey }),
        queryClient.invalidateQueries({ queryKey: ["categories", "active"] })
      ]);
    }
  });

  const onCreate = handleSubmit(async (values) => {
    setNotice(null);
    setActionError(null);

    const parsedName = productNameSchema.safeParse(values.name);
    if (!parsedName.success) {
      setError("name", {
        message: parsedName.error.issues[0]?.message ?? "Nome inválido."
      });
      return;
    }

    if (!values.categoryId) {
      setError("categoryId", { message: "Escolha a categoria." });
      return;
    }

    if (!values.unit || !PRODUCT_UNITS.includes(values.unit as ProductUnit)) {
      setError("unit", { message: "Escolha a unidade." });
      return;
    }

    let initialStockQuantity: number | null;
    let initialPrice: number | null;

    try {
      initialStockQuantity = parseOptionalNonNegativeDecimal(
        values.initialStock,
        "Estoque inicial"
      );
      initialPrice = parseOptionalNonNegativeDecimal(values.initialPrice, "Preço inicial");
    } catch (error) {
      setActionError(getProductErrorMessage(error));
      return;
    }

    try {
      await createMutation.mutateAsync({
        name: parsedName.data,
        categoryId: values.categoryId,
        unit: values.unit as ProductUnit,
        initialStockQuantity,
        initialPrice
      });

      reset();
      setCreating(false);
      setNotice("Produto criado com sucesso.");
    } catch (error) {
      setActionError(getProductErrorMessage(error));
    }
  });

  const categoryById = useMemo(
    () => new Map((categoriesQuery.data ?? []).map((category) => [category.id, category] as const)),
    [categoriesQuery.data]
  );

  const filteredProducts = useMemo(() => {
    const term = normalizeSearch(search);

    return (productsQuery.data ?? [])
      .filter((product) => !term || normalizeSearch(product.name).includes(term))
      .sort((a, b) => a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" }));
  }, [productsQuery.data, search]);

  const hasCategories = (categoriesQuery.data?.length ?? 0) > 0;
  const isLoading = productsQuery.isPending || categoriesQuery.isPending;
  const hasError = productsQuery.isError || categoriesQuery.isError;

  return (
    <AppShell title="Produtos" showBack backTo="/produtos">
      <section>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">Catálogo de produtos</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-zinc-600">
              Consulte o catálogo, pesquise pelo nome e cadastre produtos completos.
            </p>
          </div>

          <Button
            disabled={!hasCategories || createMutation.isPending}
            onClick={() => {
              setNotice(null);
              setActionError(null);
              setCreating((value) => !value);
              reset();
            }}
          >
            {creating ? "Cancelar" : "+ Novo produto"}
          </Button>
        </div>

        {!hasCategories && !categoriesQuery.isPending ? (
          <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900">
            Para cadastrar um produto completo, primeiro é necessário ter uma categoria ativa.{" "}
            <Link to="/produtos/categorias" className="font-semibold underline">
              Ir para Categorias
            </Link>
          </div>
        ) : null}

        {creating ? (
          <Card className="mt-5 p-5">
            <form onSubmit={onCreate}>
              <div className="grid gap-4 lg:grid-cols-2">
                <TextField
                  label="Nome"
                  placeholder="Ex.: Farinha de Trigo 1kg"
                  autoFocus
                  error={errors.name?.message}
                  {...register("name")}
                />

                <SelectField
                  label="Categoria"
                  error={errors.categoryId?.message}
                  {...register("categoryId")}
                >
                  <option value="">Selecione…</option>
                  {categoriesQuery.data?.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </SelectField>

                <SelectField label="Unidade" error={errors.unit?.message} {...register("unit")}>
                  <option value="">Selecione…</option>
                  {PRODUCT_UNITS.map((unit) => (
                    <option key={unit} value={unit}>
                      {unit}
                    </option>
                  ))}
                </SelectField>

                <div className="grid gap-4 sm:grid-cols-2">
                  <TextField
                    label="Estoque inicial (opcional)"
                    placeholder="Ex.: 12,5"
                    inputMode="decimal"
                    {...register("initialStock")}
                  />
                  <TextField
                    label="Preço inicial (opcional)"
                    placeholder="Ex.: 24,90"
                    inputMode="decimal"
                    {...register("initialPrice")}
                  />
                </div>
              </div>

              <div className="mt-4 rounded-xl bg-zinc-50 px-4 py-3 text-xs leading-5 text-zinc-600">
                Estoque inicial em branco significa <strong>Sem dados / Não contabilizado</strong>.
                O preço inicial também é opcional e serve como referência até existir uma Entrada real
                com preço.
              </div>

              {actionError ? (
                <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
                  {actionError}
                </div>
              ) : null}

              <div className="mt-5 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  onClick={() => {
                    reset();
                    setCreating(false);
                    setActionError(null);
                  }}
                >
                  Cancelar
                </Button>
                <Button type="submit" disabled={createMutation.isPending}>
                  {createMutation.isPending ? "Salvando…" : "Salvar produto"}
                </Button>
              </div>
            </form>
          </Card>
        ) : null}

        {notice ? (
          <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
            {notice}
          </div>
        ) : null}

        {!creating && actionError ? (
          <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            {actionError}
          </div>
        ) : null}

        <div className="mt-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div className="w-full sm:max-w-md">
            <TextField
              label="Pesquisar"
              placeholder="Digite qualquer trecho do nome"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>

          <div className="flex gap-2">
            <Button
              size="sm"
              variant={viewMode === "category" ? "primary" : "secondary"}
              onClick={() => setViewMode("category")}
            >
              Por categoria
            </Button>
            <Button
              size="sm"
              variant={viewMode === "alphabetical" ? "primary" : "secondary"}
              onClick={() => setViewMode("alphabetical")}
            >
              Alfabética
            </Button>
          </div>
        </div>

        <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
          Primeiro incremento operacional de Produtos: listagem, busca, visualização por categoria ou
          alfabética e cadastro completo conectado ao banco real. Edição, lixeira e reordenação de
          produtos entram nos próximos incrementos.
        </div>

        {isLoading ? (
          <Card className="mt-5 p-5 text-sm text-zinc-600">Carregando produtos…</Card>
        ) : null}

        {hasError ? (
          <Card className="mt-5 border-red-200 p-5">
            <p className="text-sm font-medium text-red-800">
              Não foi possível carregar o catálogo de produtos.
            </p>
            <Button
              className="mt-4"
              variant="secondary"
              onClick={() => {
                void productsQuery.refetch();
                void categoriesQuery.refetch();
              }}
            >
              Tentar novamente
            </Button>
          </Card>
        ) : null}

        {!isLoading && !hasError && filteredProducts.length === 0 ? (
          <Card className="mt-5 p-6 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-red-50 text-red-700">
              <ProductIcon />
            </div>
            <h3 className="mt-4 font-semibold">
              {search.trim() ? "Nenhum produto encontrado" : "Nenhum produto cadastrado"}
            </h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              {search.trim()
                ? "Tente pesquisar outro trecho do nome."
                : "Use “+ Novo produto” para começar o catálogo."}
            </p>
          </Card>
        ) : null}

        {!isLoading && !hasError && filteredProducts.length > 0 && viewMode === "alphabetical" ? (
          <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {filteredProducts.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                categoryName={categoryById.get(product.categoryId ?? "")?.name ?? "Cadastro pendente"}
              />
            ))}
          </div>
        ) : null}

        {!isLoading && !hasError && filteredProducts.length > 0 && viewMode === "category" ? (
          <div className="mt-5 space-y-3">
            {categoriesQuery.data?.map((category) => {
              const products = filteredProducts
                .filter((product) => product.categoryId === category.id)
                .sort(
                  (a, b) =>
                    (a.sortOrder ?? Number.MAX_SAFE_INTEGER) -
                      (b.sortOrder ?? Number.MAX_SAFE_INTEGER) ||
                    a.name.localeCompare(b.name, "pt-BR", { sensitivity: "base" })
                );

              if (search.trim() && products.length === 0) return null;

              return (
                <details
                  key={category.id}
                  open={search.trim() ? true : undefined}
                  className="group rounded-2xl border border-zinc-200 bg-white shadow-sm"
                >
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-5 py-4">
                    <div>
                      <h3 className="font-semibold text-zinc-900">{category.name}</h3>
                      <p className="mt-1 text-xs text-zinc-500">
                        {products.length === 1 ? "1 produto" : `${products.length} produtos`}
                      </p>
                    </div>
                    <span className="text-zinc-400 transition group-open:rotate-180">⌄</span>
                  </summary>

                  <div className="grid gap-3 border-t border-zinc-100 p-4 sm:grid-cols-2 xl:grid-cols-3">
                    {products.length > 0 ? (
                      products.map((product) => (
                        <ProductCard
                          key={product.id}
                          product={product}
                          categoryName={category.name}
                          compact
                        />
                      ))
                    ) : (
                      <p className="text-sm text-zinc-500">Nenhum produto nesta categoria.</p>
                    )}
                  </div>
                </details>
              );
            })}
          </div>
        ) : null}
      </section>
    </AppShell>
  );
}

function ProductCard({
  product,
  categoryName,
  compact = false
}: {
  product: ProductListItem;
  categoryName: string;
  compact?: boolean;
}) {
  return (
    <Card className={compact ? "p-4 shadow-none" : "p-4"}>
      <div className="flex items-start gap-3">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-red-50 text-red-700">
          <ProductIcon />
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="break-words font-semibold text-zinc-900">{product.name}</h3>
          <p className="mt-1 text-xs text-zinc-500">
            {categoryName} · {product.unit}
          </p>
        </div>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-3 border-t border-zinc-100 pt-3">
        <div>
          <p className="text-[11px] uppercase tracking-wide text-zinc-400">Estoque atual</p>
          <p className="mt-1 text-sm font-semibold text-zinc-800">
            {formatQuantity(product.currentQuantity, product.unit)}
          </p>
        </div>
        <div>
          <p className="text-[11px] uppercase tracking-wide text-zinc-400">Preço atual</p>
          <p className="mt-1 text-sm font-semibold text-zinc-800">
            {formatPrice(product.currentPrice)}
          </p>
        </div>
      </div>
    </Card>
  );
}

function SelectField({
  label,
  error,
  children,
  ...props
}: React.SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  error?: string | null;
}) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-zinc-800">{label}</span>
      <select
        className={`mt-2 min-h-11 w-full rounded-xl border bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100 ${
          error ? "border-red-400" : "border-zinc-300"
        }`}
        {...props}
      >
        {children}
      </select>
      {error ? <span className="mt-1.5 block text-xs font-medium text-red-700">{error}</span> : null}
    </label>
  );
}

function ProductIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none">
      <path
        d="M6 7.5 12 4l6 3.5v9L12 20l-6-3.5v-9Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="m6 7.5 6 3.5 6-3.5M12 11v9" stroke="currentColor" strokeWidth="1.8" />
    </svg>
  );
}

function normalizeSearch(value: string) {
  return value.trim().toLocaleLowerCase("pt-BR");
}

function formatQuantity(value: number | null, unit: string) {
  if (value === null) return "Sem dados";

  return `${new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 2
  }).format(value)} ${unit}`;
}

function formatPrice(value: number | null) {
  if (value === null) return "Sem preço";

  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
  }).format(value);
}

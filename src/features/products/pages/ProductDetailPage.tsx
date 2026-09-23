import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type SelectHTMLAttributes } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { useCtrlEnter } from "../../../shared/hooks/useCtrlEnter";
import { handleDialogButtonArrowNavigation } from "../../../shared/lib/dialogKeyboard";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import {
  getProductDetails,
  listProductCategories,
  softDeleteProduct,
  updateProductDetails
} from "../api/products";
import { getProductErrorMessage, productNameSchema } from "../lib/productValidation";

const productsKey = ["products", "active"] as const;

type ExitReviewMode = "back" | "cancel" | null;

export function ProductDetailPage() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [exitReview, setExitReview] = useState<ExitReviewMode>(null);
  const [deleteReviewOpen, setDeleteReviewOpen] = useState(false);

  const productQuery = useQuery({
    queryKey: ["products", "detail", productId],
    queryFn: () => {
      if (!productId) throw new Error("Produto não encontrado.");
      return getProductDetails(productId);
    },
    enabled: Boolean(productId)
  });

  const categoriesQuery = useQuery({
    queryKey: ["products", "categories"],
    queryFn: listProductCategories
  });

  const updateMutation = useMutation({
    mutationFn: updateProductDetails,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: productsKey }),
        queryClient.invalidateQueries({ queryKey: ["products", "detail", productId] }),
        queryClient.invalidateQueries({ queryKey: ["categories", "active"] })
      ]);
    }
  });

  const deleteMutation = useMutation({
    mutationFn: softDeleteProduct,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: productsKey }),
        queryClient.invalidateQueries({ queryKey: ["categories", "active"] }),
        queryClient.invalidateQueries({ queryKey: ["products", "categories"] }),
        queryClient.invalidateQueries({ queryKey: ["trash", "restorable"] })
      ]);
    }
  });

  const currentCategoryName = useMemo(
    () =>
      categoriesQuery.data?.find((category) => category.id === productQuery.data?.categoryId)?.name ??
      "Sem categoria",
    [categoriesQuery.data, productQuery.data?.categoryId]
  );

  const selectedCategoryName = useMemo(
    () => categoriesQuery.data?.find((category) => category.id === categoryId)?.name ?? "",
    [categoriesQuery.data, categoryId]
  );

  const dirty =
    Boolean(productQuery.data) &&
    (name !== productQuery.data?.name || categoryId !== (productQuery.data?.categoryId ?? ""));

  const changeSummary = useMemo(() => {
    if (!productQuery.data) return [];

    const changes: Array<{ label: string; before: string; after: string }> = [];

    if (name !== productQuery.data.name) {
      changes.push({
        label: "Nome",
        before: productQuery.data.name,
        after: name.trim() || "—"
      });
    }

    if (categoryId !== (productQuery.data.categoryId ?? "")) {
      changes.push({
        label: "Categoria",
        before: currentCategoryName,
        after: selectedCategoryName || "Sem categoria"
      });
    }

    return changes;
  }, [
    categoryId,
    currentCategoryName,
    name,
    productQuery.data,
    selectedCategoryName
  ]);

  const startEditing = () => {
    if (!productQuery.data) return;
    setName(productQuery.data.name);
    setCategoryId(productQuery.data.categoryId ?? "");
    setActionError(null);
    setNotice(null);
    setEditing(true);
  };

  const resetDraft = () => {
    setName(productQuery.data?.name ?? "");
    setCategoryId(productQuery.data?.categoryId ?? "");
    setActionError(null);
  };

  const cancelEditing = () => {
    if (dirty) {
      setExitReview("cancel");
      return;
    }

    resetDraft();
    setEditing(false);
  };

  const leaveProduct = () => {
    if (editing && dirty) {
      setExitReview("back");
      return;
    }

    navigate("/produtos/lista");
  };

  const continueEditing = () => {
    setExitReview(null);
  };

  const discardChanges = () => {
    const mode = exitReview;
    setExitReview(null);
    resetDraft();
    setEditing(false);

    if (mode === "back") {
      navigate("/produtos/lista");
    }
  };

  const deleteProduct = () => {
    if (!productQuery.data) return;

    setActionError(null);
    setNotice(null);
    setDeleteReviewOpen(true);
  };

  const confirmDeleteProduct = async () => {
    if (!productQuery.data) return;

    try {
      await deleteMutation.mutateAsync(productQuery.data.id);
      setDeleteReviewOpen(false);
      navigate("/produtos/lista");
    } catch (error) {
      setActionError(getProductErrorMessage(error));
    }
  };

  const save = async (navigateAfterSave = false) => {
    if (!productQuery.data) return;

    setActionError(null);
    setNotice(null);

    const parsedName = productNameSchema.safeParse(name);
    if (!parsedName.success) {
      setActionError(parsedName.error.issues[0]?.message ?? "Nome inválido.");
      return;
    }

    if (!categoryId) {
      setActionError("Escolha a categoria.");
      return;
    }

    try {
      await updateMutation.mutateAsync({
        id: productQuery.data.id,
        name: parsedName.data,
        categoryId
      });
      setEditing(false);
      setExitReview(null);
      setNotice("Produto atualizado com sucesso.");

      if (navigateAfterSave) {
        navigate("/produtos/lista");
      }
    } catch (error) {
      setActionError(getProductErrorMessage(error));
    }
  };

  useCtrlEnter(
    () => {
      if (editing && dirty && !updateMutation.isPending) void save(false);
    },
    editing &&
      dirty &&
      !updateMutation.isPending &&
      !exitReview &&
      !deleteReviewOpen
  );

  return (
    <AppShell title="Produto" showBack onBack={leaveProduct}>
      {productQuery.isPending || categoriesQuery.isPending ? (
        <Card className="p-5 text-sm text-zinc-600">Carregando produto…</Card>
      ) : null}

      {productQuery.isError || categoriesQuery.isError ? (
        <Card className="border-red-200 p-5">
          <p className="text-sm font-medium text-red-800">
            Não foi possível carregar este produto.
          </p>
          <Button
            className="mt-4"
            variant="secondary"
            onClick={() => {
              void productQuery.refetch();
              void categoriesQuery.refetch();
            }}
          >
            Tentar novamente
          </Button>
        </Card>
      ) : null}

      {productQuery.data && !categoriesQuery.isPending && !categoriesQuery.isError ? (
        <section>
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="flex items-start gap-3">
              <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-red-50 text-red-700">
                <ProductIcon />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
                  Cadastro de produto
                </p>
                <h2 className="mt-1 text-2xl font-semibold tracking-tight text-zinc-950">
                  {productQuery.data.name}
                </h2>
                <p className="mt-1 text-sm text-zinc-500">
                  {currentCategoryName} · {productQuery.data.unit}
                </p>
              </div>
            </div>

            {!editing ? (
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  disabled={deleteMutation.isPending}
                  className="text-red-700"
                  onClick={() => void deleteProduct()}
                >
                  {deleteMutation.isPending ? "Excluindo…" : "Excluir produto"}
                </Button>
                <Button onClick={startEditing}>Editar produto</Button>
              </div>
            ) : null}
          </div>

          {notice ? (
            <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
              {notice}
            </div>
          ) : null}

          {actionError ? (
            <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
              {actionError}
            </div>
          ) : null}

          {editing ? (
            <Card className="mt-5 p-5">
              <div className="grid gap-4 lg:grid-cols-2">
                <TextField
                  label="Nome"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  autoFocus
                />

                <SelectField
                  label="Categoria"
                  value={categoryId}
                  onChange={(event) => setCategoryId(event.target.value)}
                >
                  <option value="">Selecione…</option>
                  {categoriesQuery.data?.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </SelectField>

                <div>
                  <p className="text-sm font-medium text-zinc-800">Unidade</p>
                  <div className="mt-2 min-h-11 rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm font-semibold text-zinc-700">
                    {productQuery.data.unit}
                  </div>
                  <p className="mt-1.5 text-xs leading-5 text-zinc-500">
                    A alteração de unidade exige conversão explícita do histórico e terá um fluxo
                    próprio. Por segurança, não é alterada diretamente aqui.
                  </p>
                </div>

                {categoryId !== productQuery.data.categoryId ? (
                  <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
                    Ao mover para <strong>{selectedCategoryName || "outra categoria"}</strong>, o
                    produto entrará no final da ordem dessa categoria.
                  </div>
                ) : null}
              </div>

              <div className="mt-5 flex flex-wrap justify-end gap-2">
                <Button variant="ghost" disabled={updateMutation.isPending} onClick={cancelEditing}>
                  Cancelar
                </Button>
                <Button
                  disabled={!dirty || updateMutation.isPending}
                  onClick={() => void save(false)}
                >
                  {updateMutation.isPending ? "Salvando…" : "Salvar alterações"}
                </Button>
              </div>
            </Card>
          ) : null}

          <div className="mt-5 grid gap-4 md:grid-cols-3">
            <MetricCard
              label="Estoque atual"
              value={formatQuantity(productQuery.data.currentQuantity, productQuery.data.unit)}
              helper={
                productQuery.data.currentQuantity === null
                  ? "Ainda não existe checkpoint ou entrada que estabeleça o estoque."
                  : "Posição atual calculada pelas regras do estoque."
              }
            />
            <MetricCard
              label="Preço atual"
              value={formatPrice(productQuery.data.currentPrice)}
              helper={
                productQuery.data.currentPrice === null
                  ? "Ainda não existe preço válido registrado."
                  : "Referência vigente até uma Entrada real com novo preço."
              }
            />
            <MetricCard
              label="Valor atual"
              value={formatPrice(productQuery.data.currentValue)}
              helper={
                productQuery.data.currentValue === null
                  ? "Disponível quando estoque e preço estiverem conhecidos."
                  : "Estoque atual × preço atual."
              }
            />
          </div>

          <div className="mt-5 grid gap-4 lg:grid-cols-2">
            <Card className="p-5">
              <h3 className="font-semibold text-zinc-900">Dados do cadastro</h3>
              <dl className="mt-4 space-y-3 text-sm">
                <DetailRow label="Categoria" value={currentCategoryName} />
                <DetailRow label="Unidade" value={productQuery.data.unit} />
                <DetailRow
                  label="Estoque inicial"
                  value={formatOptionalQuantity(
                    productQuery.data.initialStockQuantity,
                    productQuery.data.unit
                  )}
                />
                <DetailRow
                  label="Preço inicial"
                  value={formatPrice(productQuery.data.initialPrice)}
                />
              </dl>
            </Card>

            <Card className="p-5">
              <details>
                <summary className="cursor-pointer font-semibold text-zinc-900">
                  Histórico de preços
                </summary>
                <div className="mt-4 space-y-3 text-sm leading-6 text-zinc-600">
                  {productQuery.data.initialPrice !== null ? (
                    <div className="rounded-xl bg-zinc-50 px-4 py-3">
                      <p className="font-medium text-zinc-800">Referência inicial</p>
                      <p className="mt-1">
                        {formatPrice(productQuery.data.initialPrice)}
                        {productQuery.data.initialPriceAt
                          ? ` · ${formatDate(productQuery.data.initialPriceAt)}`
                          : ""}
                      </p>
                    </div>
                  ) : null}

                  {productQuery.data.priceHistory.length > 0 ? (
                    <div className="space-y-2">
                      {productQuery.data.priceHistory.map((item) => (
                        <div key={item.id} className="rounded-xl border border-zinc-100 bg-zinc-50 px-4 py-3">
                          <div className="flex flex-wrap items-start justify-between gap-2">
                            <div>
                              <p className="font-medium text-zinc-900">
                                {item.unitPrice === null
                                  ? "Preço não informado"
                                  : item.unitPrice === 0
                                    ? "Bonificação"
                                    : formatPrice(item.unitPrice)}
                              </p>
                              <p className="mt-0.5 text-xs text-zinc-500">
                                {formatDate(item.effectiveAt)} · {item.supplierName}
                              </p>
                            </div>
                            <p className="text-xs font-medium text-zinc-600">
                              {formatQuantity(item.quantity, productQuery.data.unit)}
                            </p>
                          </div>
                          {item.unitPrice === null ? (
                            <p className="mt-2 text-xs text-amber-700">
                              Não altera a referência de preço atual.
                            </p>
                          ) : item.unitPrice === 0 ? (
                            <p className="mt-2 text-xs text-amber-700">
                              Bonificação não altera a referência de preço atual.
                            </p>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  ) : productQuery.data.initialPrice === null ? (
                    <p>Nenhum preço registrado até agora.</p>
                  ) : null}
                </div>
              </details>
            </Card>
          </div>

          <ConfirmDialog
            open={deleteReviewOpen}
            variant="danger"
            title="Excluir produto?"
            description={
              productQuery.data
                ? `Excluir o produto “${productQuery.data.name}”?${
                    productQuery.data.currentQuantity !== null &&
                    productQuery.data.currentQuantity > 0
                      ? ` Este produto ainda possui ${formatQuantity(
                          productQuery.data.currentQuantity,
                          productQuery.data.unit
                        )} em estoque. Ao excluir, ele sairá imediatamente do catálogo e dos cálculos ativos.`
                      : ""
                  } Ele ficará na lixeira por 7 dias e poderá ser restaurado nesse período.`
                : ""
            }
            confirmLabel="Excluir produto"
            pendingLabel="Excluindo…"
            isPending={deleteMutation.isPending}
            onCancel={() => setDeleteReviewOpen(false)}
            onConfirm={() => void confirmDeleteProduct()}
          />

          {exitReview ? (
            <div
              role="dialog"
              aria-modal="true"
              aria-labelledby="product-change-summary-title"
              className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
              onKeyDown={handleDialogButtonArrowNavigation}
            >
              <Card className="w-full max-w-lg p-5 shadow-xl">
                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
                  Alterações não salvas
                </p>
                <h3
                  id="product-change-summary-title"
                  className="mt-1 text-xl font-semibold text-zinc-950"
                >
                  Revise antes de sair
                </h3>
                <p className="mt-2 text-sm leading-6 text-zinc-600">
                  Escolha se deseja salvar, descartar ou continuar editando este produto.
                </p>

                <div className="mt-4 space-y-3 rounded-xl bg-zinc-50 p-4">
                  {changeSummary.map((change) => (
                    <div key={change.label} className="text-sm">
                      <p className="font-semibold text-zinc-800">{change.label}</p>
                      <p className="mt-1 break-words text-zinc-500">
                        {change.before} <span aria-hidden="true">→</span>{" "}
                        <span className="font-medium text-zinc-900">{change.after}</span>
                      </p>
                    </div>
                  ))}
                </div>

                <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <Button
                    autoFocus
                    variant="ghost"
                    disabled={updateMutation.isPending}
                    onClick={continueEditing}
                  >
                    Continuar editando
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={updateMutation.isPending}
                    onClick={discardChanges}
                  >
                    Descartar alterações
                  </Button>
                  <Button
                    disabled={updateMutation.isPending}
                    onClick={() => void save(exitReview === "back")}
                  >
                    {updateMutation.isPending ? "Salvando…" : "Salvar alterações"}
                  </Button>
                </div>
              </Card>
            </div>
          ) : null}
        </section>
      ) : null}
    </AppShell>
  );
}

function MetricCard({
  label,
  value,
  helper
}: {
  label: string;
  value: string;
  helper: string;
}) {
  return (
    <Card className="p-5">
      <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">{label}</p>
      <p className="mt-2 text-xl font-semibold text-zinc-950">{value}</p>
      <p className="mt-2 text-xs leading-5 text-zinc-500">{helper}</p>
    </Card>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-zinc-100 pb-3 last:border-0 last:pb-0">
      <dt className="text-zinc-500">{label}</dt>
      <dd className="text-right font-medium text-zinc-900">{value}</dd>
    </div>
  );
}

function SelectField({
  label,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-zinc-800">{label}</span>
      <select
        className="mt-2 min-h-11 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
        {...props}
      >
        {children}
      </select>
    </label>
  );
}

function ProductIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-6 w-6" fill="none">
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

function formatQuantity(value: number | null, unit: string) {
  if (value === null) return "Sem dados";

  return `${new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 2
  }).format(value)} ${unit}`;
}

function formatOptionalQuantity(value: number | null, unit: string) {
  if (value === null) return "Não informado";
  return formatQuantity(value, unit);
}

function formatPrice(value: number | null) {
  if (value === null) return "Sem preço";

  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
  }).format(value);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short"
  }).format(new Date(value));
}

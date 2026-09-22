import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { useAuth } from "../../auth/context/AuthContext";
import { listActiveProducts, type ProductListItem } from "../../products/api/products";
import { createQuickSupplier, listActiveSuppliers } from "../../suppliers/api/suppliers";
import { createEntry } from "../api/entries";
import {
  buildEffectiveAt,
  formatMoney,
  getEntryErrorMessage,
  localDateInputValue,
  parseOptionalPrice,
  parsePositiveDecimal
} from "../lib/entryValidation";

type DraftItem = {
  localId: string;
  product: ProductListItem;
  quantity: string;
  unitPrice: string;
};

export function NewEntryPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();
  const [supplierId, setSupplierId] = useState("");
  const [supplierSearch, setSupplierSearch] = useState("");
  const [quickSupplierName, setQuickSupplierName] = useState("");
  const [showQuickSupplier, setShowQuickSupplier] = useState(false);
  const [date, setDate] = useState(() => localDateInputValue());
  const [observationOpen, setObservationOpen] = useState(false);
  const [observation, setObservation] = useState("");
  const [productSearch, setProductSearch] = useState("");
  const [items, setItems] = useState<DraftItem[]>([]);
  const [duplicateProduct, setDuplicateProduct] = useState<ProductListItem | null>(null);
  const [missingPriceReview, setMissingPriceReview] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [idempotencyKey] = useState(() => crypto.randomUUID());

  const suppliersQuery = useQuery({
    queryKey: ["suppliers", "active"],
    queryFn: listActiveSuppliers
  });
  const productsQuery = useQuery({
    queryKey: ["products", "active"],
    queryFn: listActiveProducts
  });

  const quickSupplierMutation = useMutation({
    mutationFn: createQuickSupplier,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["suppliers"] });
    }
  });

  const saveMutation = useMutation({ mutationFn: createEntry });

  const selectedSupplier = (suppliersQuery.data ?? []).find((supplier) => supplier.id === supplierId) ?? null;

  const supplierSuggestions = useMemo(() => {
    const term = normalize(supplierSearch);
    if (!term || selectedSupplier) return [];
    return (suppliersQuery.data ?? [])
      .filter((supplier) => normalize([supplier.name, supplier.company ?? "", supplier.phone ?? ""].join(" ")).includes(term))
      .slice(0, 8);
  }, [selectedSupplier, supplierSearch, suppliersQuery.data]);

  const productSuggestions = useMemo(() => {
    const term = normalize(productSearch);
    if (!term) return [];
    return (productsQuery.data ?? [])
      .filter((product) => normalize(product.name).includes(term))
      .sort((a, b) => a.name.localeCompare(b.name, "pt-BR"))
      .slice(0, 10);
  }, [productSearch, productsQuery.data]);

  const totals = useMemo(() => {
    let totalKnown = 0;
    let missingPrices = 0;

    for (const item of items) {
      try {
        const quantity = parsePositiveDecimal(item.quantity, "Quantidade");
        const price = parseOptionalPrice(item.unitPrice);
        if (price === null) missingPrices += 1;
        else totalKnown += quantity * price;
      } catch {
        // Campos inválidos não entram na prévia de total; serão destacados no salvamento.
      }
    }

    return { totalKnown, missingPrices };
  }, [items]);

  const addProduct = (product: ProductListItem, forceDuplicate = false) => {
    const existing = items.find((item) => item.product.id === product.id);
    if (existing && !forceDuplicate) {
      setDuplicateProduct(product);
      return;
    }

    const localId = crypto.randomUUID();
    setItems((current) => [
      ...current,
      { localId, product, quantity: "", unitPrice: "" }
    ]);
    setProductSearch("");
    setDuplicateProduct(null);
    window.setTimeout(() => document.getElementById(`entry-qty-${localId}`)?.focus(), 0);
  };

  const goToExisting = () => {
    if (!duplicateProduct) return;
    const existing = items.find((item) => item.product.id === duplicateProduct.id);
    setDuplicateProduct(null);
    if (!existing) return;
    document.getElementById(`entry-item-${existing.localId}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
    window.setTimeout(() => document.getElementById(`entry-qty-${existing.localId}`)?.focus(), 250);
  };

  const addQuickSupplier = async () => {
    const name = quickSupplierName.trim().replace(/\s+/g, " ");
    if (!name) {
      setActionError("Informe o nome do fornecedor.");
      return;
    }
    setActionError(null);

    try {
      const id = await quickSupplierMutation.mutateAsync(name);
      setSupplierId(id);
      setSupplierSearch("");
      setQuickSupplierName("");
      setShowQuickSupplier(false);
    } catch (error) {
      setActionError(getEntryErrorMessage(error));
    }
  };

  const validateAndBuild = () => {
    if (!supplierId) throw new Error("Selecione um fornecedor.");
    if (!deviceId) throw new Error("Este dispositivo ainda não está pronto para registrar Entradas.");
    if (!items.length) throw new Error("Adicione pelo menos um produto.");

    return {
      supplierId,
      effectiveAt: buildEffectiveAt(date),
      deviceId,
      idempotencyKey,
      observation: observation.trim() || null,
      items: items.map((item) => ({
        productId: item.product.id,
        quantity: parsePositiveDecimal(item.quantity, `Quantidade de ${item.product.name}`),
        unitPrice: parseOptionalPrice(item.unitPrice)
      }))
    };
  };

  const requestSave = () => {
    setActionError(null);
    try {
      const payload = validateAndBuild();
      if (payload.items.some((item) => item.unitPrice === null)) {
        setMissingPriceReview(true);
        return;
      }
      void persist(payload);
    } catch (error) {
      setActionError(getEntryErrorMessage(error));
    }
  };

  const persist = async (payload = validateAndBuild()) => {
    setActionError(null);
    try {
      const entryId = await saveMutation.mutateAsync(payload);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["entries"] }),
        queryClient.invalidateQueries({ queryKey: ["products", "active"] }),
        queryClient.invalidateQueries({ queryKey: ["suppliers"] })
      ]);
      navigate(`/entradas/${entryId}`, { replace: true });
    } catch (error) {
      setMissingPriceReview(false);
      setActionError(getEntryErrorMessage(error));
    }
  };

  return (
    <AppShell title="Nova Entrada" showBack backTo="/entradas">
      <section className="pb-28">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">Mercadoria recebida</h2>
          <p className="mt-1 text-sm text-zinc-600">Registre somente mercadoria que realmente chegou à Panificadora.</p>
        </div>

        <Card className="mt-5 p-5">
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="block text-sm font-medium text-zinc-800">Fornecedor *</label>
              {selectedSupplier ? (
                <div className="mt-2 flex min-h-11 items-center justify-between gap-3 rounded-xl border border-zinc-300 bg-white px-3 py-2">
                  <div>
                    <p className="text-sm font-semibold">{selectedSupplier.name}</p>
                    <p className="text-xs text-zinc-500">{selectedSupplier.company ?? "Cadastro pendente"}</p>
                  </div>
                  <button type="button" className="text-xs font-semibold text-red-700" onClick={() => { setSupplierId(""); setSupplierSearch(""); }}>
                    Trocar
                  </button>
                </div>
              ) : (
                <>
                  <input
                    value={supplierSearch}
                    onChange={(event) => setSupplierSearch(event.target.value)}
                    placeholder="Buscar contato, empresa ou telefone"
                    className="mt-2 min-h-11 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                  />
                  {supplierSearch ? (
                    <div className="mt-2 overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
                      {supplierSuggestions.map((supplier) => (
                        <button
                          key={supplier.id}
                          type="button"
                          className="block w-full border-b border-zinc-100 px-4 py-3 text-left text-sm last:border-0 hover:bg-zinc-50"
                          onClick={() => { setSupplierId(supplier.id); setSupplierSearch(""); }}
                        >
                          <span className="font-semibold">{supplier.name}</span>
                          <span className="ml-2 text-zinc-500">{supplier.company ?? "Pendente"}</span>
                        </button>
                      ))}
                      <button
                        type="button"
                        className="block w-full px-4 py-3 text-left text-sm font-semibold text-red-700 hover:bg-red-50"
                        onClick={() => {
                          setQuickSupplierName(supplierSearch.trim());
                          setShowQuickSupplier(true);
                        }}
                      >
                        + Cadastrar novo fornecedor
                      </button>
                    </div>
                  ) : null}
                </>
              )}
            </div>

            <TextField label="Data *" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
          </div>

          <div className="mt-4">
            {!observationOpen ? (
              <Button variant="ghost" size="sm" onClick={() => setObservationOpen(true)}>+ Adicionar observação</Button>
            ) : (
              <label className="block">
                <span className="text-sm font-medium text-zinc-800">Observação</span>
                <textarea
                  rows={3}
                  maxLength={2000}
                  value={observation}
                  onChange={(event) => setObservation(event.target.value)}
                  className="mt-2 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                  placeholder="Observação única para esta Entrada."
                />
              </label>
            )}
          </div>
        </Card>

        <div className="mt-6">
          <h3 className="font-semibold">Produtos recebidos</h3>
          <div className="mt-3 max-w-2xl">
            <TextField label="Adicionar produto" placeholder="Buscar por nome" value={productSearch} onChange={(event) => setProductSearch(event.target.value)} />
            {productSearch ? (
              <div className="mt-2 overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
                {productSuggestions.map((product) => (
                  <button key={product.id} type="button" className="flex w-full items-center justify-between gap-3 border-b border-zinc-100 px-4 py-3 text-left text-sm last:border-0 hover:bg-zinc-50" onClick={() => addProduct(product)}>
                    <span className="font-semibold">{product.name}</span>
                    <span className="text-xs text-zinc-500">{product.unit}</span>
                  </button>
                ))}
                {productSuggestions.length === 0 ? (
                  <div className="px-4 py-3 text-sm text-zinc-500">
                    Nenhum produto encontrado. O cadastro rápido de produto será implementado em uma próxima leva.
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>
        </div>

        <div className="mt-4 space-y-3">
          {items.map((item, index) => {
            const quantity = safeQuantity(item.quantity);
            const price = safePrice(item.unitPrice);
            const lineTotal = quantity !== null && price !== null ? quantity * price : null;

            return (
              <Card id={`entry-item-${item.localId}`} key={item.localId} className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">Item {index + 1}</p>
                    <h4 className="mt-1 font-semibold">{item.product.name}</h4>
                    <p className="mt-1 text-xs text-zinc-500">Unidade: {item.product.unit}</p>
                  </div>
                  <button type="button" className="text-xs font-semibold text-red-700" onClick={() => setItems((current) => current.filter((candidate) => candidate.localId !== item.localId))}>
                    Remover
                  </button>
                </div>

                <div className="mt-4 grid gap-3 md:grid-cols-[1fr_1fr_160px]">
                  <TextField
                    id={`entry-qty-${item.localId}`}
                    label="Quantidade *"
                    inputMode="decimal"
                    value={item.quantity}
                    onChange={(event) => setItems((current) => current.map((candidate) => candidate.localId === item.localId ? { ...candidate, quantity: event.target.value } : candidate))}
                  />
                  <TextField
                    label="Preço unitário"
                    inputMode="decimal"
                    placeholder="Vazio = não informado"
                    value={item.unitPrice}
                    onChange={(event) => setItems((current) => current.map((candidate) => candidate.localId === item.localId ? { ...candidate, unitPrice: event.target.value } : candidate))}
                  />
                  <div className="rounded-xl bg-zinc-50 px-4 py-3">
                    <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">Total</p>
                    <p className="mt-2 font-semibold">
                      {price === 0 && quantity !== null ? "Bonificação" : lineTotal === null ? "—" : formatMoney(lineTotal)}
                    </p>
                  </div>
                </div>
              </Card>
            );
          })}
        </div>

        {items.length === 0 ? (
          <Card className="mt-4 p-6 text-center text-sm text-zinc-500">Adicione ao menos um produto recebido.</Card>
        ) : null}

        {actionError ? <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{actionError}</div> : null}

        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-zinc-200 bg-white/95 px-4 py-3 shadow-[0_-8px_24px_rgba(0,0,0,0.08)] backdrop-blur">
          <div className="mx-auto flex max-w-6xl items-center justify-between gap-4">
            <div>
              <p className="text-xs text-zinc-500">Total conhecido</p>
              <p className="font-semibold">{formatMoney(totals.totalKnown)}{totals.missingPrices ? " *" : ""}</p>
              {totals.missingPrices ? <p className="text-xs text-amber-700">{totals.missingPrices} item(ns) sem preço</p> : null}
            </div>
            <Button disabled={saveMutation.isPending} onClick={requestSave}>
              {saveMutation.isPending ? "Salvando…" : "Salvar Entrada"}
            </Button>
          </div>
        </div>

        <ConfirmDialog
          open={missingPriceReview}
          variant="warning"
          title="Salvar com preço não informado?"
          description={`Há ${totals.missingPrices} item(ns) sem preço. O total da Entrada ficará parcial e somará apenas os itens com preço conhecido. Deseja salvar mesmo assim?`}
          confirmLabel="Salvar mesmo assim"
          pendingLabel="Salvando…"
          isPending={saveMutation.isPending}
          onCancel={() => setMissingPriceReview(false)}
          onConfirm={() => void persist()}
        />

        {showQuickSupplier ? (
          <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6">
            <Card className="w-full max-w-md p-5 shadow-xl">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Cadastro rápido</p>
              <h3 className="mt-1 text-xl font-semibold">Novo fornecedor</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Informe somente o nome. Empresa e telefone ficarão pendentes para completar depois em Fornecedores.
              </p>
              <div className="mt-4">
                <TextField label="Nome *" autoFocus value={quickSupplierName} onChange={(event) => setQuickSupplierName(event.target.value)} />
              </div>
              <div className="mt-5 flex justify-end gap-2">
                <Button variant="ghost" disabled={quickSupplierMutation.isPending} onClick={() => setShowQuickSupplier(false)}>Cancelar</Button>
                <Button disabled={quickSupplierMutation.isPending} onClick={() => void addQuickSupplier()}>
                  {quickSupplierMutation.isPending ? "Salvando…" : "Cadastrar e selecionar"}
                </Button>
              </div>
            </Card>
          </div>
        ) : null}

        {duplicateProduct ? (
          <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6">
            <Card className="w-full max-w-lg p-5 shadow-xl">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-amber-700">Produto já adicionado</p>
              <h3 className="mt-1 text-xl font-semibold">Adicionar novamente?</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                “{duplicateProduct.name}” já está nesta Entrada. Você pode ir para o item existente ou adicionar uma nova linha.
              </p>
              <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <Button variant="ghost" onClick={() => setDuplicateProduct(null)}>Cancelar</Button>
                <Button variant="secondary" onClick={goToExisting}>Ir para item existente</Button>
                <Button onClick={() => addProduct(duplicateProduct, true)}>Adicionar novamente</Button>
              </div>
            </Card>
          </div>
        ) : null}
      </section>
    </AppShell>
  );
}

function safeQuantity(value: string) {
  try {
    return parsePositiveDecimal(value, "Quantidade");
  } catch {
    return null;
  }
}

function safePrice(value: string) {
  try {
    return parseOptionalPrice(value);
  } catch {
    return null;
  }
}

function normalize(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR").trim();
}

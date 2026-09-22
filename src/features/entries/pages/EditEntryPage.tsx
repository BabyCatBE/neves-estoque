import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type KeyboardEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { useAuth } from "../../auth/context/AuthContext";
import { listActiveProducts, type ProductListItem } from "../../products/api/products";
import { listActiveSuppliers } from "../../suppliers/api/suppliers";
import { getEntryDetails, updateEntry, type EntryDetails } from "../api/entries";
import { useControlKeyPressed } from "../lib/useControlKeyPressed";
import {
  buildEffectiveAt,
  formatMoney,
  getEntryErrorMessage,
  parseOptionalPrice,
  parsePositiveDecimal
} from "../lib/entryValidation";

type DraftItem = {
  localId: string;
  productId: string;
  productName: string;
  unit: string;
  quantity: string;
  unitPrice: string;
};

type ItemFieldErrors = Record<string, {
  quantity?: string;
  unitPrice?: string;
}>;

type EditDraft = {
  supplierId: string;
  date: string;
  observation: string;
  items: DraftItem[];
};

export function EditEntryPage() {
  const { entryId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();
  const controlKeyPressed = useControlKeyPressed();
  const [draft, setDraft] = useState<EditDraft | null>(null);
  const [itemErrors, setItemErrors] = useState<ItemFieldErrors>({});
  const [productSearch, setProductSearch] = useState("");
  const [productActiveIndex, setProductActiveIndex] = useState(0);
  const [duplicateProduct, setDuplicateProduct] = useState<ProductListItem | null>(null);
  const [missingPriceReview, setMissingPriceReview] = useState(false);
  const [saveReviewOpen, setSaveReviewOpen] = useState(false);
  const [leaveReviewOpen, setLeaveReviewOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const entryQuery = useQuery({
    queryKey: ["entries", "detail", entryId],
    queryFn: () => {
      if (!entryId) throw new Error("Entrada inválida.");
      return getEntryDetails(entryId);
    },
    enabled: Boolean(entryId)
  });
  const suppliersQuery = useQuery({ queryKey: ["suppliers", "active"], queryFn: listActiveSuppliers });
  const productsQuery = useQuery({ queryKey: ["products", "active"], queryFn: listActiveProducts });
  const updateMutation = useMutation({ mutationFn: updateEntry });

  useEffect(() => {
    if (entryQuery.data && !draft) setDraft(toDraft(entryQuery.data));
  }, [draft, entryQuery.data]);

  const original = entryQuery.data ? toDraft(entryQuery.data) : null;
  const dirty = Boolean(draft && original && JSON.stringify(snapshot(draft)) !== JSON.stringify(snapshot(original)));

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
    for (const item of draft?.items ?? []) {
      try {
        const qty = parsePositiveDecimal(item.quantity, "Quantidade");
        const price = parseOptionalPrice(item.unitPrice);
        if (price === null) missingPrices += 1;
        else totalKnown += qty * price;
      } catch {
        // validação completa ocorre ao salvar
      }
    }
    return { totalKnown, missingPrices };
  }, [draft]);

  const changes = useMemo(() => buildChangeSummary(entryQuery.data, draft), [draft, entryQuery.data]);

  const addProduct = (product: ProductListItem, forceDuplicate = false) => {
    if (!draft) return;
    const existing = draft.items.find((item) => item.productId === product.id);
    if (existing && !forceDuplicate) {
      setDuplicateProduct(product);
      return;
    }
    const localId = crypto.randomUUID();
    setDraft({
      ...draft,
      items: [...draft.items, {
        localId,
        productId: product.id,
        productName: product.name,
        unit: product.unit,
        quantity: "",
        unitPrice: ""
      }]
    });
    setProductSearch("");
    setProductActiveIndex(0);
    setDuplicateProduct(null);
    window.setTimeout(() => document.getElementById(`edit-entry-qty-${localId}`)?.focus(), 0);
  };

  const handleProductSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.ctrlKey || event.metaKey || controlKeyPressed.current) return;
    if (!productSearch || productSuggestions.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setProductActiveIndex((index) => (index + 1) % productSuggestions.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setProductActiveIndex((index) => (index - 1 + productSuggestions.length) % productSuggestions.length);
    } else if (event.key === "Enter") {
      event.preventDefault();
      const selected = productSuggestions[Math.min(productActiveIndex, productSuggestions.length - 1)];
      if (selected) addProduct(selected);
    } else if (event.key === "Escape") {
      setProductSearch("");
      setProductActiveIndex(0);
    }
  };

  const setItemError = (localId: string, field: "quantity" | "unitPrice", message?: string) => {
    setItemErrors((current) => {
      const nextForItem = { ...current[localId], [field]: message };
      if (!nextForItem.quantity && !nextForItem.unitPrice) {
        const next = { ...current };
        delete next[localId];
        return next;
      }
      return { ...current, [localId]: nextForItem };
    });
  };

  const validateQuantityNow = (item: DraftItem) => {
    try {
      parsePositiveDecimal(item.quantity, "Quantidade");
      setItemError(item.localId, "quantity");
      return true;
    } catch (error) {
      setItemError(item.localId, "quantity", validationMessage(error, "Quantidade inválida."));
      return false;
    }
  };

  const validatePriceNow = (item: DraftItem) => {
    try {
      parseOptionalPrice(item.unitPrice);
      setItemError(item.localId, "unitPrice");
      return true;
    } catch (error) {
      setItemError(item.localId, "unitPrice", validationMessage(error, "Preço unitário inválido."));
      return false;
    }
  };

  const buildPayload = () => {
    if (!draft || !entryQuery.data || !entryId) throw new Error("Entrada inválida.");
    if (!deviceId) throw new Error("Este dispositivo ainda não está pronto para editar Entradas.");
    if (!draft.supplierId) throw new Error("Selecione um fornecedor.");
    if (!draft.items.length) throw new Error("A Entrada precisa de pelo menos um item.");

    return {
      entryId,
      supplierId: draft.supplierId,
      effectiveAt: buildEffectiveAt(draft.date, new Date(entryQuery.data.effectiveAt)),
      deviceId,
      observation: draft.observation.trim() || null,
      items: draft.items.map((item) => ({
        productId: item.productId,
        quantity: parsePositiveDecimal(item.quantity, `Quantidade de ${item.productName}`),
        unitPrice: parseOptionalPrice(item.unitPrice)
      }))
    };
  };

  const requestSave = () => {
    setActionError(null);
    try {
      const payload = buildPayload();
      if (payload.items.some((item) => item.unitPrice === null)) {
        setMissingPriceReview(true);
        return;
      }
      setSaveReviewOpen(true);
    } catch (error) {
      setActionError(getEntryErrorMessage(error));
    }
  };

  const persist = async () => {
    try {
      const payload = buildPayload();
      await updateMutation.mutateAsync(payload);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["entries"] }),
        queryClient.invalidateQueries({ queryKey: ["products", "active"] })
      ]);
      setSaveReviewOpen(false);
      setMissingPriceReview(false);
      navigate(`/entradas/${entryId}`, { replace: true });
    } catch (error) {
      setSaveReviewOpen(false);
      setMissingPriceReview(false);
      setActionError(getEntryErrorMessage(error));
    }
  };

  const requestBack = () => {
    if (dirty) setLeaveReviewOpen(true);
    else navigate(`/entradas/${entryId}`);
  };

  if (entryQuery.isPending || !draft) {
    return <AppShell title="Editar Entrada" showBack onBack={requestBack}><Card className="p-5 text-sm text-zinc-600">Carregando Entrada…</Card></AppShell>;
  }

  if (entryQuery.isError) {
    return <AppShell title="Editar Entrada" showBack backTo="/entradas/historico"><Card className="border-red-200 p-5 text-sm text-red-800">Não foi possível carregar esta Entrada.</Card></AppShell>;
  }

  return (
    <AppShell title="Editar Entrada" showBack onBack={requestBack}>
      <section
        className="pb-28"
        onKeyDown={(event) => {
          if (
            (event.ctrlKey || event.metaKey || controlKeyPressed.current) &&
            event.key === "Enter" &&
            dirty &&
            !missingPriceReview &&
            !saveReviewOpen &&
            !leaveReviewOpen &&
            !duplicateProduct &&
            !updateMutation.isPending
          ) {
            event.preventDefault();
            requestSave();
          }
        }}
      >
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Edição histórica</p>
          <h2 className="mt-1 text-2xl font-semibold">Editar Entrada</h2>
          <p className="mt-2 text-sm leading-6 text-zinc-600">Ao salvar, estoque e preços derivados serão recalculados automaticamente.</p>
        </div>

        <Card className="mt-5 p-5">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="block">
              <span className="text-sm font-medium text-zinc-800">Fornecedor *</span>
              <select
                value={draft.supplierId}
                onChange={(event) => setDraft({ ...draft, supplierId: event.target.value })}
                className="mt-2 min-h-11 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none focus:border-red-500 focus:ring-2 focus:ring-red-100"
              >
                {(suppliersQuery.data ?? []).map((supplier) => (
                  <option key={supplier.id} value={supplier.id}>{supplier.name}{supplier.company ? ` — ${supplier.company}` : " — pendente"}</option>
                ))}
              </select>
            </label>
            <TextField label="Data *" type="date" value={draft.date} onChange={(event) => setDraft({ ...draft, date: event.target.value })} />
          </div>
          <label className="mt-4 block">
            <span className="text-sm font-medium text-zinc-800">Observação</span>
            <textarea
              rows={3}
              maxLength={2000}
              value={draft.observation}
              onChange={(event) => setDraft({ ...draft, observation: event.target.value })}
              className="mt-2 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-red-500 focus:ring-2 focus:ring-red-100"
            />
          </label>
        </Card>

        <div className="mt-6 max-w-2xl">
          <TextField
            id="edit-entry-product-search"
            label="Adicionar produto"
            placeholder="Buscar por nome"
            value={productSearch}
            onChange={(event) => { setProductSearch(event.target.value); setProductActiveIndex(0); }}
            onKeyDown={handleProductSearchKeyDown}
          />
          {productSearch ? (
            <div className="mt-2 overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
              {productSuggestions.map((product, index) => (
                <button
                  key={product.id}
                  type="button"
                  className={`flex w-full items-center justify-between border-b border-zinc-100 px-4 py-3 text-left text-sm last:border-0 ${index === productActiveIndex ? "bg-red-50" : "hover:bg-zinc-50"}`}
                  onMouseEnter={() => setProductActiveIndex(index)}
                  onClick={() => addProduct(product)}
                >
                  <span className="font-semibold">{product.name}</span><span className="text-xs text-zinc-500">{product.unit}</span>
                </button>
              ))}
            </div>
          ) : null}
        </div>

        <div className="mt-4 space-y-3">
          {draft.items.map((item, index) => {
            const qty = safeQuantity(item.quantity);
            const price = safePrice(item.unitPrice);
            return (
              <Card key={item.localId} id={`edit-entry-item-${item.localId}`} className="p-4">
                <div className="flex justify-between gap-3">
                  <div><p className="text-xs font-semibold uppercase text-zinc-400">Item {index + 1}</p><h3 className="mt-1 font-semibold">{item.productName}</h3><p className="mt-1 text-xs text-zinc-500">Unidade: {item.unit}</p></div>
                  <button
                    type="button"
                    className="text-xs font-semibold text-red-700"
                    onClick={() => {
                      setDraft({ ...draft, items: draft.items.filter((candidate) => candidate.localId !== item.localId) });
                      setItemErrors((current) => {
                        const next = { ...current };
                        delete next[item.localId];
                        return next;
                      });
                    }}
                  >
                    Remover
                  </button>
                </div>
                <div className="mt-4 grid gap-3 md:grid-cols-[1fr_1fr_160px]">
                  <TextField
                    id={`edit-entry-qty-${item.localId}`}
                    label="Quantidade *"
                    inputMode="decimal"
                    value={item.quantity}
                    error={itemErrors[item.localId]?.quantity}
                    aria-invalid={Boolean(itemErrors[item.localId]?.quantity)}
                    onChange={(event) => {
                      const value = event.target.value;
                      setDraft({ ...draft, items: draft.items.map((candidate) => candidate.localId === item.localId ? { ...candidate, quantity: value } : candidate) });
                      if (itemErrors[item.localId]?.quantity) {
                        try {
                          parsePositiveDecimal(value, "Quantidade");
                          setItemError(item.localId, "quantity");
                        } catch {
                          // Mantém o erro visível até o valor se tornar válido.
                        }
                      }
                    }}
                    onKeyDown={(event) => {
                      if (event.ctrlKey || event.metaKey || controlKeyPressed.current) return;
                      if (event.key === "Enter") {
                        event.preventDefault();
                        if (validateQuantityNow(item)) {
                          document.getElementById(`edit-entry-price-${item.localId}`)?.focus();
                        }
                      }
                    }}
                  />
                  <TextField
                    id={`edit-entry-price-${item.localId}`}
                    label="Preço unitário"
                    inputMode="decimal"
                    placeholder="Vazio = não informado"
                    value={item.unitPrice}
                    error={itemErrors[item.localId]?.unitPrice}
                    aria-invalid={Boolean(itemErrors[item.localId]?.unitPrice)}
                    onChange={(event) => {
                      const value = event.target.value;
                      setDraft({ ...draft, items: draft.items.map((candidate) => candidate.localId === item.localId ? { ...candidate, unitPrice: value } : candidate) });
                      if (itemErrors[item.localId]?.unitPrice) {
                        try {
                          parseOptionalPrice(value);
                          setItemError(item.localId, "unitPrice");
                        } catch {
                          // Mantém o erro visível até o valor se tornar válido.
                        }
                      }
                    }}
                    onKeyDown={(event) => {
                      if (event.ctrlKey || event.metaKey || controlKeyPressed.current) return;
                      if (event.key === "Enter") {
                        event.preventDefault();
                        if (validatePriceNow(item)) {
                          document.getElementById("edit-entry-product-search")?.focus();
                        }
                      }
                    }}
                  />
                  <div className="rounded-xl bg-zinc-50 px-4 py-3"><p className="text-xs font-semibold uppercase text-zinc-400">Total</p><p className="mt-2 font-semibold">{qty !== null && price !== null ? (price === 0 ? "Bonificação" : formatMoney(qty * price)) : "—"}</p></div>
                </div>
              </Card>
            );
          })}
        </div>

        {actionError ? <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{actionError}</div> : null}

        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-zinc-200 bg-white/95 px-4 py-3 shadow-[0_-8px_24px_rgba(0,0,0,0.08)] backdrop-blur">
          <div className="mx-auto flex max-w-6xl items-center justify-between gap-4">
            <div><p className="text-xs text-zinc-500">Total conhecido</p><p className="font-semibold">{formatMoney(totals.totalKnown)}{totals.missingPrices ? " *" : ""}</p></div>
            <div className="flex items-center gap-3">
              <span className="hidden text-xs text-zinc-400 sm:inline">Atalho: Ctrl + Enter</span>
              <Button disabled={!dirty || updateMutation.isPending} onClick={requestSave}>{updateMutation.isPending ? "Salvando…" : "Salvar alterações"}</Button>
            </div>
          </div>
        </div>

        <ConfirmDialog
          open={missingPriceReview}
          variant="warning"
          title="Continuar com preço não informado?"
          description={`Há ${totals.missingPrices} item(ns) sem preço. O total continuará parcial. Deseja revisar as alterações mesmo assim?`}
          confirmLabel="Continuar"
          onCancel={() => setMissingPriceReview(false)}
          onConfirm={() => { setMissingPriceReview(false); setSaveReviewOpen(true); }}
        />

        <ConfirmDialog
          open={saveReviewOpen}
          variant="warning"
          title="Salvar edição histórica?"
          description={
            <div>
              <p>Esta alteração recalculará estoque, preços e dependências posteriores.</p>
              <ul className="mt-3 space-y-1 text-sm">
                {changes.map((change) => <li key={change}>• {change}</li>)}
              </ul>
            </div>
          }
          confirmLabel="Salvar alterações"
          pendingLabel="Salvando…"
          isPending={updateMutation.isPending}
          onCancel={() => setSaveReviewOpen(false)}
          onConfirm={() => void persist()}
        />

        <ConfirmDialog
          open={leaveReviewOpen}
          variant="warning"
          title="Descartar alterações?"
          description="Existem alterações não salvas nesta Entrada."
          confirmLabel="Descartar e sair"
          onCancel={() => setLeaveReviewOpen(false)}
          onConfirm={() => navigate(`/entradas/${entryId}`)}
        />

        {duplicateProduct ? (
          <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6">
            <Card className="w-full max-w-lg p-5 shadow-xl">
              <h3 className="text-xl font-semibold">Produto já adicionado</h3>
              <p className="mt-2 text-sm text-zinc-600">“{duplicateProduct.name}” já está nesta Entrada.</p>
              <div className="mt-5 flex justify-end gap-2">
                <Button variant="ghost" onClick={() => setDuplicateProduct(null)}>Cancelar</Button>
                <Button variant="secondary" onClick={() => {
                  const existing = draft.items.find((item) => item.productId === duplicateProduct.id);
                  setDuplicateProduct(null);
                  if (existing) document.getElementById(`edit-entry-item-${existing.localId}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
                }}>Ir para item existente</Button>
                <Button onClick={() => addProduct(duplicateProduct, true)}>Adicionar novamente</Button>
              </div>
            </Card>
          </div>
        ) : null}
      </section>
    </AppShell>
  );
}

function toDraft(entry: EntryDetails): EditDraft {
  return {
    supplierId: entry.supplierId,
    date: localDateFromIso(entry.effectiveAt),
    observation: entry.observation ?? "",
    items: entry.items.map((item) => ({
      localId: item.id,
      productId: item.productId,
      productName: item.productName,
      unit: item.unit,
      quantity: String(item.quantity).replace(".", ","),
      unitPrice: item.unitPrice === null ? "" : String(item.unitPrice).replace(".", ",")
    }))
  };
}

function snapshot(draft: EditDraft) {
  return {
    supplierId: draft.supplierId,
    date: draft.date,
    observation: draft.observation.trim(),
    items: draft.items.map((item) => ({
      productId: item.productId,
      quantity: item.quantity.trim(),
      unitPrice: item.unitPrice.trim()
    }))
  };
}

function buildChangeSummary(entry: EntryDetails | undefined, draft: EditDraft | null) {
  if (!entry || !draft) return [];
  const changes: string[] = [];
  if (entry.supplierId !== draft.supplierId) changes.push("Fornecedor alterado.");
  if (localDateFromIso(entry.effectiveAt) !== draft.date) changes.push("Data alterada.");
  if ((entry.observation ?? "").trim() !== draft.observation.trim()) changes.push("Observação alterada.");
  const originalItems = entry.items.map((item) => `${item.productId}|${item.quantity}|${item.unitPrice ?? ""}`);
  const draftItems = draft.items.map((item) => `${item.productId}|${item.quantity.replace(",", ".")}|${item.unitPrice.replace(",", ".")}`);
  if (JSON.stringify(originalItems) !== JSON.stringify(draftItems)) changes.push(`Itens recebidos alterados (${entry.items.length} → ${draft.items.length} linhas).`);
  return changes.length ? changes : ["Nenhuma alteração detectada."];
}

function localDateFromIso(value: string) {
  const date = new Date(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function safeQuantity(value: string) {
  try { return parsePositiveDecimal(value, "Quantidade"); } catch { return null; }
}

function safePrice(value: string) {
  try { return parseOptionalPrice(value); } catch { return null; }
}

function validationMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function normalize(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR").trim();
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useRef, useState, type KeyboardEvent, type SelectHTMLAttributes } from "react";
import { useBlocker, useNavigate } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { useCtrlEnter } from "../../../shared/hooks/useCtrlEnter";
import { handleDialogButtonArrowNavigation } from "../../../shared/lib/dialogKeyboard";
import { createBrowserUuid } from "../../../shared/lib/browserUuid";
import { normalizeSearchText } from "../../../shared/lib/searchText";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { useAuth } from "../../auth/context/AuthContext";
import {
  listActiveProducts,
  listProductCategories,
  updateProductDetails,
  type ProductListItem
} from "../../products/api/products";
import {
  getProductErrorMessage,
  PRODUCT_UNITS,
  productNameSchema,
  type ProductUnit
} from "../../products/lib/productValidation";
import { listActiveSuppliers } from "../../suppliers/api/suppliers";
import { createEntry } from "../api/entries";
import { useControlKeyPressed } from "../lib/useControlKeyPressed";
import {
  buildEffectiveAt,
  formatMoney,
  getEntryErrorMessage,
  getFutureOperationalDateError,
  localDateInputValue,
  parseOptionalPrice,
  parsePositiveDecimal
} from "../lib/entryValidation";

type DraftSupplier = {
  name: string;
};

type DraftProduct = {
  clientId: string;
  name: string;
  unit: ProductUnit;
  categoryId: string | null;
};

type DraftItem = {
  localId: string;
  product: ProductListItem;
  draftProduct?: DraftProduct;
  quantity: string;
  unitPrice: string;
};

type ItemFieldErrors = Record<string, {
  quantity?: string;
  unitPrice?: string;
}>;

export function NewEntryPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();
  const controlKeyPressed = useControlKeyPressed();
  const [supplierId, setSupplierId] = useState("");
  const [supplierSearch, setSupplierSearch] = useState("");
  const [supplierActiveIndex, setSupplierActiveIndex] = useState(0);
  const [quickSupplierName, setQuickSupplierName] = useState("");
  const [quickSupplierError, setQuickSupplierError] = useState<string | null>(null);
  const [showQuickSupplier, setShowQuickSupplier] = useState(false);
  const [draftSupplier, setDraftSupplier] = useState<DraftSupplier | null>(null);
  const [initialDate] = useState(() => localDateInputValue());
  const [date, setDate] = useState(initialDate);
  const [dateError, setDateError] = useState<string | null>(null);
  const [observationOpen, setObservationOpen] = useState(false);
  const [observation, setObservation] = useState("");
  const [productSearch, setProductSearch] = useState("");
  const [productActiveIndex, setProductActiveIndex] = useState(0);
  const [quickProductName, setQuickProductName] = useState("");
  const [quickProductUnit, setQuickProductUnit] = useState<ProductUnit>("UN");
  const [quickProductCategoryId, setQuickProductCategoryId] = useState("");
  const [quickProductError, setQuickProductError] = useState<string | null>(null);
  const [showQuickProduct, setShowQuickProduct] = useState(false);
  const [pendingProduct, setPendingProduct] = useState<ProductListItem | null>(null);
  const [pendingProductCategoryId, setPendingProductCategoryId] = useState("");
  const [pendingProductError, setPendingProductError] = useState<string | null>(null);
  const [items, setItems] = useState<DraftItem[]>([]);
  const [itemErrors, setItemErrors] = useState<ItemFieldErrors>({});
  const [duplicateProduct, setDuplicateProduct] = useState<ProductListItem | null>(null);
  const [missingPriceReview, setMissingPriceReview] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [idempotencyKey] = useState(() => createBrowserUuid());
  const allowNavigationRef = useRef(false);

  const dirty = useMemo(
    () =>
      Boolean(
        supplierId ||
        draftSupplier ||
        supplierSearch.trim() ||
        date !== initialDate ||
        observation.trim() ||
        productSearch.trim() ||
        items.length
      ),
    [date, draftSupplier, initialDate, items.length, observation, productSearch, supplierId, supplierSearch]
  );

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty &&
      !allowNavigationRef.current &&
      currentLocation.pathname !== nextLocation.pathname
  );

  const suppliersQuery = useQuery({
    queryKey: ["suppliers", "active"],
    queryFn: listActiveSuppliers
  });
  const productsQuery = useQuery({
    queryKey: ["products", "active"],
    queryFn: listActiveProducts
  });
  const productCategoriesQuery = useQuery({
    queryKey: ["products", "categories"],
    queryFn: listProductCategories
  });

  const completePendingProductMutation = useMutation({
    mutationFn: updateProductDetails,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["products"] });
    }
  });

  const saveMutation = useMutation({ mutationFn: createEntry });

  const selectedSupplier = draftSupplier
    ? { id: "draft-supplier", name: draftSupplier.name, company: null }
    : (suppliersQuery.data ?? []).find((supplier) => supplier.id === supplierId) ?? null;

  const supplierSuggestions = useMemo(() => {
    const term = normalizeSearchText(supplierSearch);
    if (!term || selectedSupplier) return [];
    return (suppliersQuery.data ?? [])
      .filter((supplier) => normalizeSearchText([supplier.name, supplier.company ?? "", supplier.phone ?? ""].join(" ")).includes(term))
      .slice(0, 8);
  }, [selectedSupplier, supplierSearch, suppliersQuery.data]);

  const focusDate = () => {
    window.setTimeout(() => document.getElementById("entry-date")?.focus(), 0);
  };

  const selectSupplier = (id: string) => {
    setDraftSupplier(null);
    setSupplierId(id);
    setSupplierSearch("");
    setSupplierActiveIndex(0);
    focusDate();
  };

  const openQuickSupplier = () => {
    setQuickSupplierName(supplierSearch.trim());
    setQuickSupplierError(null);
    setShowQuickSupplier(true);
  };

  const handleSupplierSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.ctrlKey || event.metaKey || controlKeyPressed.current) return;
    if (!supplierSearch) return;

    const optionCount = supplierSuggestions.length + 1;

    if (event.key === "ArrowDown") {
      event.preventDefault();
      setSupplierActiveIndex((index) => (index + 1) % optionCount);
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      setSupplierActiveIndex((index) => (index - 1 + optionCount) % optionCount);
      return;
    }

    if (event.key === "Enter") {
      event.preventDefault();
      const selected = supplierSuggestions[supplierActiveIndex];
      if (selected) selectSupplier(selected.id);
      else openQuickSupplier();
      return;
    }

    if (event.key === "Escape") {
      setSupplierSearch("");
      setSupplierActiveIndex(0);
    }
  };

  const productSuggestions = useMemo(() => {
    const term = normalizeSearchText(productSearch);
    if (!term) return [];

    const localDraftProducts = Array.from(
      new Map(
        items
          .filter((item) => item.draftProduct)
          .map((item) => [item.product.id, item.product] as const)
      ).values()
    );

    return [...(productsQuery.data ?? []), ...localDraftProducts]
      .filter((product) => normalizeSearchText(product.name).includes(term))
      .sort((a, b) => a.name.localeCompare(b.name, "pt-BR"))
      .slice(0, 10);
  }, [items, productSearch, productsQuery.data]);

  const openQuickProduct = () => {
    setQuickProductName(productSearch.trim());
    setQuickProductUnit("UN");
    setQuickProductCategoryId("");
    setQuickProductError(null);
    setShowQuickProduct(true);
  };

  const chooseProduct = (product: ProductListItem) => {
    const localDraft = items.find((item) => item.product.id === product.id)?.draftProduct;
    if (localDraft) {
      addProduct(product, false, localDraft);
      return;
    }

    if (product.categoryId === null) {
      setPendingProduct(product);
      setPendingProductCategoryId("");
      setPendingProductError(null);
      return;
    }

    addProduct(product);
  };

  const handleProductSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.ctrlKey || event.metaKey || controlKeyPressed.current) return;
    if (!productSearch) return;

    const optionCount = productSuggestions.length + 1;

    if (event.key === "ArrowDown") {
      event.preventDefault();
      setProductActiveIndex((index) => (index + 1) % optionCount);
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      setProductActiveIndex((index) => (index - 1 + optionCount) % optionCount);
      return;
    }

    if (event.key === "Enter") {
      event.preventDefault();
      const selected = productSuggestions[productActiveIndex];
      if (selected) chooseProduct(selected);
      else openQuickProduct();
      return;
    }

    if (event.key === "Escape") {
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

  const addProduct = (
    product: ProductListItem,
    forceDuplicate = false,
    draftProduct?: DraftProduct
  ) => {
    const existing = items.find((item) => item.product.id === product.id);
    if (existing && !forceDuplicate) {
      setDuplicateProduct(product);
      return;
    }

    const localId = createBrowserUuid();
    const resolvedDraftProduct =
      draftProduct ?? items.find((item) => item.product.id === product.id)?.draftProduct;
    setItems((current) => [
      ...current,
      { localId, product, draftProduct: resolvedDraftProduct, quantity: "", unitPrice: "" }
    ]);
    setProductSearch("");
    setProductActiveIndex(0);
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

  const addQuickSupplier = () => {
    const name = quickSupplierName.trim().replace(/\s+/g, " ");
    if (!name) {
      setQuickSupplierError("Informe o nome do fornecedor.");
      window.setTimeout(() => document.getElementById("quick-supplier-name")?.focus(), 0);
      return;
    }

    setQuickSupplierError(null);
    setActionError(null);
    setDraftSupplier({ name });
    setSupplierId("");
    setSupplierSearch("");
    setSupplierActiveIndex(0);
    setQuickSupplierName("");
    setShowQuickSupplier(false);
    focusDate();
  };

  const addQuickProduct = () => {
    setQuickProductError(null);

    const parsedName = productNameSchema.safeParse(quickProductName);
    if (!parsedName.success) {
      setQuickProductError(parsedName.error.issues[0]?.message ?? "Informe o nome do produto.");
      window.setTimeout(() => document.getElementById("quick-product-name")?.focus(), 0);
      return;
    }

    const categoryId = quickProductCategoryId || null;
    const clientId = createBrowserUuid();
    const draftProduct: DraftProduct = {
      clientId,
      name: parsedName.data,
      unit: quickProductUnit,
      categoryId
    };
    const createdProduct: ProductListItem = {
      id: `draft:${clientId}`,
      name: parsedName.data,
      categoryId,
      unit: quickProductUnit,
      sortOrder: null,
      currentQuantity: null,
      currentPrice: null
    };

    setShowQuickProduct(false);
    setQuickProductName("");
    setQuickProductUnit("UN");
    setQuickProductCategoryId("");
    addProduct(createdProduct, false, draftProduct);
  };

  const completePendingProduct = async () => {
    if (!pendingProduct) return;
    if (!pendingProductCategoryId) {
      setPendingProductError("Escolha uma categoria para continuar.");
      window.setTimeout(() => document.getElementById("pending-product-category")?.focus(), 0);
      return;
    }

    setPendingProductError(null);
    try {
      await completePendingProductMutation.mutateAsync({
        id: pendingProduct.id,
        name: pendingProduct.name,
        categoryId: pendingProductCategoryId
      });

      const completedProduct: ProductListItem = {
        ...pendingProduct,
        categoryId: pendingProductCategoryId
      };

      setPendingProduct(null);
      setPendingProductCategoryId("");
      setProductSearch("");
      setProductActiveIndex(0);
      addProduct(completedProduct);
    } catch (error) {
      setPendingProductError(getProductErrorMessage(error));
    }
  };

  const validateAndBuild = () => {
    setItemErrors({});

    if (!supplierId && !draftSupplier) {
      window.setTimeout(() => document.getElementById("entry-supplier-search")?.focus(), 0);
      throw new Error("Selecione um fornecedor.");
    }

    const nextDateError = getFutureOperationalDateError(date);
    if (nextDateError) {
      setDateError(nextDateError);
      window.setTimeout(() => document.getElementById("entry-date")?.focus(), 0);
      throw new Error(nextDateError);
    }

    if (!deviceId) {
      throw new Error("Este dispositivo ainda não está pronto para registrar Entradas.");
    }

    if (!items.length) {
      window.setTimeout(() => document.getElementById("entry-product-search")?.focus(), 0);
      throw new Error("Adicione pelo menos um produto.");
    }

    const errors: ItemFieldErrors = {};
    let firstInvalidId: string | null = null;
    let firstInvalidField: "quantity" | "unitPrice" | null = null;

    const parsedItems = items.map((item) => {
      let quantity: number | null = null;
      let unitPrice: number | null = null;

      try {
        quantity = parsePositiveDecimal(
          item.quantity,
          `Quantidade de ${item.product.name}`
        );
      } catch (error) {
        errors[item.localId] = {
          ...errors[item.localId],
          quantity: validationMessage(error, "Quantidade inválida.")
        };
        if (!firstInvalidId) {
          firstInvalidId = item.localId;
          firstInvalidField = "quantity";
        }
      }

      try {
        unitPrice = parseOptionalPrice(item.unitPrice);
      } catch (error) {
        errors[item.localId] = {
          ...errors[item.localId],
          unitPrice: validationMessage(error, "Preço unitário inválido.")
        };
        if (!firstInvalidId) {
          firstInvalidId = item.localId;
          firstInvalidField = "unitPrice";
        }
      }

      return {
        ...(item.draftProduct
          ? { newProduct: item.draftProduct }
          : { productId: item.product.id }),
        quantity,
        unitPrice
      };
    });

    if (firstInvalidId && firstInvalidField) {
      setItemErrors(errors);
      const fieldId =
        firstInvalidField === "quantity"
          ? `entry-qty-${firstInvalidId}`
          : `entry-price-${firstInvalidId}`;
      window.setTimeout(() => document.getElementById(fieldId)?.focus(), 0);
      throw new Error("Revise os campos destacados antes de salvar a Entrada.");
    }

    return {
      supplierId: draftSupplier ? null : supplierId,
      newSupplier: draftSupplier,
      effectiveAt: buildEffectiveAt(date),
      deviceId,
      idempotencyKey,
      observation: observation.trim() || null,
      items: parsedItems.map((item) => ({
        ...item,
        quantity: item.quantity as number
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
        queryClient.invalidateQueries({ queryKey: ["suppliers"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] })
      ]);
      allowNavigationRef.current = true;
      navigate(`/entradas/${entryId}?saved=1`, { replace: true });
    } catch (error) {
      setMissingPriceReview(false);
      setActionError(getEntryErrorMessage(error));
    }
  };

  useCtrlEnter(
    requestSave,
    !showQuickSupplier &&
      !showQuickProduct &&
      !pendingProduct &&
      !duplicateProduct &&
      !missingPriceReview &&
      blocker.state !== "blocked" &&
      !saveMutation.isPending
  );

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
                    <p className="text-xs text-zinc-500">
                      {draftSupplier ? "Novo nesta Entrada" : selectedSupplier.company ?? "Cadastro pendente"}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="text-xs font-semibold text-red-700"
                    onClick={() => {
                      setDraftSupplier(null);
                      setSupplierId("");
                      setSupplierSearch("");
                      window.setTimeout(() => document.getElementById("entry-supplier-search")?.focus(), 0);
                    }}
                  >
                    Trocar
                  </button>
                </div>
              ) : (
                <>
                  <input
                    id="entry-supplier-search"
                    autoFocus
                    value={supplierSearch}
                    onChange={(event) => {
                      setSupplierSearch(event.target.value);
                      setSupplierActiveIndex(0);
                    }}
                    onKeyDown={handleSupplierSearchKeyDown}
                    placeholder="Buscar contato, empresa ou telefone"
                    aria-autocomplete="list"
                    aria-controls="entry-supplier-suggestions"
                    className="mt-2 min-h-11 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                  />
                  {supplierSearch ? (
                    <div id="entry-supplier-suggestions" role="listbox" className="mt-2 overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
                      {supplierSuggestions.map((supplier, index) => (
                        <button
                          key={supplier.id}
                          type="button"
                          role="option"
                          aria-selected={index === supplierActiveIndex}
                          className={`block w-full border-b border-zinc-100 px-4 py-3 text-left text-sm last:border-0 ${index === supplierActiveIndex ? "bg-red-50 text-red-950" : "hover:bg-zinc-50"}`}
                          onMouseEnter={() => setSupplierActiveIndex(index)}
                          onClick={() => selectSupplier(supplier.id)}
                        >
                          <span className="font-semibold">{supplier.name}</span>
                          <span className="ml-2 text-zinc-500">{supplier.company ?? "Pendente"}</span>
                        </button>
                      ))}
                      <button
                        type="button"
                        role="option"
                        aria-selected={supplierActiveIndex === supplierSuggestions.length}
                        className={`block w-full px-4 py-3 text-left text-sm font-semibold text-red-700 ${supplierActiveIndex === supplierSuggestions.length ? "bg-red-50" : "hover:bg-red-50"}`}
                        onMouseEnter={() => setSupplierActiveIndex(supplierSuggestions.length)}
                        onClick={openQuickSupplier}
                      >
                        + Cadastrar novo fornecedor
                      </button>
                    </div>
                  ) : null}
                </>
              )}
            </div>

            <TextField
              id="entry-date"
              label="Data *"
              type="date"
              max={localDateInputValue()}
              value={date}
              error={dateError}
              onChange={(event) => {
                const nextDate = event.target.value;
                setDate(nextDate);
                setDateError(getFutureOperationalDateError(nextDate));
              }}
              onKeyDown={(event) => {
                if (event.ctrlKey || event.metaKey || controlKeyPressed.current) return;
                if (event.key === "Enter") {
                  event.preventDefault();
                  const error = getFutureOperationalDateError(date);
                  setDateError(error);
                  if (error) {
                    document.getElementById("entry-date")?.focus();
                    return;
                  }
                  document.getElementById("entry-product-search")?.focus();
                }
              }}
            />
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
            <TextField
              id="entry-product-search"
              label="Adicionar produto"
              placeholder="Buscar por nome"
              value={productSearch}
              onChange={(event) => {
                setProductSearch(event.target.value);
                setProductActiveIndex(0);
              }}
              onKeyDown={handleProductSearchKeyDown}
              aria-autocomplete="list"
              aria-controls="entry-product-suggestions"
            />
            {productSearch ? (
              <div id="entry-product-suggestions" role="listbox" className="mt-2 overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm">
                {productSuggestions.map((product, index) => (
                  <button
                    key={product.id}
                    type="button"
                    role="option"
                    aria-selected={index === productActiveIndex}
                    className={`flex w-full items-center justify-between gap-3 border-b border-zinc-100 px-4 py-3 text-left text-sm last:border-0 ${index === productActiveIndex ? "bg-red-50 text-red-950" : "hover:bg-zinc-50"}`}
                    onMouseEnter={() => setProductActiveIndex(index)}
                    onClick={() => chooseProduct(product)}
                  >
                    <span>
                      <span className="font-semibold">{product.name}</span>
                      {product.categoryId === null && !product.id.startsWith("draft:") ? (
                        <span className="ml-2 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-800">
                          Cadastro pendente — falta categoria
                        </span>
                      ) : null}
                      {product.id.startsWith("draft:") ? (
                        <span className="ml-2 rounded-full bg-zinc-100 px-2 py-0.5 text-[11px] font-semibold text-zinc-600">
                          Novo nesta Entrada
                        </span>
                      ) : null}
                    </span>
                    <span className="text-xs text-zinc-500">{product.unit}</span>
                  </button>
                ))}
                {productSuggestions.length === 0 ? (
                  <div className="px-4 py-3 text-sm text-zinc-500">
                    Nenhum produto encontrado.
                  </div>
                ) : null}
                <button
                  type="button"
                  role="option"
                  aria-selected={productActiveIndex === productSuggestions.length}
                  className={`flex w-full items-center gap-2 px-4 py-3 text-left text-sm font-semibold text-red-700 ${productActiveIndex === productSuggestions.length ? "bg-red-50" : "hover:bg-red-50"}`}
                  onMouseEnter={() => setProductActiveIndex(productSuggestions.length)}
                  onClick={openQuickProduct}
                >
                  + Cadastrar novo produto
                </button>
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
                  <button
                    type="button"
                    className="text-xs font-semibold text-red-700"
                    onClick={() => {
                      setItems((current) => current.filter((candidate) => candidate.localId !== item.localId));
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
                    id={`entry-qty-${item.localId}`}
                    label="Quantidade *"
                    inputMode="decimal"
                    value={item.quantity}
                    error={itemErrors[item.localId]?.quantity}
                    aria-invalid={Boolean(itemErrors[item.localId]?.quantity)}
                    onChange={(event) => {
                      const value = event.target.value;
                      setItems((current) => current.map((candidate) => candidate.localId === item.localId ? { ...candidate, quantity: value } : candidate));
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
                          document.getElementById(`entry-price-${item.localId}`)?.focus();
                        }
                      }
                    }}
                  />
                  <TextField
                    id={`entry-price-${item.localId}`}
                    label="Preço unitário"
                    inputMode="decimal"
                    placeholder="Vazio = não informado"
                    value={item.unitPrice}
                    error={itemErrors[item.localId]?.unitPrice}
                    aria-invalid={Boolean(itemErrors[item.localId]?.unitPrice)}
                    onChange={(event) => {
                      const value = event.target.value;
                      setItems((current) => current.map((candidate) => candidate.localId === item.localId ? { ...candidate, unitPrice: value } : candidate));
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
                          document.getElementById("entry-product-search")?.focus();
                        }
                      }
                    }}
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
            <div className="flex items-center gap-3">
              <span className="hidden text-xs text-zinc-400 sm:inline">Atalho: Ctrl + Enter</span>
              <Button
                isLoading={saveMutation.isPending}
                loadingLabel="Salvando…"
                onClick={requestSave}
              >
                Salvar Entrada
              </Button>
            </div>
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
                Informe somente o nome. O fornecedor ficará como rascunho desta Entrada e só será cadastrado no banco quando a Entrada for salva.
              </p>
              <div className="mt-4">
                <TextField
                  id="quick-supplier-name"
                  label="Nome *"
                  autoFocus
                  value={quickSupplierName}
                  error={quickSupplierError}
                  onChange={(event) => {
                    setQuickSupplierName(event.target.value);
                    if (quickSupplierError) setQuickSupplierError(null);
                  }}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      document.getElementById("quick-supplier-submit")?.focus();
                    }
                  }}
                />
              </div>
              <div className="mt-5 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  onClick={() => {
                    setShowQuickSupplier(false);
                    setQuickSupplierError(null);
                  }}
                >
                  Cancelar
                </Button>
                <Button id="quick-supplier-submit" onClick={addQuickSupplier}>
                  Cadastrar e selecionar
                </Button>
              </div>
            </Card>
          </div>
        ) : null}

        {showQuickProduct ? (
          <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6">
            <Card className="w-full max-w-md p-5 shadow-xl">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Cadastro rápido</p>
              <h3 className="mt-1 text-xl font-semibold">Novo produto</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Nome e Unidade são obrigatórios. Categoria é opcional. Este Produto ficará somente como rascunho desta Entrada e só será cadastrado no banco quando a Entrada for salva.
              </p>
              <div className="mt-4 space-y-4">
                <TextField
                  id="quick-product-name"
                  label="Nome *"
                  autoFocus
                  value={quickProductName}
                  onChange={(event) => {
                    setQuickProductName(event.target.value);
                    if (quickProductError) setQuickProductError(null);
                  }}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      document.getElementById("quick-product-unit")?.focus();
                    }
                  }}
                />
                <SelectField
                  id="quick-product-unit"
                  label="Unidade *"
                  value={quickProductUnit}
                  onChange={(event) => setQuickProductUnit(event.target.value as ProductUnit)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      document.getElementById("quick-product-category")?.focus();
                    }
                  }}
                >
                  {PRODUCT_UNITS.map((unit) => (
                    <option key={unit} value={unit}>{unit}</option>
                  ))}
                </SelectField>
                <SelectField
                  id="quick-product-category"
                  label="Categoria"
                  value={quickProductCategoryId}
                  onChange={(event) => setQuickProductCategoryId(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      document.getElementById("quick-product-submit")?.focus();
                    }
                  }}
                >
                  <option value="">Sem categoria — cadastro pendente</option>
                  {(productCategoriesQuery.data ?? []).map((category) => (
                    <option key={category.id} value={category.id}>{category.name}</option>
                  ))}
                </SelectField>
              </div>
              {quickProductError ? (
                <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
                  {quickProductError}
                </div>
              ) : null}
              <div className="mt-5 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  onClick={() => setShowQuickProduct(false)}
                >
                  Cancelar
                </Button>
                <Button id="quick-product-submit" onClick={addQuickProduct}>
                  Adicionar à Entrada
                </Button>
              </div>
            </Card>
          </div>
        ) : null}

        {pendingProduct ? (
          <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6">
            <Card className="w-full max-w-md p-5 shadow-xl">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-amber-700">
                Cadastro pendente
              </p>
              <h3 className="mt-1 text-xl font-semibold">{pendingProduct.name}</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                Este Produto está sem categoria. Escolha uma categoria para concluir o cadastro e adicioná-lo à Entrada.
              </p>
              <div className="mt-4">
                <SelectField
                  id="pending-product-category"
                  label="Categoria *"
                  autoFocus
                  value={pendingProductCategoryId}
                  onChange={(event) => {
                    setPendingProductCategoryId(event.target.value);
                    if (pendingProductError) setPendingProductError(null);
                  }}
                >
                  <option value="">Selecione a categoria</option>
                  {(productCategoriesQuery.data ?? []).map((category) => (
                    <option key={category.id} value={category.id}>{category.name}</option>
                  ))}
                </SelectField>
              </div>
              {pendingProductError ? (
                <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
                  {pendingProductError}
                </div>
              ) : null}
              <div className="mt-5 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  disabled={completePendingProductMutation.isPending}
                  onClick={() => {
                    setPendingProduct(null);
                    setPendingProductCategoryId("");
                    setPendingProductError(null);
                  }}
                >
                  Cancelar
                </Button>
                <Button
                  isLoading={completePendingProductMutation.isPending}
                  loadingLabel="Salvando…"
                  onClick={() => void completePendingProduct()}
                >
                  Concluir e adicionar
                </Button>
              </div>
            </Card>
          </div>
        ) : null}

        <ConfirmDialog
          open={blocker.state === "blocked"}
          variant="warning"
          title="Sair da Nova Entrada?"
          description="Há dados preenchidos nesta Entrada que ainda não foram salvos. Se sair agora, esse preenchimento será perdido."
          confirmLabel="Descartar e sair"
          onCancel={() => {
            if (blocker.state === "blocked") blocker.reset();
          }}
          onConfirm={() => {
            if (blocker.state !== "blocked") return;
            allowNavigationRef.current = true;
            blocker.proceed();
          }}
        />

        {duplicateProduct ? (
          <div
            role="dialog"
            aria-modal="true"
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
            onKeyDown={handleDialogButtonArrowNavigation}
          >
            <Card className="w-full max-w-lg p-5 shadow-xl">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-amber-700">Produto já adicionado</p>
              <h3 className="mt-1 text-xl font-semibold">Adicionar novamente?</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-600">
                “{duplicateProduct.name}” já está nesta Entrada. Você pode ir para o item existente ou adicionar uma nova linha.
              </p>
              <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <Button autoFocus variant="ghost" onClick={() => setDuplicateProduct(null)}>Cancelar</Button>
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

function validationMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}


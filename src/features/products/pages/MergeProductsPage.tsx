import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { matchesSearchText } from "../../../shared/lib/searchText";
import { useAuth } from "../../auth/context/AuthContext";
import {
  getProductDetails,
  listActiveProducts,
  listProductCategories,
  mergeProducts,
  type MergeInitialPriceSource,
  type ProductDetails
} from "../api/products";
import { calculateMergeUnitFactors, determineMergePair } from "../lib/productMerge";
import {
  PRODUCT_UNITS,
  getProductErrorMessage,
  productNameSchema,
  type ProductUnit
} from "../lib/productValidation";
import { parsePositiveConversionQuantity } from "../lib/productUnitConversion";

type Stage = "setup" | "preview";

export function MergeProductsPage() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { deviceId } = useAuth();

  const [search, setSearch] = useState("");
  const [candidateId, setCandidateId] = useState("");
  const [candidateDetails, setCandidateDetails] = useState<ProductDetails | null>(null);
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [stage, setStage] = useState<Stage>("setup");
  const [finalName, setFinalName] = useState("");
  const [finalCategoryId, setFinalCategoryId] = useState("");
  const [finalUnit, setFinalUnit] = useState("");
  const [survivorEquivalentQuantity, setSurvivorEquivalentQuantity] = useState("");
  const [absorbedEquivalentQuantity, setAbsorbedEquivalentQuantity] = useState("");
  const [initialPriceSource, setInitialPriceSource] =
    useState<MergeInitialPriceSource>("none");
  const [formError, setFormError] = useState<string | null>(null);

  const sourceQuery = useQuery({
    queryKey: ["products", "detail", productId],
    queryFn: () => {
      if (!productId) throw new Error("Produto não encontrado.");
      return getProductDetails(productId);
    },
    enabled: Boolean(productId)
  });

  const productsQuery = useQuery({
    queryKey: ["products", "active"],
    queryFn: listActiveProducts
  });

  const categoriesQuery = useQuery({
    queryKey: ["products", "categories"],
    queryFn: listProductCategories
  });

  const candidates = useMemo(
    () =>
      (productsQuery.data ?? [])
        .filter(
          (product) =>
            product.id !== productId &&
            product.categoryId !== null &&
            matchesSearchText(product.name, search)
        )
        .sort((a, b) => a.name.localeCompare(b.name, "pt-BR")),
    [productId, productsQuery.data, search]
  );

  const pair = useMemo(() => {
    if (!sourceQuery.data || !candidateDetails) return null;
    return determineMergePair(sourceQuery.data, candidateDetails);
  }, [candidateDetails, sourceQuery.data]);

  const finalCategoryName =
    categoriesQuery.data?.find((category) => category.id === finalCategoryId)?.name ?? "";

  const unitsDiffer = Boolean(pair && pair.survivor.unit !== pair.absorbed.unit);

  const previewData = useMemo(() => {
    if (!pair || stage !== "preview") return null;

    const survivorQty = unitsDiffer
      ? parsePositiveConversionQuantity(
          survivorEquivalentQuantity,
          `Quantidade em ${pair.survivor.unit}`
        )
      : null;
    const absorbedQty = unitsDiffer
      ? parsePositiveConversionQuantity(
          absorbedEquivalentQuantity,
          `Quantidade em ${pair.absorbed.unit}`
        )
      : null;

    const factors = calculateMergeUnitFactors(
      pair.survivor.unit,
      pair.absorbed.unit,
      finalUnit as ProductUnit,
      survivorQty,
      absorbedQty
    );

    return { survivorQty, absorbedQty, ...factors };
  }, [
    absorbedEquivalentQuantity,
    finalUnit,
    pair,
    stage,
    survivorEquivalentQuantity,
    unitsDiffer
  ]);

  const mergeMutation = useMutation({ mutationFn: mergeProducts });

  const selectCandidate = async (id: string) => {
    if (!sourceQuery.data) return;

    setCandidateLoading(true);
    setFormError(null);

    try {
      const candidate = await queryClient.fetchQuery({
        queryKey: ["products", "detail", id],
        queryFn: () => getProductDetails(id)
      });
      const nextPair = determineMergePair(sourceQuery.data, candidate);

      setCandidateId(id);
      setCandidateDetails(candidate);
      setSearch("");
      setStage("setup");
      setFinalName(nextPair.survivor.name);
      setFinalCategoryId(nextPair.survivor.categoryId ?? "");
      setFinalUnit(nextPair.survivor.unit);
      setSurvivorEquivalentQuantity("");
      setAbsorbedEquivalentQuantity("");
      setInitialPriceSource(
        nextPair.survivor.initialPrice !== null
          ? "survivor"
          : nextPair.absorbed.initialPrice !== null
            ? "absorbed"
            : "none"
      );
    } catch (error) {
      setFormError(getProductErrorMessage(error));
    } finally {
      setCandidateLoading(false);
    }
  };

  const validateAndPreview = () => {
    if (!pair) return;
    setFormError(null);

    const nameResult = productNameSchema.safeParse(finalName);
    if (!nameResult.success) {
      setFormError(nameResult.error.issues[0]?.message ?? "Nome final inválido.");
      return;
    }

    if (!finalCategoryId) {
      setFormError("Escolha a categoria final.");
      return;
    }

    if (!PRODUCT_UNITS.includes(finalUnit as ProductUnit)) {
      setFormError("Escolha a unidade final.");
      return;
    }

    if (finalUnit !== pair.survivor.unit && finalUnit !== pair.absorbed.unit) {
      setFormError("A unidade final deve ser uma das unidades atuais.");
      return;
    }

    try {
      const survivorQty = unitsDiffer
        ? parsePositiveConversionQuantity(
            survivorEquivalentQuantity,
            `Quantidade em ${pair.survivor.unit}`
          )
        : null;
      const absorbedQty = unitsDiffer
        ? parsePositiveConversionQuantity(
            absorbedEquivalentQuantity,
            `Quantidade em ${pair.absorbed.unit}`
          )
        : null;

      calculateMergeUnitFactors(
        pair.survivor.unit,
        pair.absorbed.unit,
        finalUnit as ProductUnit,
        survivorQty,
        absorbedQty
      );
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Equivalência inválida.");
      return;
    }

    if (initialPriceSource === "survivor" && pair.survivor.initialPrice === null) {
      setFormError("O cadastro mais antigo não possui preço inicial.");
      return;
    }

    if (initialPriceSource === "absorbed" && pair.absorbed.initialPrice === null) {
      setFormError("O cadastro mais novo não possui preço inicial.");
      return;
    }

    setFinalName(nameResult.data);
    setStage("preview");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const confirmMerge = async () => {
    if (!pair || !deviceId) {
      setFormError("Este dispositivo ainda não está pronto para mesclar Produtos.");
      return;
    }

    try {
      setFormError(null);
      const survivorQty = unitsDiffer
        ? parsePositiveConversionQuantity(
            survivorEquivalentQuantity,
            `Quantidade em ${pair.survivor.unit}`
          )
        : null;
      const absorbedQty = unitsDiffer
        ? parsePositiveConversionQuantity(
            absorbedEquivalentQuantity,
            `Quantidade em ${pair.absorbed.unit}`
          )
        : null;

      const result = await mergeMutation.mutateAsync({
        productAId: pair.survivor.id,
        productBId: pair.absorbed.id,
        finalName,
        finalCategoryId,
        finalUnit: finalUnit as ProductUnit,
        survivorEquivalentQuantity: survivorQty,
        absorbedEquivalentQuantity: absorbedQty,
        initialPriceSource,
        deviceId
      });

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["products"] }),
        queryClient.invalidateQueries({ queryKey: ["categories"] }),
        queryClient.invalidateQueries({ queryKey: ["stock"] }),
        queryClient.invalidateQueries({ queryKey: ["entries"] }),
        queryClient.invalidateQueries({ queryKey: ["conferences"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] }),
        queryClient.invalidateQueries({ queryKey: ["trash"] })
      ]);

      navigate(`/produtos/${result.survivorProductId}`, { replace: true });
    } catch (error) {
      setFormError(getProductErrorMessage(error));
    }
  };

  const backTo = productId ? `/produtos/${productId}` : "/produtos/lista";

  return (
    <AppShell title="Mesclar produtos" showBack backTo={backTo}>
      {sourceQuery.isPending || productsQuery.isPending || categoriesQuery.isPending ? (
        <Card className="p-5 text-sm text-zinc-600">Carregando Produtos…</Card>
      ) : null}

      {sourceQuery.isError || productsQuery.isError || categoriesQuery.isError ? (
        <Card className="border-red-200 p-5 text-sm text-red-800">
          Não foi possível preparar a mescla.
        </Card>
      ) : null}

      {sourceQuery.data && !sourceQuery.isError ? (
        <section className="space-y-5">
          <Card className="p-5">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
              Produto atual
            </p>
            <h2 className="mt-1 text-xl font-semibold text-zinc-950">{sourceQuery.data.name}</h2>
            <p className="mt-1 text-sm text-zinc-500">
              {sourceQuery.data.unit} · criado em {formatDateTime(sourceQuery.data.createdAt)}
            </p>
          </Card>

          {!pair ? (
            <Card className="p-5">
              <h3 className="text-lg font-semibold text-zinc-950">Escolha o cadastro duplicado</h3>
              <p className="mt-1 text-sm leading-6 text-zinc-600">
                O aplicativo decidirá automaticamente qual ID permanece: sempre o cadastro mais antigo.
              </p>

              <label className="mt-4 block" htmlFor="merge-product-search">
                <span className="text-sm font-medium text-zinc-800">Pesquisar Produto</span>
                <input
                  id="merge-product-search"
                  autoFocus
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Digite o nome do produto duplicado"
                  className="mt-2 min-h-11 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                />
              </label>

              <div className="mt-4 max-h-80 space-y-2 overflow-y-auto">
                {candidates.map((product) => (
                  <button
                    key={product.id}
                    type="button"
                    onClick={() => void selectCandidate(product.id)}
                    className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-3 text-left transition hover:border-red-200 hover:bg-red-50/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600"
                  >
                    <span className="block font-semibold text-zinc-950">{product.name}</span>
                    <span className="mt-1 block text-xs text-zinc-500">
                      {product.unit} · criado em {formatDateTime(product.createdAt)}
                    </span>
                  </button>
                ))}
                {candidates.length === 0 ? (
                  <p className="py-4 text-sm text-zinc-500">Nenhum outro Produto ativo encontrado.</p>
                ) : null}
              </div>
            </Card>
          ) : null}

          {candidateLoading ? (
            <Card className="p-5 text-sm text-zinc-600">Carregando o Produto duplicado…</Card>
          ) : null}

          {pair && stage === "setup" ? (
            <>
              <div className="grid gap-4 lg:grid-cols-2">
                <ProductIdentityCard label="ID que permanece" product={pair.survivor} helper="Cadastro mais antigo" />
                <ProductIdentityCard label="ID que será absorvido" product={pair.absorbed} helper="Não vai para a Lixeira" danger />
              </div>

              <Card className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h3 className="text-lg font-semibold text-zinc-950">Dados finais visíveis</h3>
                    <p className="mt-1 text-sm leading-6 text-zinc-600">
                      Escolha como o Produto único ficará depois da mescla.
                    </p>
                  </div>
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setCandidateId("");
                      setCandidateDetails(null);
                      setStage("setup");
                      setFormError(null);
                    }}
                  >
                    Trocar duplicado
                  </Button>
                </div>

                <div className="mt-5 grid gap-4 lg:grid-cols-2">
                  <TextField id="merge-final-name" label="Nome final" value={finalName} onChange={(event) => setFinalName(event.target.value)} />

                  <label className="block">
                    <span className="text-sm font-medium text-zinc-800">Categoria final</span>
                    <select
                      id="merge-final-category"
                      value={finalCategoryId}
                      onChange={(event) => setFinalCategoryId(event.target.value)}
                      className="mt-2 min-h-11 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                    >
                      <option value="">Selecione…</option>
                      {categoriesQuery.data?.map((category) => (
                        <option key={category.id} value={category.id}>{category.name}</option>
                      ))}
                    </select>
                  </label>

                  <label className="block">
                    <span className="text-sm font-medium text-zinc-800">Unidade final</span>
                    <select
                      id="merge-final-unit"
                      value={finalUnit}
                      onChange={(event) => setFinalUnit(event.target.value)}
                      className="mt-2 min-h-11 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                    >
                      {[...new Set([pair.survivor.unit, pair.absorbed.unit])].map((unit) => (
                        <option key={unit} value={unit}>{unit}</option>
                      ))}
                    </select>
                  </label>

                  <label className="block">
                    <span className="text-sm font-medium text-zinc-800">Referência inicial de preço</span>
                    <select
                      value={initialPriceSource}
                      onChange={(event) => setInitialPriceSource(event.target.value as MergeInitialPriceSource)}
                      className="mt-2 min-h-11 w-full rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
                    >
                      <option value="none">Nenhuma</option>
                      {pair.survivor.initialPrice !== null ? (
                        <option value="survivor">Cadastro mais antigo · {formatMoney(pair.survivor.initialPrice)}</option>
                      ) : null}
                      {pair.absorbed.initialPrice !== null ? (
                        <option value="absorbed">Cadastro mais novo · {formatMoney(pair.absorbed.initialPrice)}</option>
                      ) : null}
                    </select>
                    <span className="mt-1.5 block text-xs leading-5 text-zinc-500">
                      Só é usada se não existir um preço real válido no histórico de Entradas.
                    </span>
                  </label>
                </div>

                {unitsDiffer ? (
                  <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4">
                    <p className="font-semibold text-amber-950">Conversão obrigatória de unidade</p>
                    <p className="mt-1 text-sm leading-6 text-amber-900">
                      Informe uma equivalência real entre as duas unidades.
                    </p>
                    <div className="mt-4 grid gap-4 sm:grid-cols-2">
                      <TextField id="merge-survivor-equivalence" label={`Quantidade em ${pair.survivor.unit}`} inputMode="decimal" value={survivorEquivalentQuantity} onChange={(event) => setSurvivorEquivalentQuantity(event.target.value)} placeholder="Ex.: 1" />
                      <TextField id="merge-absorbed-equivalence" label={`Quantidade em ${pair.absorbed.unit}`} inputMode="decimal" value={absorbedEquivalentQuantity} onChange={(event) => setAbsorbedEquivalentQuantity(event.target.value)} placeholder="Ex.: 12" />
                    </div>
                  </div>
                ) : (
                  <div className="mt-5 rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-700">
                    Os dois cadastros já usam a mesma unidade: <strong>{pair.survivor.unit}</strong>.
                  </div>
                )}

                <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
                  Estoque inicial e preço inicial do cadastro absorvido não serão somados nem
                  transformados em eventos artificiais. Esses valores ficam preservados no backup
                  da mescla. O estoque final exigirá uma nova Conferência física.
                </div>

                {formError ? <ErrorBox message={formError} /> : null}

                <div className="mt-5 flex justify-end">
                  <Button onClick={validateAndPreview}>Ver prévia da mescla</Button>
                </div>
              </Card>
            </>
          ) : null}

          {pair && stage === "preview" && previewData ? (
            <Card className="p-5">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Confirmação final</p>
              <h3 className="mt-1 text-xl font-semibold text-zinc-950">Prévia da mescla</h3>

              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                <PreviewRow label="Produto final" value={finalName} />
                <PreviewRow label="Categoria final" value={finalCategoryName || "—"} />
                <PreviewRow label="Unidade final" value={finalUnit} />
                <PreviewRow label="ID que permanece" value={`${pair.survivor.name} · mais antigo`} />
              </div>

              {unitsDiffer ? (
                <div className="mt-4 rounded-xl border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm text-zinc-700">
                  Equivalência: <strong>{formatDecimal(previewData.survivorQty ?? 0)} {pair.survivor.unit}</strong>
                  {" = "}
                  <strong>{formatDecimal(previewData.absorbedQty ?? 0)} {pair.absorbed.unit}</strong>.
                  Todo o histórico será convertido para <strong>{finalUnit}</strong>.
                </div>
              ) : null}

              <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm leading-6 text-amber-950">
                <strong>Depois de confirmar:</strong> Entradas, preços e Conferências dos dois IDs
                passam a pertencer ao cadastro mais antigo. O cadastro mais novo é absorvido e não
                aparece na Lixeira. O estoque atual ficará como <strong>Conferência necessária</strong>
                até uma nova contagem física.
              </div>

              <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-4 text-sm leading-6 text-red-900">
                Esta ação não possui desfazer pela interface. Antes de alterar os dados, o banco
                guarda um backup completo dos dois cadastros e do histórico afetado.
              </div>

              {formError ? <ErrorBox message={formError} /> : null}

              <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                <Button variant="ghost" disabled={mergeMutation.isPending} onClick={() => { setFormError(null); setStage("setup"); }}>
                  Voltar
                </Button>
                <Button variant="danger" isLoading={mergeMutation.isPending} loadingLabel="Mesclando…" onClick={() => void confirmMerge()}>
                  Confirmar mescla
                </Button>
              </div>
            </Card>
          ) : null}
        </section>
      ) : null}
    </AppShell>
  );
}

function ProductIdentityCard({
  label,
  product,
  helper,
  danger = false
}: {
  label: string;
  product: { name: string; unit: string; createdAt: string };
  helper: string;
  danger?: boolean;
}) {
  return (
    <Card className={`p-5 ${danger ? "border-amber-200" : ""}`}>
      <p className={`text-xs font-semibold uppercase tracking-wide ${danger ? "text-amber-700" : "text-red-700"}`}>{label}</p>
      <p className="mt-1 text-lg font-semibold text-zinc-950">{product.name}</p>
      <p className="mt-1 text-sm text-zinc-500">{product.unit} · {formatDateTime(product.createdAt)}</p>
      <p className="mt-2 text-xs font-medium text-zinc-600">{helper}</p>
    </Card>
  );
}

function PreviewRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-zinc-200 bg-white px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-zinc-400">{label}</p>
      <p className="mt-1 font-semibold text-zinc-950">{value}</p>
    </div>
  );
}

function ErrorBox({ message }: { message: string }) {
  return <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{message}</div>;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}

function formatDecimal(value: number) {
  return new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 8 }).format(value);
}

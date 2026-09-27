import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useRef, useState } from "react";
import { useBlocker, useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../../../shared/components/AppShell";
import { useNetworkStatus } from "../../../shared/offline/NetworkContext";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { useCtrlEnter } from "../../../shared/hooks/useCtrlEnter";
import { handleDialogButtonArrowNavigation } from "../../../shared/lib/dialogKeyboard";
import { createBrowserUuid } from "../../../shared/lib/browserUuid";
import { useAuth } from "../../auth/context/AuthContext";
import { savePendingProductConference } from "../../offline/lib/pendingOperations";
import { listActiveProducts, type ProductListItem } from "../../products/api/products";
import {
  createProductConference,
  reviewConferenceConsumption,
  type ConferenceConsumptionWarning,
  type ProductConferenceWriteInput
} from "../api/conferences";
import {
  buildConferenceEffectiveAt,
  getConferenceErrorMessage,
  localDateInputValue,
  parseConferenceQuantity
} from "../lib/conferenceValidation";

export function NewProductConferencePage() {
  const { productId } = useParams();
  const productQuery = useQuery({
    queryKey: ["products", "detail", productId],
    queryFn: () => {
      if (!productId) throw new Error("Produto não encontrado.");
      return listActiveProducts().then((products) => {
        const product = products.find((item) => item.id === productId);
        if (!product) throw new Error("Produto não encontrado.");
        return product;
      });
    },
    enabled: Boolean(productId)
  });

  const backTo = productId ? `/produtos/${productId}/estoque` : "/produtos/lista";

  return (
    <AppShell title="Conferência do produto" showBack backTo={backTo}>
      {productQuery.isPending ? (
        <Card className="p-5 text-sm text-zinc-600">Carregando produto…</Card>
      ) : null}
      {productQuery.isError ? (
        <Card className="border-red-200 p-5 text-sm text-red-800">
          Não foi possível carregar este produto.
        </Card>
      ) : null}
      {productQuery.data ? (
        <ProductConferenceForm key={productQuery.data.id} product={productQuery.data} />
      ) : null}
    </AppShell>
  );
}

function ProductConferenceForm({ product }: { product: ProductListItem }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { session, appUserId, deviceId, displayName, username } = useAuth();
  const { isOnline } = useNetworkStatus();
  const [responsible, setResponsible] = useState("");
  const [responsibleError, setResponsibleError] = useState<string | null>(null);
  const [quantity, setQuantity] = useState("");
  const [quantityError, setQuantityError] = useState<string | null>(null);
  const [observation, setObservation] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [consumptionWarnings, setConsumptionWarnings] = useState<ConferenceConsumptionWarning[]>([]);
  const [pendingPayload, setPendingPayload] = useState<ProductConferenceWriteInput | null>(null);
  const [checkingSave, setCheckingSave] = useState(false);
  const [idempotencyKey] = useState(() => createBrowserUuid());
  const allowNavigationRef = useRef(false);

  const dirty = useMemo(
    () => Boolean(responsible.trim() || quantity.trim() || observation.trim()),
    [observation, quantity, responsible]
  );

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty &&
      !allowNavigationRef.current &&
      currentLocation.pathname !== nextLocation.pathname
  );

  const saveMutation = useMutation({ mutationFn: createProductConference });

  const validateAndBuild = (): ProductConferenceWriteInput => {
    setActionError(null);
    const cleanResponsible = responsible.trim().replace(/\s+/g, " ");
    if (!cleanResponsible) {
      setResponsibleError("Informe o responsável pela contagem física.");
      window.setTimeout(() => document.getElementById("product-conference-responsible")?.focus(), 0);
      throw new Error("Informe o responsável pela contagem física.");
    }
    if (cleanResponsible.length > 160) {
      setResponsibleError("O responsável pode ter no máximo 160 caracteres.");
      window.setTimeout(() => document.getElementById("product-conference-responsible")?.focus(), 0);
      throw new Error("O responsável pode ter no máximo 160 caracteres.");
    }
    setResponsibleError(null);

    let parsedQuantity: number;
    try {
      parsedQuantity = parseConferenceQuantity(quantity, "Nova quantidade");
      setQuantityError(null);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Nova quantidade inválida.";
      setQuantityError(message);
      window.setTimeout(() => document.getElementById("product-conference-quantity")?.focus(), 0);
      throw error;
    }

    if (!deviceId) throw new Error("Este dispositivo ainda não está pronto para registrar Conferências.");

    return {
      productId: product.id,
      effectiveAt: buildConferenceEffectiveAt(localDateInputValue()),
      physicalResponsible: cleanResponsible,
      deviceId,
      idempotencyKey,
      quantity: parsedQuantity,
      observation: observation.trim() || null
    };
  };

  const persist = async (payload: ProductConferenceWriteInput) => {
    if (!isOnline) {
      if (!session?.user.id || !deviceId) {
        throw new Error("Este acesso ainda não está pronto para guardar uma Conferência offline.");
      }

      await savePendingProductConference(payload, {
        authUserId: session.user.id,
        appUserId,
        deviceId,
        actorLabel: displayName ?? (username ? `@${username}` : "Usuário autorizado"),
        productLabel: product.name
      });

      allowNavigationRef.current = true;
      navigate("/alertas/pendencias-locais?saved=conference", { replace: true });
      return;
    }

    await saveMutation.mutateAsync(payload);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["conferences"] }),
      queryClient.invalidateQueries({ queryKey: ["products"] }),
      queryClient.invalidateQueries({ queryKey: ["products", "detail", product.id] }),
      queryClient.invalidateQueries({ queryKey: ["stock", "current"] }),
      queryClient.invalidateQueries({ queryKey: ["purchases"] })
    ]);
    allowNavigationRef.current = true;
    navigate(`/produtos/${product.id}/estoque?saved=1`, { replace: true });
  };

  const requestSave = async () => {
    let payload: ProductConferenceWriteInput;
    try {
      payload = validateAndBuild();
    } catch (error) {
      const message = error instanceof Error ? error.message : "";
      const fieldValidationError =
        message === "Informe o responsável pela contagem física." ||
        message === "O responsável pode ter no máximo 160 caracteres." ||
        message.startsWith("Nova quantidade");
      setActionError(fieldValidationError ? null : getConferenceErrorMessage(error));
      return;
    }

    if (!isOnline) {
      try {
        await persist(payload);
      } catch (error) {
        setActionError(getConferenceErrorMessage(error));
      }
      return;
    }

    setCheckingSave(true);
    try {
      const warnings = await reviewConferenceConsumption({
        effectiveAt: payload.effectiveAt,
        items: [{ productId: payload.productId, quantity: payload.quantity }]
      });
      if (warnings.length) {
        setPendingPayload(payload);
        setConsumptionWarnings(warnings);
        return;
      }
      await persist(payload);
    } catch (error) {
      setActionError(getConferenceErrorMessage(error));
    } finally {
      setCheckingSave(false);
    }
  };

  const confirmConsumptionWarnings = async () => {
    if (!pendingPayload) return;
    const payload = pendingPayload;
    setPendingPayload(null);
    setConsumptionWarnings([]);
    setCheckingSave(true);
    try {
      await persist(payload);
    } catch (error) {
      setActionError(getConferenceErrorMessage(error));
    } finally {
      setCheckingSave(false);
    }
  };

  useCtrlEnter(
    () => void requestSave(),
    blocker.state !== "blocked" && !pendingPayload && !saveMutation.isPending && !checkingSave
  );

  return (
    <section>
      {!isOnline ? (
        <div className="mb-5 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-950">
          Você está offline. Ao salvar, esta Conferência unitária ficará somente neste aparelho como <strong>PENDENTE DE CONFIRMAÇÃO</strong>. A revisão de consumo será feita quando o envio for confirmado online.
        </div>
      ) : null}
      <Card className="p-5">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">Produto</p>
        <h2 className="mt-1 text-xl font-semibold text-zinc-950">{product.name}</h2>
        <p className="mt-1 text-sm text-zinc-500">{product.unit}</p>

        <div className="mt-5 grid gap-4 lg:grid-cols-2">
          <div>
            <p className="text-sm font-medium text-zinc-800">Quantidade atual registrada</p>
            <div className="mt-2 min-h-11 rounded-xl border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm font-semibold text-zinc-700">
              {formatQuantity(product.currentQuantity, product.unit)}
            </div>
          </div>

          <TextField
            id="product-conference-responsible"
            label="Responsável pela contagem física"
            value={responsible}
            error={responsibleError}
            autoFocus
            onChange={(event) => {
              setResponsible(event.target.value);
              if (responsibleError) setResponsibleError(null);
            }}
            onKeyDown={(event) => {
              if (event.key !== "Enter" || event.ctrlKey || event.metaKey) return;
              event.preventDefault();
              document.getElementById("product-conference-quantity")?.focus();
            }}
          />

          <TextField
            id="product-conference-quantity"
            label="Nova quantidade"
            inputMode="decimal"
            placeholder="0"
            value={quantity}
            error={quantityError}
            onChange={(event) => {
              setQuantity(event.target.value);
              if (quantityError) setQuantityError(null);
            }}
            onKeyDown={(event) => {
              if (event.key !== "Enter" || event.ctrlKey || event.metaKey) return;
              event.preventDefault();
              document.getElementById("product-conference-observation")?.focus();
            }}
          />

          <label className="block lg:col-span-2" htmlFor="product-conference-observation">
            <span className="text-sm font-medium text-zinc-800">Observação (opcional)</span>
            <textarea
              id="product-conference-observation"
              rows={3}
              value={observation}
              onChange={(event) => setObservation(event.target.value)}
              onKeyDown={(event) => {
                if (event.key !== "Enter" || event.shiftKey || event.ctrlKey || event.metaKey) return;
                event.preventDefault();
                document.getElementById("product-conference-save")?.focus();
              }}
              className="mt-2 w-full scroll-mt-24 rounded-xl border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100"
            />
          </label>
        </div>

        <p className="mt-4 text-xs leading-5 text-zinc-500">
          A Conferência será registrada com a data e o horário atuais. A contagem física passa a ser o novo checkpoint deste produto.
        </p>

        {actionError ? (
          <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            {actionError}
          </div>
        ) : null}

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <Button
            variant="ghost"
            disabled={saveMutation.isPending || checkingSave}
            onClick={() => navigate(`/produtos/${product.id}/estoque`)}
          >
            Cancelar
          </Button>
          <Button
            id="product-conference-save"
            isLoading={saveMutation.isPending || checkingSave}
            loadingLabel={saveMutation.isPending ? "Salvando…" : "Verificando…"}
            onClick={() => void requestSave()}
          >
            {isOnline ? "Salvar Conferência" : "Salvar pendência"}
          </Button>
        </div>
      </Card>

      {pendingPayload && consumptionWarnings.length > 0 ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="product-consumption-warning-title"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
          onKeyDown={handleDialogButtonArrowNavigation}
        >
          <Card className="max-h-[85vh] w-full max-w-xl overflow-y-auto p-5 shadow-xl">
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-amber-700">Confira o consumo</p>
            <h3 id="product-consumption-warning-title" className="mt-1 text-xl font-semibold text-zinc-950">
              Há consumo fora do padrão
            </h3>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Confira a nova quantidade. Se confirmar, este valor será considerado verdadeiro e entrará integralmente nos cálculos futuros.
            </p>

            {consumptionWarnings.map((warning) => (
              <div key={warning.productId} className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm">
                <p className="font-semibold text-zinc-950">{product.name}</p>
                <p className="mt-1 text-zinc-700">{warningTitle(warning)}</p>
                <p className="mt-2 text-xs leading-5 text-zinc-600">
                  Esperado: <strong>{formatConsumption(warning.expectedConsumption, product.unit)}</strong>
                  {" · "}Conferência indica: <strong>{formatConsumption(warning.actualConsumption, product.unit)}</strong>
                  {" · "}Diferença: <strong>{formatDifference(warning, product.unit)}</strong>
                </p>
              </div>
            ))}

            <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button
                autoFocus
                variant="ghost"
                onClick={() => {
                  setPendingPayload(null);
                  setConsumptionWarnings([]);
                }}
              >
                Voltar e conferir
              </Button>
              <Button
                isLoading={checkingSave || saveMutation.isPending}
                loadingLabel={saveMutation.isPending ? "Salvando…" : "Verificando…"}
                onClick={() => void confirmConsumptionWarnings()}
              >
                Confirmar mesmo assim
              </Button>
            </div>
          </Card>
        </div>
      ) : null}

      <ConfirmDialog
        open={blocker.state === "blocked"}
        title="Sair da Conferência?"
        description="A nova quantidade ainda não foi salva."
        confirmLabel="Descartar e sair"
        variant="warning"
        onCancel={() => blocker.reset?.()}
        onConfirm={() => {
          allowNavigationRef.current = true;
          blocker.proceed?.();
        }}
      />
    </section>
  );
}

function warningTitle(warning: ConferenceConsumptionWarning) {
  if (warning.kind === "inconsistent") {
    return "A contagem indica aumento de estoque que não é explicado pelas Entradas registradas.";
  }
  return warning.kind === "above" ? "Consumo muito acima do esperado." : "Consumo muito abaixo do esperado.";
}

function formatConsumption(value: number, unit: string) {
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(value)} ${unit}`;
}

function formatDifference(warning: ConferenceConsumptionWarning, unit: string) {
  const sign = warning.difference > 0 ? "+" : "";
  const amount = `${sign}${formatConsumption(warning.difference, unit)}`;
  if (warning.differencePercent === null) return amount;
  return `${amount} (${sign}${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(warning.differencePercent)}%)`;
}

function formatQuantity(value: number | null, unit: string) {
  if (value === null) return "Sem dados";
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(value)} ${unit}`;
}

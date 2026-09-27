import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Button } from "../../../shared/components/ui/Button";
import { Card } from "../../../shared/components/ui/Card";
import { TextField } from "../../../shared/components/ui/TextField";
import { handleDialogButtonArrowNavigation } from "../../../shared/lib/dialogKeyboard";
import { useAuth } from "../../auth/context/AuthContext";
import {
  convertProductUnit,
  type ProductDetails
} from "../api/products";
import {
  PRODUCT_UNITS,
  getProductErrorMessage,
  type ProductUnit
} from "../lib/productValidation";
import {
  calculateUnitConversionFactor,
  convertPriceForUnit,
  convertQuantityForUnit,
  parsePositiveConversionQuantity
} from "../lib/productUnitConversion";

type Stage = "setup" | "preview";

type ConversionDraft = {
  newUnit: ProductUnit;
  oldQuantity: number;
  newQuantity: number;
};

export function ProductUnitConversionDialog({
  product,
  onClose,
  onConverted
}: {
  product: ProductDetails;
  onClose: () => void;
  onConverted: () => void;
}) {
  const { deviceId } = useAuth();
  const queryClient = useQueryClient();
  const [stage, setStage] = useState<Stage>("setup");
  const [newUnit, setNewUnit] = useState("");
  const [oldQuantity, setOldQuantity] = useState("1");
  const [newQuantity, setNewQuantity] = useState("");
  const [newUnitError, setNewUnitError] = useState<string | null>(null);
  const [oldQuantityError, setOldQuantityError] = useState<string | null>(null);
  const [newQuantityError, setNewQuantityError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [draft, setDraft] = useState<ConversionDraft | null>(null);

  const conversionMutation = useMutation({ mutationFn: convertProductUnit });

  const factor = useMemo(
    () =>
      draft
        ? calculateUnitConversionFactor(draft.oldQuantity, draft.newQuantity)
        : null,
    [draft]
  );

  const continueToPreview = () => {
    setActionError(null);

    if (!newUnit || newUnit === product.unit) {
      setNewUnitError("Escolha uma unidade diferente da atual.");
      window.setTimeout(() => document.getElementById("unit-conversion-new-unit")?.focus(), 0);
      return;
    }
    setNewUnitError(null);

    let parsedOldQuantity: number;
    let parsedNewQuantity: number;

    try {
      parsedOldQuantity = parsePositiveConversionQuantity(
        oldQuantity,
        `Quantidade em ${product.unit}`
      );
      setOldQuantityError(null);
    } catch (error) {
      setOldQuantityError(error instanceof Error ? error.message : "Quantidade inválida.");
      window.setTimeout(() => document.getElementById("unit-conversion-old-quantity")?.focus(), 0);
      return;
    }

    try {
      parsedNewQuantity = parsePositiveConversionQuantity(
        newQuantity,
        `Quantidade em ${newUnit}`
      );
      setNewQuantityError(null);
    } catch (error) {
      setNewQuantityError(error instanceof Error ? error.message : "Quantidade inválida.");
      window.setTimeout(() => document.getElementById("unit-conversion-new-quantity")?.focus(), 0);
      return;
    }

    setDraft({
      newUnit: newUnit as ProductUnit,
      oldQuantity: parsedOldQuantity,
      newQuantity: parsedNewQuantity
    });
    setStage("preview");
  };

  const confirmConversion = async () => {
    if (!draft) return;
    if (!deviceId) {
      setActionError("Este dispositivo ainda não está pronto para alterar a unidade.");
      return;
    }

    setActionError(null);

    try {
      await conversionMutation.mutateAsync({
        productId: product.id,
        newUnit: draft.newUnit,
        oldQuantity: draft.oldQuantity,
        newQuantity: draft.newQuantity,
        deviceId
      });

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["products"] }),
        queryClient.invalidateQueries({ queryKey: ["stock"] }),
        queryClient.invalidateQueries({ queryKey: ["entries"] }),
        queryClient.invalidateQueries({ queryKey: ["conferences"] }),
        queryClient.invalidateQueries({ queryKey: ["purchases"] })
      ]);

      onConverted();
    } catch (error) {
      setActionError(getProductErrorMessage(error));
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="unit-conversion-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-6"
      onKeyDown={handleDialogButtonArrowNavigation}
    >
      <Card className="max-h-[90vh] w-full max-w-2xl overflow-y-auto p-5 shadow-xl">
        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-red-700">
          Alteração protegida
        </p>
        <h3 id="unit-conversion-title" className="mt-1 text-xl font-semibold text-zinc-950">
          Alterar unidade de {product.name}
        </h3>

        {stage === "setup" ? (
          <>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Informe uma equivalência real entre a unidade atual e a nova. Exemplo:{" "}
              <strong>12 UN = 1 CX</strong> ou <strong>1 PCT = 100 UN</strong>.
            </p>

            <div className="mt-5 grid gap-4 sm:grid-cols-3 sm:items-end">
              <TextField
                id="unit-conversion-old-quantity"
                label={`Quantidade em ${product.unit}`}
                inputMode="decimal"
                value={oldQuantity}
                error={oldQuantityError}
                onChange={(event) => {
                  setOldQuantity(event.target.value);
                  if (oldQuantityError) setOldQuantityError(null);
                }}
                onKeyDown={(event) => {
                  if (event.key !== "Enter" || event.ctrlKey || event.metaKey) return;
                  event.preventDefault();
                  document.getElementById("unit-conversion-new-unit")?.focus();
                }}
              />

              <label className="block">
                <span className="text-sm font-medium text-zinc-800">Nova unidade</span>
                <select
                  id="unit-conversion-new-unit"
                  value={newUnit}
                  autoFocus
                  aria-invalid={Boolean(newUnitError)}
                  onChange={(event) => {
                    setNewUnit(event.target.value);
                    if (newUnitError) setNewUnitError(null);
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== "Enter" || event.ctrlKey || event.metaKey) return;
                    event.preventDefault();
                    document.getElementById("unit-conversion-new-quantity")?.focus();
                  }}
                  className={`mt-2 min-h-11 w-full rounded-xl border bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition focus:border-red-500 focus:ring-2 focus:ring-red-100 ${
                    newUnitError ? "border-red-400" : "border-zinc-300"
                  }`}
                >
                  <option value="">Selecione…</option>
                  {PRODUCT_UNITS.filter((unit) => unit !== product.unit).map((unit) => (
                    <option key={unit} value={unit}>
                      {unit}
                    </option>
                  ))}
                </select>
                {newUnitError ? (
                  <span className="mt-1.5 block text-xs font-medium text-red-700">
                    {newUnitError}
                  </span>
                ) : null}
              </label>

              <TextField
                id="unit-conversion-new-quantity"
                label={`Quantidade em ${newUnit || "nova unidade"}`}
                inputMode="decimal"
                value={newQuantity}
                error={newQuantityError}
                onChange={(event) => {
                  setNewQuantity(event.target.value);
                  if (newQuantityError) setNewQuantityError(null);
                }}
                onKeyDown={(event) => {
                  if (event.key !== "Enter" || event.ctrlKey || event.metaKey) return;
                  event.preventDefault();
                  document.getElementById("unit-conversion-preview")?.focus();
                }}
              />
            </div>

            <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
              A conversão será retroativa: estoque inicial, todas as Entradas, preços unitários e
              todas as Conferências deste Produto serão convertidos. Registros excluídos também
              serão ajustados para continuarem coerentes caso sejam restaurados.
            </div>

            {actionError ? <ErrorBox message={actionError} /> : null}

            <div className="mt-5 flex flex-wrap justify-end gap-2">
              <Button variant="ghost" onClick={onClose}>Cancelar</Button>
              <Button id="unit-conversion-preview" onClick={continueToPreview}>
                Ver prévia
              </Button>
            </div>
          </>
        ) : draft && factor !== null ? (
          <>
            <p className="mt-2 text-sm leading-6 text-zinc-600">
              Confira os valores antes de confirmar. Nenhum histórico é convertido até você
              confirmar esta etapa.
            </p>

            <div className="mt-4 rounded-xl border border-red-100 bg-red-50 px-4 py-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-red-700">
                Equivalência
              </p>
              <p className="mt-1 text-lg font-semibold text-zinc-950">
                {formatDecimal(draft.oldQuantity)} {product.unit} ={" "}
                {formatDecimal(draft.newQuantity)} {draft.newUnit}
              </p>
            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <PreviewRow
                label="Estoque atual"
                before={formatQuantity(product.currentQuantity, product.unit)}
                after={formatQuantity(
                  convertQuantityForUnit(product.currentQuantity, factor),
                  draft.newUnit
                )}
              />
              <PreviewRow
                label="Preço atual"
                before={formatPrecisePrice(product.currentPrice, product.unit)}
                after={formatPrecisePrice(
                  convertPriceForUnit(product.currentPrice, factor),
                  draft.newUnit
                )}
              />
              {product.initialStockQuantity !== null ? (
                <PreviewRow
                  label="Estoque inicial"
                  before={formatQuantity(product.initialStockQuantity, product.unit)}
                  after={formatQuantity(
                    convertQuantityForUnit(product.initialStockQuantity, factor),
                    draft.newUnit
                  )}
                />
              ) : null}
              {product.initialPrice !== null ? (
                <PreviewRow
                  label="Preço inicial"
                  before={formatPrecisePrice(product.initialPrice, product.unit)}
                  after={formatPrecisePrice(
                    convertPriceForUnit(product.initialPrice, factor),
                    draft.newUnit
                  )}
                />
              ) : null}
            </div>

            {product.currentValue !== null ? (
              <div className="mt-4 rounded-xl bg-zinc-50 px-4 py-3 text-sm text-zinc-700">
                Valor atual preservado: <strong>{formatMoney(product.currentValue)}</strong>.
                Quantidade e preço mudam de escala, mas o valor financeiro permanece equivalente.
              </div>
            ) : null}

            <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs leading-5 text-amber-900">
              O banco mantém uma cópia de segurança dos valores anteriores antes da conversão.
              Esta ação atualiza retroativamente todo o histórico do Produto em uma única transação.
            </div>

            {actionError ? <ErrorBox message={actionError} /> : null}

            <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button
                variant="ghost"
                disabled={conversionMutation.isPending}
                onClick={() => {
                  setActionError(null);
                  setStage("setup");
                }}
              >
                Voltar
              </Button>
              <Button
                autoFocus
                isLoading={conversionMutation.isPending}
                loadingLabel="Convertendo…"
                onClick={() => void confirmConversion()}
              >
                Confirmar conversão
              </Button>
            </div>
          </>
        ) : null}
      </Card>
    </div>
  );
}

function PreviewRow({
  label,
  before,
  after
}: {
  label: string;
  before: string;
  after: string;
}) {
  return (
    <div className="rounded-xl border border-zinc-200 bg-white px-4 py-3 text-sm">
      <p className="font-medium text-zinc-800">{label}</p>
      <p className="mt-1 break-words text-zinc-500">
        {before} <span aria-hidden="true">→</span>{" "}
        <strong className="text-zinc-950">{after}</strong>
      </p>
    </div>
  );
}

function ErrorBox({ message }: { message: string }) {
  return (
    <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
      {message}
    </div>
  );
}

function formatQuantity(value: number | null, unit: string) {
  if (value === null) return "Sem dados";
  return `${formatDecimal(value)} ${unit}`;
}

function formatPrecisePrice(value: number | null, unit: string) {
  if (value === null) return "Sem preço";
  return `${new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 8
  }).format(value)} / ${unit}`;
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL"
  }).format(value);
}

function formatDecimal(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    maximumFractionDigits: 8
  }).format(value);
}

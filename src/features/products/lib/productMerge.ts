import type { ProductDetails } from "../api/products";
import type { ProductUnit } from "./productValidation";

export type MergePair = {
  survivor: ProductDetails;
  absorbed: ProductDetails;
};

export function determineMergePair(a: ProductDetails, b: ProductDetails): MergePair {
  const aIsOlder =
    a.createdAt < b.createdAt ||
    (a.createdAt === b.createdAt && a.id <= b.id);

  return aIsOlder ? { survivor: a, absorbed: b } : { survivor: b, absorbed: a };
}

export function calculateMergeUnitFactors(
  survivorUnit: string,
  absorbedUnit: string,
  finalUnit: ProductUnit,
  survivorEquivalentQuantity: number | null,
  absorbedEquivalentQuantity: number | null
) {
  if (finalUnit !== survivorUnit && finalUnit !== absorbedUnit) {
    throw new Error("A unidade final deve ser uma das unidades atuais.");
  }

  if (survivorUnit === absorbedUnit) {
    return { survivorFactor: 1, absorbedFactor: 1 };
  }

  if (
    survivorEquivalentQuantity === null ||
    survivorEquivalentQuantity <= 0 ||
    absorbedEquivalentQuantity === null ||
    absorbedEquivalentQuantity <= 0
  ) {
    throw new Error("Informe a equivalência entre as duas unidades.");
  }

  return finalUnit === survivorUnit
    ? {
        survivorFactor: 1,
        absorbedFactor: survivorEquivalentQuantity / absorbedEquivalentQuantity
      }
    : {
        survivorFactor: absorbedEquivalentQuantity / survivorEquivalentQuantity,
        absorbedFactor: 1
      };
}

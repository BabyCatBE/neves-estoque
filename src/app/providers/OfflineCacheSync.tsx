import { useEffect, useRef } from "react";
import { useAuth } from "../../features/auth/context/AuthContext";
import { listActiveCategories } from "../../features/categories/api/categories";
import {
  listActiveProducts,
  listProductCategories
} from "../../features/products/api/products";
import { listCurrentStock } from "../../features/stock/api/stock";
import { listActiveSuppliers } from "../../features/suppliers/api/suppliers";
import { useNetworkStatus } from "../../shared/offline/NetworkContext";

export function OfflineCacheSync() {
  const { status } = useAuth();
  const { isOnline } = useNetworkStatus();
  const runningRef = useRef(false);

  useEffect(() => {
    if (status !== "ready" || !isOnline || runningRef.current) return;

    runningRef.current = true;

    void Promise.allSettled([
      listActiveCategories(),
      listProductCategories(),
      listActiveProducts(),
      listActiveSuppliers(),
      listCurrentStock()
    ]).finally(() => {
      runningRef.current = false;
    });
  }, [isOnline, status]);

  return null;
}

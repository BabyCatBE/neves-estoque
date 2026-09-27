import { useCallback, useEffect, useState } from "react";
import type { OfflinePendingOperationRecord } from "../../../shared/offline/offlineDb";
import {
  listOfflinePendingOperations,
  OFFLINE_PENDING_UPDATED_EVENT
} from "../lib/pendingOperations";

export function useOfflinePendingOperations() {
  const [items, setItems] = useState<OfflinePendingOperationRecord[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setItems(await listOfflinePendingOperations());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const handleUpdated = () => void refresh();
    window.addEventListener(OFFLINE_PENDING_UPDATED_EVENT, handleUpdated);
    return () => window.removeEventListener(OFFLINE_PENDING_UPDATED_EVENT, handleUpdated);
  }, [refresh]);

  return { items, loading, refresh };
}

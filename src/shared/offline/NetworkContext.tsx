import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren
} from "react";
import {
  browserIsOnline,
  getLatestOfflineCacheUpdatedAt,
  OFFLINE_CACHE_UPDATED_EVENT
} from "./offlineCache";

type NetworkContextValue = {
  isOnline: boolean;
  lastCacheUpdatedAt: string | null;
};

const NetworkContext = createContext<NetworkContextValue | null>(null);

export function NetworkProvider({ children }: PropsWithChildren) {
  const [isOnline, setIsOnline] = useState(browserIsOnline);
  const [lastCacheUpdatedAt, setLastCacheUpdatedAt] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    void getLatestOfflineCacheUpdatedAt().then((value) => {
      if (active) setLastCacheUpdatedAt(value);
    });

    const refreshOnlineState = () => setIsOnline(browserIsOnline());
    const refreshCacheTimestamp = (event: Event) => {
      const detail = (event as CustomEvent<{ updatedAt?: string }>).detail;

      if (detail?.updatedAt) {
        setLastCacheUpdatedAt(detail.updatedAt);
        return;
      }

      void getLatestOfflineCacheUpdatedAt().then((value) => {
        if (active) setLastCacheUpdatedAt(value);
      });
    };

    window.addEventListener("online", refreshOnlineState);
    window.addEventListener("offline", refreshOnlineState);
    window.addEventListener(OFFLINE_CACHE_UPDATED_EVENT, refreshCacheTimestamp);

    return () => {
      active = false;
      window.removeEventListener("online", refreshOnlineState);
      window.removeEventListener("offline", refreshOnlineState);
      window.removeEventListener(OFFLINE_CACHE_UPDATED_EVENT, refreshCacheTimestamp);
    };
  }, []);

  const value = useMemo(
    () => ({ isOnline, lastCacheUpdatedAt }),
    [isOnline, lastCacheUpdatedAt]
  );

  return <NetworkContext.Provider value={value}>{children}</NetworkContext.Provider>;
}

export function useNetworkStatus() {
  const value = useContext(NetworkContext);
  if (!value) throw new Error("useNetworkStatus precisa estar dentro de NetworkProvider.");
  return value;
}

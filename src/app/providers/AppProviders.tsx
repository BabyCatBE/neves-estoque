import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type PropsWithChildren } from "react";
import { AuthProvider } from "../../features/auth/context/AuthContext";
import { useKeepFocusedFieldVisible } from "../../shared/hooks/useKeepFocusedFieldVisible";
import { NetworkProvider } from "../../shared/offline/NetworkContext";
import { OfflineCacheSync } from "./OfflineCacheSync";

export function AppProviders({ children }: PropsWithChildren) {
  useKeepFocusedFieldVisible();

  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: 1,
            networkMode: "always"
          }
        }
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <NetworkProvider>
        <AuthProvider>
          <OfflineCacheSync />
          {children}
        </AuthProvider>
      </NetworkProvider>
    </QueryClientProvider>
  );
}

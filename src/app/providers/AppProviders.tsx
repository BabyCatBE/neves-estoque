import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type PropsWithChildren } from "react";
import { AuthProvider } from "../../features/auth/context/AuthContext";
import { useKeepFocusedFieldVisible } from "../../shared/hooks/useKeepFocusedFieldVisible";

export function AppProviders({ children }: PropsWithChildren) {
  useKeepFocusedFieldVisible();

  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            retry: 1
          }
        }
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}

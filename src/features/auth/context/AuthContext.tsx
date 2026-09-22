import type { Session } from "@supabase/supabase-js";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PropsWithChildren
} from "react";
import { supabase } from "../../../shared/lib/supabase";
import { getOrCreateDeviceKey, inferFriendlyDeviceName } from "../lib/deviceIdentity";

export type AuthStatus =
  | "loading"
  | "signed-out"
  | "ready"
  | "unauthorized"
  | "device-blocked"
  | "config-missing";

type FailureStatus = Extract<AuthStatus, "unauthorized" | "device-blocked">;

type AuthContextValue = {
  status: AuthStatus;
  session: Session | null;
  appUserId: string | null;
  roleName: string | null;
  deviceId: string | null;
  errorMessage: string | null;
  signInWithGoogle: () => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<Session | null>(null);
  const [initialized, setInitialized] = useState(false);
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [appUserId, setAppUserId] = useState<string | null>(null);
  const [roleName, setRoleName] = useState<string | null>(null);
  const [deviceId, setDeviceId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const failureStatus = useRef<FailureStatus | null>(null);

  useEffect(() => {
    const client = supabase;
    if (!client) {
      setStatus("config-missing");
      setInitialized(true);
      return;
    }

    let active = true;

    void client.auth.getSession().then(({ data, error }) => {
      if (!active) return;

      if (error) {
        setErrorMessage("Não foi possível recuperar a sessão.");
        setSession(null);
      } else {
        setSession(data.session);
      }

      setInitialized(true);
    });

    const {
      data: { subscription }
    } = client.auth.onAuthStateChange((_event, nextSession) => {
      if (!active) return;
      setSession(nextSession);
      setInitialized(true);
    });

    return () => {
      active = false;
      subscription.unsubscribe();
    };
  }, []);

  useEffect(() => {
    if (!initialized) return;

    const client = supabase;
    if (!client) {
      setStatus("config-missing");
      return;
    }

    if (!session) {
      setAppUserId(null);
      setRoleName(null);
      setDeviceId(null);
      setStatus(failureStatus.current ?? "signed-out");
      return;
    }

    const activeClient = client;
    let cancelled = false;

    async function bootstrapAccess() {
      setStatus("loading");
      setErrorMessage(null);

      const { data: accessRows, error: accessError } = await activeClient.rpc("claim_app_access");
      if (cancelled) return;

      const access = accessRows?.[0];
      if (accessError || !access) {
        failureStatus.current = "unauthorized";
        setErrorMessage("Esta conta Google não está autorizada a acessar o Neves Estoque.");
        await activeClient.auth.signOut({ scope: "local" });
        if (!cancelled) setStatus("unauthorized");
        return;
      }

      let deviceKey: string;
      try {
        deviceKey = getOrCreateDeviceKey(window.localStorage);
      } catch {
        failureStatus.current = "device-blocked";
        setErrorMessage("Não foi possível identificar este dispositivo com segurança.");
        await activeClient.auth.signOut({ scope: "local" });
        if (!cancelled) setStatus("device-blocked");
        return;
      }

      const friendlyName = inferFriendlyDeviceName(window.navigator.userAgent);
      const { data: deviceRows, error: deviceError } = await activeClient.rpc("register_device", {
        p_device_key: deviceKey,
        p_friendly_name: friendlyName
      });
      if (cancelled) return;

      const device = deviceRows?.[0];
      if (deviceError || !device || !device.is_allowed) {
        failureStatus.current = "device-blocked";
        setErrorMessage(
          deviceError
            ? "Não foi possível registrar este dispositivo."
            : "Este dispositivo está bloqueado para o Neves Estoque."
        );
        await activeClient.auth.signOut({ scope: "local" });
        if (!cancelled) setStatus("device-blocked");
        return;
      }

      failureStatus.current = null;
      setAppUserId(access.app_user_id);
      setRoleName(access.role_name);
      setDeviceId(device.device_id);
      setStatus("ready");
    }

    void bootstrapAccess();

    return () => {
      cancelled = true;
    };
  }, [initialized, session]);

  const signInWithGoogle = useCallback(async () => {
    const client = supabase;
    if (!client) {
      setStatus("config-missing");
      return;
    }

    failureStatus.current = null;
    setErrorMessage(null);
    setStatus("loading");

    const redirectTo = new URL("/auth/callback", window.location.origin).toString();
    const { error } = await client.auth.signInWithOAuth({
      provider: "google",
      options: {
        redirectTo,
        queryParams: { prompt: "select_account" }
      }
    });

    if (error) {
      setErrorMessage("Não foi possível iniciar o login com Google.");
      setStatus("signed-out");
    }
  }, []);

  const signOut = useCallback(async () => {
    const client = supabase;
    failureStatus.current = null;
    setErrorMessage(null);

    if (!client) {
      setStatus("config-missing");
      return;
    }

    await activeClient.auth.signOut({ scope: "local" });
    setStatus("signed-out");
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      session,
      appUserId,
      roleName,
      deviceId,
      errorMessage,
      signInWithGoogle,
      signOut
    }),
    [appUserId, deviceId, errorMessage, roleName, session, signInWithGoogle, signOut, status]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth precisa estar dentro de AuthProvider.");
  return value;
}

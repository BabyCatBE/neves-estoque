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
import {
  clearRegisteredDeviceId,
  getOrCreateDeviceKey,
  inferFriendlyDeviceName,
  setRegisteredDeviceId
} from "../lib/deviceIdentity";
import {
  isValidSecondaryUsername,
  normalizeSecondaryUsername
} from "../lib/secondaryAuth";

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
  displayName: string | null;
  username: string | null;
  authMethod: string | null;
  errorMessage: string | null;
  signInWithGoogle: () => Promise<void>;
  signInWithUsername: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function forgetRegisteredDeviceId() {
  try {
    clearRegisteredDeviceId(window.localStorage);
  } catch {
    // O encerramento da sessão deve continuar mesmo se o storage local estiver indisponível.
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<Session | null>(null);
  const [initialized, setInitialized] = useState(() => !supabase);
  const [status, setStatus] = useState<AuthStatus>(() =>
    supabase ? "loading" : "config-missing"
  );
  const [appUserId, setAppUserId] = useState<string | null>(null);
  const [roleName, setRoleName] = useState<string | null>(null);
  const [deviceId, setDeviceId] = useState<string | null>(null);
  const [displayName, setDisplayName] = useState<string | null>(null);
  const [username, setUsername] = useState<string | null>(null);
  const [authMethod, setAuthMethod] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const failureStatus = useRef<FailureStatus | null>(null);

  useEffect(() => {
    const client = supabase;
    if (!client) return;

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
    if (!client) return;

    const activeClient = client;
    let cancelled = false;

    async function bootstrapAccess() {
      await Promise.resolve();
      if (cancelled) return;

      if (!session) {
        setAppUserId(null);
        setRoleName(null);
        setDeviceId(null);
        setDisplayName(null);
        setUsername(null);
        setAuthMethod(null);
        setStatus(failureStatus.current ?? "signed-out");
        return;
      }

      setStatus("loading");
      setErrorMessage(null);

      const { data: accessRows, error: accessError } = await activeClient.rpc("claim_app_access");
      if (cancelled) return;

      const access = accessRows?.[0];
      if (accessError || !access) {
        failureStatus.current = "unauthorized";
        setErrorMessage("Este acesso não está autorizado para o Neves Estoque.");
        forgetRegisteredDeviceId();
        await activeClient.auth.signOut({ scope: "local" });
        if (!cancelled) setStatus("unauthorized");
        return;
      }

      const { data: profile, error: profileError } = await activeClient
        .from("app_users")
        .select("display_name,username,auth_method")
        .eq("auth_user_id", session.user.id)
        .maybeSingle();

      if (profileError || !profile) {
        failureStatus.current = "unauthorized";
        setErrorMessage("Não foi possível validar o perfil deste acesso.");
        forgetRegisteredDeviceId();
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
        forgetRegisteredDeviceId();
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
        forgetRegisteredDeviceId();
        await activeClient.auth.signOut({ scope: "local" });
        if (!cancelled) setStatus("device-blocked");
        return;
      }

      try {
        setRegisteredDeviceId(window.localStorage, device.device_id);
      } catch {
        failureStatus.current = "device-blocked";
        setErrorMessage("Não foi possível concluir o vínculo deste dispositivo com segurança.");
        forgetRegisteredDeviceId();
        await activeClient.auth.signOut({ scope: "local" });
        if (!cancelled) setStatus("device-blocked");
        return;
      }

      failureStatus.current = null;
      setAppUserId(access.app_user_id);
      setRoleName(access.role_name);
      setDeviceId(device.device_id);
      setDisplayName(profile.display_name);
      setUsername(profile.username);
      setAuthMethod(profile.auth_method);
      setStatus("ready");
    }

    void bootstrapAccess();

    return () => {
      cancelled = true;
    };
  }, [initialized, session]);

  const signInWithUsername = useCallback(async (rawUsername: string, password: string) => {
    const client = supabase;
    if (!client) {
      setStatus("config-missing");
      return;
    }

    const normalizedUsername = normalizeSecondaryUsername(rawUsername);
    if (!isValidSecondaryUsername(normalizedUsername) || password.length < 1) {
      setErrorMessage("Informe um usuário e uma senha válidos.");
      setStatus("signed-out");
      return;
    }

    failureStatus.current = null;
    setErrorMessage(null);
    setStatus("loading");

    const { data: loginEmail, error: resolveError } = await client.rpc(
      "resolve_secondary_login",
      { p_username: normalizedUsername }
    );

    if (resolveError || !loginEmail) {
      setErrorMessage("Usuário ou senha incorretos.");
      setStatus("signed-out");
      return;
    }

    const { error } = await client.auth.signInWithPassword({
      email: loginEmail,
      password
    });

    if (error) {
      setErrorMessage("Usuário ou senha incorretos.");
      setStatus("signed-out");
    }
  }, []);

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
    forgetRegisteredDeviceId();

    if (!client) {
      setStatus("config-missing");
      return;
    }

    await client.auth.signOut({ scope: "local" });
    setStatus("signed-out");
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      session,
      appUserId,
      roleName,
      deviceId,
      displayName,
      username,
      authMethod,
      errorMessage,
      signInWithGoogle,
      signInWithUsername,
      signOut
    }),
    [
      appUserId,
      authMethod,
      deviceId,
      displayName,
      errorMessage,
      roleName,
      session,
      signInWithGoogle,
      signInWithUsername,
      signOut,
      status,
      username
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth precisa estar dentro de AuthProvider.");
  return value;
}

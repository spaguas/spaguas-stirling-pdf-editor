import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import {
  communityRequest,
  CommunityApiError,
  type CommunitySession,
} from "@app/communityApi";
import { setCommunityIdentity } from "@app/auth/communityIdentity";

interface SessionContextValue {
  session: CommunitySession | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  changePassword: (
    currentPassword: string,
    newPassword: string,
  ) => Promise<void>;
}
const Context = createContext<SessionContextValue | null>(null);

/** Resolves the cookie session before any document-storage service is loaded. */
export function CommunitySessionProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [session, setSession] = useState<CommunitySession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const next = await communityRequest<CommunitySession>("/session");
      if (next.account) setCommunityIdentity(next.account.id);
      setSession(next);
      setError(null);
    } catch (failure) {
      setError(
        failure instanceof Error
          ? failure.message
          : "Unable to contact the server",
      );
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  useEffect(() => {
    const expired = () => {
      setSession(null);
      window.location.reload();
    };
    window.addEventListener("community-session-expired", expired);
    return () =>
      window.removeEventListener("community-session-expired", expired);
  }, []);
  useEffect(() => {
    const changed = (event: StorageEvent) => {
      if (event.key === "community-session-generation")
        window.location.reload();
    };
    window.addEventListener("storage", changed);
    return () => window.removeEventListener("storage", changed);
  }, []);
  const signIn = async (username: string, password: string) => {
    try {
      await communityRequest<void>("/login", {
        method: "POST",
        form: new URLSearchParams({ username, password }),
      });
    } catch (failure) {
      if (failure instanceof CommunityApiError && failure.status === 401)
        throw new Error("Invalid username or password", { cause: failure });
      throw failure;
    }
    localStorage.setItem("community-session-generation", crypto.randomUUID());
    window.location.reload();
  };
  const signOut = async () => {
    await communityRequest<void>("/logout", { method: "POST" });
    localStorage.setItem("community-session-generation", crypto.randomUUID());
    window.location.reload();
  };
  const changePassword = async (
    currentPassword: string,
    newPassword: string,
  ) => {
    await communityRequest<void>("/account/password", {
      method: "POST",
      body: { currentPassword, newPassword },
    });
    localStorage.setItem("community-session-generation", crypto.randomUUID());
    window.location.reload();
  };
  return (
    <Context.Provider
      value={{
        session,
        loading,
        error,
        refresh,
        signIn,
        signOut,
        changePassword,
      }}
    >
      {children}
    </Context.Provider>
  );
}

export function useCommunitySession(): SessionContextValue {
  const value = useContext(Context);
  if (!value) throw new Error("CommunitySessionProvider is required");
  return value;
}

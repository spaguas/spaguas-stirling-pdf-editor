import { useCommunitySession } from "@app/auth/CommunitySession";

/** Adapts Community identity to the core editor's user-display and workbench-ownership seam. */
export function useAuth() {
  const { session, loading, error, refresh, signOut } = useCommunitySession();
  const account = session?.account ?? null;
  return {
    session: account ? { user: account } : null,
    user: account ? { ...account, email: account.username } : null,
    displayName: account?.username ?? null,
    isAnonymous: false,
    loading,
    error: error ? new Error(error) : null,
    refreshSession: refresh,
    signOut,
    isAdmin: account?.role === "ADMIN",
    portalAccess: false,
  };
}

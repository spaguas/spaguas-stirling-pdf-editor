import { getApiBaseUrl } from "@app/services/apiClientConfig";

export type AccountRole = "ADMIN" | "USER";
export type MemberRole = "OWNER" | "MEMBER";
export interface CommunityAccount {
  id: string;
  username: string;
  role: AccountRole;
  enabled: boolean;
  mustChangePassword: boolean;
}
export interface CommunitySession {
  authenticated: boolean;
  account: CommunityAccount | null;
  canProcessPdf: boolean;
}
export interface CommunityTeam {
  id: string;
  name: string;
  canProcessPdf: boolean;
  membersCanViewDirectory: boolean;
  membership: MemberRole | null;
  memberCount: number;
}
export interface CommunityMember {
  account: CommunityAccount;
  role: MemberRole;
}
export class CommunityApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

/** Cookie sessions use a CSRF header; URLs always use the editor's runtime deployment base. */
export async function communityRequest<T>(
  path: string,
  options: { method?: string; body?: unknown; form?: URLSearchParams } = {},
): Promise<T> {
  const csrfCookie = document.cookie
    .split(";")
    .map((value) => value.trim())
    .find((value) => value.startsWith("XSRF-TOKEN="));
  const csrf = csrfCookie
    ? decodeURIComponent(csrfCookie.substring("XSRF-TOKEN=".length))
    : "";
  const response = await fetch(
    `${getApiBaseUrl().replace(/\/+$/, "")}/api/v1/community${path}`,
    {
      method: options.method ?? "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
        ...(options.body !== undefined
          ? { "Content-Type": "application/json" }
          : {}),
      },
      body:
        options.form ??
        (options.body !== undefined ? JSON.stringify(options.body) : undefined),
    },
  );
  if (!response.ok) {
    let detail = `Request failed (${response.status})`;
    try {
      const data = (await response.json()) as { detail?: string };
      if (typeof data.detail === "string") detail = data.detail;
    } catch {
      /* An upstream may return an HTML error page. */
    }
    if (response.status === 401 && path !== "/login" && path !== "/session")
      window.dispatchEvent(new Event("community-session-expired"));
    throw new CommunityApiError(response.status, detail);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

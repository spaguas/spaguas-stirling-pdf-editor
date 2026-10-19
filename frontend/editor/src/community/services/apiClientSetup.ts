import type { AxiosInstance } from "axios";
import { setupApiInterceptors as setupCoreApiInterceptors } from "@core/services/apiClientSetup";

/** Shared PDF requests use the same session-expiry boundary as Community administration. */
export function setupApiInterceptors(client: AxiosInstance): void {
  setupCoreApiInterceptors(client);
  client.interceptors.response.use(undefined, (error: unknown) => {
    if (typeof error === "object" && error !== null && "response" in error) {
      const response = error.response;
      if (
        typeof response === "object" &&
        response !== null &&
        "status" in response &&
        response.status === 401
      )
        window.dispatchEvent(new Event("community-session-expired"));
    }
    return Promise.reject(error);
  });
}

/** Raw mutations must include the cookie session's CSRF token. */
export async function getAuthHeaders(): Promise<Record<string, string>> {
  const csrf = document.cookie
    .split(";")
    .map((value) => value.trim())
    .find((value) => value.startsWith("XSRF-TOKEN="));
  return csrf
    ? { "X-XSRF-TOKEN": decodeURIComponent(csrf.slice("XSRF-TOKEN=".length)) }
    : {};
}

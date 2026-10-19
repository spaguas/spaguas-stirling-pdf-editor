import { beforeEach, describe, expect, it, vi } from "vitest";
import { communityRequest } from "@app/communityApi";

vi.mock("@app/services/apiClientConfig", () => ({
  getApiBaseUrl: () => "/editor-pdf/",
}));

describe("Community requests", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
  });
  it("preserves the deployment prefix, cookie credentials and CSRF header", async () => {
    const fetcher = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));
    await communityRequest("/teams", {
      method: "POST",
      body: { name: "Operations" },
    });
    expect(fetcher).toHaveBeenCalledWith(
      "/editor-pdf/api/v1/community/teams",
      expect.objectContaining({
        credentials: "same-origin",
        headers: expect.objectContaining({ "X-XSRF-TOKEN": "test-csrf" }),
      }),
    );
  });
  it("expires document sessions on an unauthorized API response, excluding failed logins", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(
      async () =>
        new Response('{"detail":"Authentication required"}', { status: 401 }),
    );
    const expired = vi.fn();
    window.addEventListener("community-session-expired", expired);
    await expect(communityRequest("/teams")).rejects.toThrow(
      "Authentication required",
    );
    expect(expired).toHaveBeenCalledTimes(1);
    await expect(communityRequest("/login")).rejects.toThrow();
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener("community-session-expired", expired);
  });
  it("reports non-JSON proxy errors without attempting to use a successful response", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("<html>Bad gateway</html>", { status: 502 }),
    );
    await expect(communityRequest("/session")).rejects.toThrow(
      "Request failed (502)",
    );
  });
});

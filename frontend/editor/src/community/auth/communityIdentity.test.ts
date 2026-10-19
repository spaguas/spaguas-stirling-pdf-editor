import { describe, expect, it, vi } from "vitest";

describe("account-scoped document databases", () => {
  it("requires identity, keeps names idempotent and refuses an in-place account switch", async () => {
    vi.resetModules();
    const identity = await import("@app/auth/communityIdentity");
    expect(() => identity.communityDatabaseName("files")).toThrow("Resolve");
    identity.setCommunityIdentity("first");
    expect(identity.communityDatabaseName("files")).toBe(
      "files::community::first",
    );
    expect(
      identity.communityDatabaseName(identity.communityDatabaseName("files")),
    ).toBe("files::community::first");
    expect(() => identity.setCommunityIdentity("second")).toThrow("Reload");
    vi.resetModules();
    const next = await import("@app/auth/communityIdentity");
    next.setCommunityIdentity("second");
    expect(next.communityDatabaseName("files")).not.toBe(
      identity.communityDatabaseName("files"),
    );
  });
});

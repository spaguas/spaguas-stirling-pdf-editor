import { describe, expect, it, vi } from "vitest";
import { SignatureStorageService } from "@core/services/signatureStorageService";
import type { SavedSignature } from "@app/types/signature";

describe("Community signature storage", () => {
  it("uses per-account keys and never probes a commercial endpoint", async () => {
    localStorage.clear();
    const fetcher = vi.spyOn(globalThis, "fetch");
    const first = new SignatureStorageService({
      backendEnabled: false,
      storageKey: () => "signatures::first",
    });
    const second = new SignatureStorageService({
      backendEnabled: false,
      storageKey: () => "signatures::second",
    });
    const signature: SavedSignature = {
      id: "sample",
      label: "Private",
      scope: "localStorage",
      createdAt: 1,
      updatedAt: 1,
      type: "canvas",
      dataUrl: "data:image/png;base64,c2ln",
    };
    await first.saveSignature(signature);
    expect(await first.loadSignatures()).toHaveLength(1);
    expect(await second.loadSignatures()).toEqual([]);
    expect(await second.getStorageType()).toBe("localStorage");
    expect(fetcher).not.toHaveBeenCalled();
    fetcher.mockRestore();
  });
});

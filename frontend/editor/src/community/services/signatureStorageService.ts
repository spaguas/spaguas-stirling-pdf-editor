import { SignatureStorageService } from "@core/services/signatureStorageService";
import { communityDatabaseName } from "@app/auth/communityIdentity";
export type { StorageType } from "@core/services/signatureStorageService";

/** Saved signatures belong to the authenticated account, with no commercial backend probe. */
export const signatureStorageService = new SignatureStorageService({
  backendEnabled: false,
  storageKey: () => communityDatabaseName("stirling:saved-signatures:v1"),
});

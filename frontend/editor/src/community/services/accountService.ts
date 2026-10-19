import type {
  accountService as CoreAccountService,
  AccountData,
  LoginPageData,
} from "@core/services/accountService";
import { communityRequest, type CommunitySession } from "@app/communityApi";

export type { AccountData, LoginPageData };

async function unsupported(): Promise<never> {
  throw new Error("This account operation is unavailable in Community");
}
async function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await communityRequest("/account/password", {
    method: "POST",
    body: { currentPassword, newPassword },
  });
  window.location.reload();
}

/** Supplies the shared editor's account contract from Community, without commercial endpoints. */
export const accountService = {
  async getAccountData(): Promise<AccountData> {
    const { account } = await communityRequest<CommunitySession>("/session");
    if (!account) throw new Error("Authentication required");
    return {
      username: account.username,
      role: `ROLE_${account.role}`,
      settings: "{}",
      changeCredsFlag: account.mustChangePassword,
      oAuth2Login: false,
      saml2Login: false,
      mfaEnabled: false,
      mfaRequired: false,
    };
  },
  async getLoginPageData(): Promise<LoginPageData> {
    return {
      enableLogin: true,
      firstTimeSetup: false,
      showDefaultCredentials: false,
    };
  },
  changePassword,
  changePasswordOnLogin: changePassword,
  changeUsername: unsupported,
  requestMfaSetup: unsupported,
  enableMfa: unsupported,
  disableMfa: unsupported,
  cancelMfaSetup: unsupported,
} satisfies typeof CoreAccountService;

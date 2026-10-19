import { useTranslation } from "react-i18next";
import { useSettingsNav as useCoreSettingsNav } from "@core/components/settings/useSettingsNav";
import type { SettingsNav } from "@app/components/settings/settingsNavTypes";
import { useCommunitySession } from "@app/auth/CommunitySession";
import { CommunityUsers } from "@app/components/community/CommunityUsers";
import { CommunityTeams } from "@app/components/community/CommunityTeams";
import { CommunityAccountSettings } from "@app/components/community/CommunityAccountSettings";

/** Adds Community administration to the MIT settings navigation; backend authorization remains authoritative. */
export function useSettingsNav(onLeave: () => void): SettingsNav {
  const base = useCoreSettingsNav(onLeave);
  const { t } = useTranslation();
  const { session } = useCommunitySession();
  return {
    ...base,
    sections: [
      ...base.sections,
      {
        id: "community",
        title: "Community",
        items: [
          {
            key: "security",
            label: t("community.account", "Account"),
            icon: "shield",
            component: <CommunityAccountSettings />,
          },
          {
            key: "teams",
            label: t("community.teams", "Teams"),
            icon: "users",
            component: <CommunityTeams />,
          },
          ...(session?.account?.role === "ADMIN"
            ? [
                {
                  key: "users" as const,
                  label: t("community.users", "Users"),
                  icon: "user" as const,
                  component: <CommunityUsers />,
                },
              ]
            : []),
        ],
      },
    ],
  };
}

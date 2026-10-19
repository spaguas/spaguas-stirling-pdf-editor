import type { ReactNode } from "react";
import { Group, Text } from "@mantine/core";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Button } from "@app/ui/Button";
import { useCommunitySession } from "@app/auth/CommunitySession";

/** Keeps account actions reachable in every editor view without pulling in the commercial portal. */
export function TitleBarChrome({ children }: { children: ReactNode }) {
  const { session, signOut } = useCommunitySession();
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <div className="community-shell">
      <header className="community-titlebar">
        <Text fw={600}>Stirling PDF Community</Text>
        <Group gap="xs">
          <Text size="sm">{session?.account?.username}</Text>
          <Button variant="quiet" onClick={() => navigate("/settings/teams")}>
            {t("community.teams", "Teams")}
          </Button>
          <Button variant="quiet" onClick={() => void signOut()}>
            {t("community.signOut", "Sign out")}
          </Button>
        </Group>
      </header>
      {children}
    </div>
  );
}

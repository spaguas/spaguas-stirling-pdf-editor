import { lazy, Suspense } from "react";
import { PreferencesProvider } from "@app/contexts/PreferencesContext";
import { ThemeProvider } from "@app/components/shared/ThemeProvider";
import {
  CommunitySessionProvider,
  useCommunitySession,
} from "@app/auth/CommunitySession";
import { CommunitySignIn } from "@app/components/community/CommunitySignIn";
import { Loader, Center, Text, Stack } from "@mantine/core";
import { Button } from "@app/ui/Button";
import { useTranslation } from "react-i18next";
import "@app/styles/community.css";

const Editor = lazy(() => import("@core/App"));

function CommunityGate() {
  const { session, loading, error, refresh } = useCommunitySession();
  const { t } = useTranslation();
  if (loading)
    return (
      <Center mih="100dvh">
        <Loader aria-label={t("community.loading", "Loading session")} />
      </Center>
    );
  if (error)
    return (
      <Center mih="100dvh">
        <Stack>
          <Text role="alert">{error}</Text>
          <Button onClick={() => void refresh()}>
            {t("common.retry", "Retry")}
          </Button>
        </Stack>
      </Center>
    );
  if (!session?.account || session.account.mustChangePassword)
    return <CommunitySignIn />;
  return (
    <Suspense
      fallback={
        <Center mih="100dvh">
          <Loader />
        </Center>
      }
    >
      <Editor />
    </Suspense>
  );
}

/** Community imports only its own identity layer and the MIT editor, after resolving account-scoped storage. */
export default function App() {
  return (
    <CommunitySessionProvider>
      <PreferencesProvider>
        <ThemeProvider>
          <CommunityGate />
        </ThemeProvider>
      </PreferencesProvider>
    </CommunitySessionProvider>
  );
}

import { useState, type FormEvent } from "react";
import {
  Paper,
  Stack,
  TextInput,
  PasswordInput,
  Title,
  Text,
  Alert,
} from "@mantine/core";
import { useTranslation } from "react-i18next";
import { Button } from "@app/ui/Button";
import { useCommunitySession } from "@app/auth/CommunitySession";

/** Local-password entry and the compulsory first-login password change use the same cookie/CSRF session. */
export function CommunitySignIn() {
  const { t } = useTranslation();
  const { session, signIn, signOut, changePassword } = useCommunitySession();
  const forced = session?.account?.mustChangePassword === true;
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [nextPassword, setNextPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (forced && nextPassword !== confirmation) {
      setError(t("community.passwordMismatch", "Passwords do not match"));
      return;
    }
    setBusy(true);
    try {
      if (forced) await changePassword(password, nextPassword);
      else await signIn(username, password);
    } catch (failure) {
      setError(
        failure instanceof Error
          ? failure.message
          : t("community.requestFailed", "The request could not be completed"),
      );
      setBusy(false);
    }
  }
  return (
    <main className="community-auth">
      <Paper className="community-auth-card" withBorder>
        <form onSubmit={(event) => void submit(event)}>
          <Stack>
            <Text size="sm" fw={600}>
              Stirling PDF Community
            </Text>
            <Title order={1} size="h2">
              {forced
                ? t("community.changePassword", "Change your password")
                : t("community.signIn", "Sign in")}
            </Title>
            <Text c="dimmed">
              {forced
                ? t(
                    "community.firstPassword",
                    "Set a new password before accessing the editor. Use at least 12 characters.",
                  )
                : t(
                    "community.localAccounts",
                    "Use your local account to access your teams and PDF tools.",
                  )}
            </Text>
            {error ? (
              <Alert color="var(--c-danger)" role="alert">
                {error}
              </Alert>
            ) : null}
            {forced ? (
              <Text fw={500}>{session?.account?.username}</Text>
            ) : (
              <TextInput
                label={t("community.username", "Username")}
                value={username}
                onChange={(event) => setUsername(event.currentTarget.value)}
                autoComplete="username"
                required
                autoFocus
              />
            )}
            <PasswordInput
              label={
                forced
                  ? t("community.currentPassword", "Current password")
                  : t("community.password", "Password")
              }
              value={password}
              onChange={(event) => setPassword(event.currentTarget.value)}
              autoComplete="current-password"
              required
            />
            {forced ? (
              <>
                <PasswordInput
                  label={t("community.newPassword", "New password")}
                  value={nextPassword}
                  onChange={(event) =>
                    setNextPassword(event.currentTarget.value)
                  }
                  autoComplete="new-password"
                  minLength={12}
                  required
                />
                <PasswordInput
                  label={t("community.confirmPassword", "Confirm new password")}
                  value={confirmation}
                  onChange={(event) =>
                    setConfirmation(event.currentTarget.value)
                  }
                  autoComplete="new-password"
                  required
                />
              </>
            ) : null}
            <Button type="submit" loading={busy}>
              {forced
                ? t("community.savePassword", "Save password and sign in again")
                : t("community.signIn", "Sign in")}
            </Button>
            {forced ? (
              <Button variant="quiet" onClick={() => void signOut()}>
                {t("community.signOut", "Sign out")}
              </Button>
            ) : null}
          </Stack>
        </form>
      </Paper>
    </main>
  );
}

import { useState, type FormEvent } from "react";
import { Stack, PasswordInput, Alert, Text } from "@mantine/core";
import { useTranslation } from "react-i18next";
import { Button } from "@app/ui/Button";
import { useCommunitySession } from "@app/auth/CommunitySession";

/** Password changes invalidate existing sessions; the next login uses the new credential. */
export function CommunityAccountSettings() {
  const { t } = useTranslation();
  const { session, changePassword } = useCommunitySession();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (next !== confirmation) {
      setError(t("community.passwordMismatch", "Passwords do not match"));
      return;
    }
    setBusy(true);
    try {
      await changePassword(current, next);
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
    <form onSubmit={(event) => void submit(event)}>
      <Stack maw={500}>
        <Text fw={600}>{session?.account?.username}</Text>
        {error ? (
          <Alert color="var(--c-danger)" role="alert">
            {error}
          </Alert>
        ) : null}
        <PasswordInput
          label={t("community.currentPassword", "Current password")}
          value={current}
          onChange={(event) => setCurrent(event.currentTarget.value)}
          autoComplete="current-password"
          required
        />
        <PasswordInput
          label={t("community.newPassword", "New password")}
          value={next}
          onChange={(event) => setNext(event.currentTarget.value)}
          autoComplete="new-password"
          minLength={12}
          required
        />
        <PasswordInput
          label={t("community.confirmPassword", "Confirm new password")}
          value={confirmation}
          onChange={(event) => setConfirmation(event.currentTarget.value)}
          autoComplete="new-password"
          required
        />
        <Button type="submit" loading={busy}>
          {t("community.savePassword", "Save password and sign in again")}
        </Button>
      </Stack>
    </form>
  );
}

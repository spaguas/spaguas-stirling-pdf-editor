import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Stack,
  Group,
  TextInput,
  PasswordInput,
  Select,
  Switch,
  Alert,
  Text,
  Modal,
  Loader,
} from "@mantine/core";
import { useTranslation } from "react-i18next";
import { Button } from "@app/ui/Button";
import {
  communityRequest,
  type CommunityAccount,
  type CommunityTeam,
  type AccountRole,
} from "@app/communityApi";
import { useCommunityAction } from "@app/components/community/useCommunityAction";

/** Server administrators create accounts and change global roles; no seat count or commercial entitlement is consulted. */
export function CommunityUsers() {
  const { t } = useTranslation();
  const { run, busy, error } = useCommunityAction();
  const users = useQuery({
    queryKey: ["community", "users"],
    queryFn: () => communityRequest<CommunityAccount[]>("/users"),
  });
  const teams = useQuery({
    queryKey: ["community", "teams"],
    queryFn: () => communityRequest<CommunityTeam[]>("/teams"),
  });
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<AccountRole>("USER");
  const [team, setTeam] = useState<string | null>(null);
  const [reset, setReset] = useState<CommunityAccount | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const roles = [
    { value: "USER", label: t("community.member", "User") },
    { value: "ADMIN", label: t("community.administrator", "Administrator") },
  ];
  async function create(event: FormEvent) {
    event.preventDefault();
    if (
      await run(() =>
        communityRequest("/users", {
          method: "POST",
          body: { username, password, role, teamId: team },
        }),
      )
    ) {
      setUsername("");
      setPassword("");
    }
  }
  const update = (
    account: CommunityAccount,
    nextRole: AccountRole,
    enabled: boolean,
  ) =>
    run(() =>
      communityRequest(`/users/${account.id}`, {
        method: "PUT",
        body: { role: nextRole, enabled },
      }),
    );
  return (
    <Stack>
      <Text c="dimmed">
        {t(
          "community.usersDescription",
          "Manage local accounts and global administration. New accounts must change their initial password.",
        )}
      </Text>
      {error ? (
        <Alert color="var(--c-danger)" role="alert">
          {error}
        </Alert>
      ) : null}
      <form onSubmit={(event) => void create(event)}>
        <Group align="end" wrap="wrap">
          <TextInput
            label={t("community.username", "Username")}
            value={username}
            onChange={(event) => setUsername(event.currentTarget.value)}
            minLength={3}
            maxLength={64}
            required
            autoComplete="off"
          />
          <PasswordInput
            label={t("community.initialPassword", "Initial password")}
            value={password}
            onChange={(event) => setPassword(event.currentTarget.value)}
            minLength={12}
            required
            autoComplete="new-password"
          />
          <Select
            label={t("community.role", "Role")}
            value={role}
            data={roles}
            onChange={(value) => {
              if (value === "USER" || value === "ADMIN") setRole(value);
            }}
            allowDeselect={false}
          />
          <Select
            label={t("community.initialTeam", "Initial team")}
            placeholder={t("community.noTeam", "No processing team")}
            value={team}
            onChange={setTeam}
            data={(teams.data ?? []).map((value) => ({
              value: value.id,
              label: value.name,
            }))}
            clearable
          />
          <Button type="submit" loading={busy}>
            {t("community.createUser", "Create user")}
          </Button>
        </Group>
      </form>
      {users.isLoading ? (
        <Loader />
      ) : users.isError ? (
        <Alert color="var(--c-danger)" role="alert">
          {users.error.message}
        </Alert>
      ) : (
        <div className="community-table-wrap">
          <table className="community-table">
            <thead>
              <tr>
                <th>{t("community.username", "Username")}</th>
                <th>{t("community.role", "Role")}</th>
                <th>{t("community.enabled", "Enabled")}</th>
                <th>{t("community.actions", "Actions")}</th>
              </tr>
            </thead>
            <tbody>
              {users.data?.map((account) => (
                <tr key={account.id}>
                  <td>{account.username}</td>
                  <td>
                    <Select
                      aria-label={`${t("community.role", "Role")} ${account.username}`}
                      value={account.role}
                      data={roles}
                      disabled={busy}
                      allowDeselect={false}
                      onChange={(value) => {
                        if (value === "USER" || value === "ADMIN")
                          void update(account, value, account.enabled);
                      }}
                    />
                  </td>
                  <td>
                    <Switch
                      aria-label={`${t("community.enabled", "Enabled")} ${account.username}`}
                      checked={account.enabled}
                      disabled={busy}
                      onChange={(event) =>
                        void update(
                          account,
                          account.role,
                          event.currentTarget.checked,
                        )
                      }
                    />
                  </td>
                  <td>
                    <Group gap="xs">
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => {
                          setReset(account);
                          setResetPassword("");
                        }}
                      >
                        {t("community.resetPassword", "Reset password")}
                      </Button>
                      <Button
                        variant="quiet"
                        accent="danger"
                        disabled={busy}
                        onClick={() => {
                          if (
                            window.confirm(
                              t(
                                "community.deleteUserConfirm",
                                "Delete this account and its team memberships?",
                              ),
                            )
                          )
                            void run(() =>
                              communityRequest(`/users/${account.id}`, {
                                method: "DELETE",
                              }),
                            );
                        }}
                      >
                        {t("community.delete", "Delete")}
                      </Button>
                    </Group>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Modal
        opened={reset !== null}
        onClose={() => setReset(null)}
        title={t("community.resetPassword", "Reset password")}
      >
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (reset)
              void run(() =>
                communityRequest(`/users/${reset.id}/password`, {
                  method: "POST",
                  body: { password: resetPassword },
                }),
              ).then((ok) => {
                if (ok) setReset(null);
              });
          }}
        >
          <Stack>
            <Text>{reset?.username}</Text>
            {error ? (
              <Alert color="var(--c-danger)" role="alert">
                {error}
              </Alert>
            ) : null}
            <PasswordInput
              label={t("community.newPassword", "New password")}
              value={resetPassword}
              onChange={(event) => setResetPassword(event.currentTarget.value)}
              minLength={12}
              required
              autoComplete="new-password"
            />
            <Button type="submit" loading={busy}>
              {t("community.resetPassword", "Reset password")}
            </Button>
          </Stack>
        </form>
      </Modal>
    </Stack>
  );
}

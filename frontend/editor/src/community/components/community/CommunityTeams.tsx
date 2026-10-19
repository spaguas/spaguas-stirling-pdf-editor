import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Stack,
  Group,
  TextInput,
  Select,
  Switch,
  Alert,
  Text,
  Paper,
  Loader,
} from "@mantine/core";
import { useTranslation } from "react-i18next";
import { Button } from "@app/ui/Button";
import {
  communityRequest,
  type CommunityTeam,
  type CommunityMember,
  type MemberRole,
} from "@app/communityApi";
import { useCommunitySession } from "@app/auth/CommunitySession";
import { useCommunityAction } from "@app/components/community/useCommunityAction";

export function CommunityTeams() {
  const { t } = useTranslation();
  const { session } = useCommunitySession();
  const admin = session?.account?.role === "ADMIN";
  const { run, busy, error } = useCommunityAction();
  const query = useQuery({
    queryKey: ["community", "teams"],
    queryFn: () => communityRequest<CommunityTeam[]>("/teams"),
  });
  const [name, setName] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const team = query.data?.find((value) => value.id === selected);
  async function create(event: FormEvent) {
    event.preventDefault();
    if (
      await run(() =>
        communityRequest("/teams", {
          method: "POST",
          body: { name, canProcessPdf: true, membersCanViewDirectory: true },
        }),
      )
    )
      setName("");
  }
  return (
    <Stack>
      <Text c="dimmed">
        {t(
          "community.teamsDescription",
          "Team owners manage membership. Administrators control PDF-processing permissions and directory visibility.",
        )}
      </Text>
      {error ? (
        <Alert color="var(--c-danger)" role="alert">
          {error}
        </Alert>
      ) : null}
      {admin ? (
        <form onSubmit={(event) => void create(event)}>
          <Group align="end">
            <TextInput
              label={t("community.teamName", "Team name")}
              value={name}
              onChange={(event) => setName(event.currentTarget.value)}
              maxLength={100}
              required
            />
            <Button type="submit" loading={busy}>
              {t("community.createTeam", "Create team")}
            </Button>
          </Group>
        </form>
      ) : null}
      {query.isLoading ? (
        <Loader />
      ) : query.isError ? (
        <Alert color="var(--c-danger)" role="alert">
          {query.error.message}
        </Alert>
      ) : (
        <Select
          label={t("community.teams", "Teams")}
          value={selected}
          onChange={setSelected}
          data={(query.data ?? []).map((value) => ({
            value: value.id,
            label: `${value.name} (${value.memberCount})`,
          }))}
          placeholder={t("community.selectTeam", "Select a team")}
        />
      )}
      {query.data?.length === 0 ? (
        <Text>
          {t(
            "community.noTeams",
            "You have no team memberships. Contact an administrator to receive PDF-processing access.",
          )}
        </Text>
      ) : null}
      {team ? (
        <CommunityTeamDetails
          key={`${team.id}:${team.name}:${team.canProcessPdf}:${team.membersCanViewDirectory}`}
          team={team}
          admin={admin}
          onDeleted={() => setSelected(null)}
        />
      ) : null}
    </Stack>
  );
}

function CommunityTeamDetails({
  team,
  admin,
  onDeleted,
}: {
  team: CommunityTeam;
  admin: boolean;
  onDeleted: () => void;
}) {
  const { t } = useTranslation();
  const { run, busy, error } = useCommunityAction();
  const [name, setName] = useState(team.name);
  const [processing, setProcessing] = useState(team.canProcessPdf);
  const [directory, setDirectory] = useState(team.membersCanViewDirectory);
  const [username, setUsername] = useState("");
  const [role, setRole] = useState<MemberRole>("MEMBER");
  const manager = admin || team.membership === "OWNER";
  const canView = manager || team.membersCanViewDirectory;
  const members = useQuery({
    queryKey: ["community", "members", team.id],
    queryFn: () =>
      communityRequest<CommunityMember[]>(`/teams/${team.id}/members`),
    enabled: canView,
  });
  const roles = [
    { value: "MEMBER", label: t("community.member", "User") },
    { value: "OWNER", label: t("community.teamOwner", "Team owner") },
  ];
  async function save(event: FormEvent) {
    event.preventDefault();
    await run(() =>
      communityRequest(`/teams/${team.id}`, {
        method: "PUT",
        body: {
          name,
          canProcessPdf: processing,
          membersCanViewDirectory: directory,
        },
      }),
    );
  }
  async function add(event: FormEvent) {
    event.preventDefault();
    if (
      await run(() =>
        communityRequest(`/teams/${team.id}/members`, {
          method: "PUT",
          body: { username, role },
        }),
      )
    )
      setUsername("");
  }
  return (
    <Stack>
      <Paper withBorder p="md">
        <Stack>
          <Text fw={600}>{team.name}</Text>
          {error ? (
            <Alert color="var(--c-danger)" role="alert">
              {error}
            </Alert>
          ) : null}
          {admin ? (
            <form onSubmit={(event) => void save(event)}>
              <Stack>
                <TextInput
                  label={t("community.teamName", "Team name")}
                  value={name}
                  onChange={(event) => setName(event.currentTarget.value)}
                  maxLength={100}
                  required
                />
                <Switch
                  label={t(
                    "community.processingPermission",
                    "Allow PDF processing",
                  )}
                  checked={processing}
                  onChange={(event) =>
                    setProcessing(event.currentTarget.checked)
                  }
                />
                <Switch
                  label={t(
                    "community.directoryPermission",
                    "Allow members to view the team directory",
                  )}
                  checked={directory}
                  onChange={(event) =>
                    setDirectory(event.currentTarget.checked)
                  }
                />
                <Group>
                  <Button type="submit" loading={busy}>
                    {t("community.save", "Save")}
                  </Button>
                  <Button
                    variant="quiet"
                    accent="danger"
                    disabled={busy}
                    onClick={() => {
                      if (
                        window.confirm(
                          t(
                            "community.deleteTeamConfirm",
                            "Delete this team and all its memberships? Members may lose PDF-processing access.",
                          ),
                        )
                      )
                        void run(() =>
                          communityRequest(`/teams/${team.id}`, {
                            method: "DELETE",
                          }),
                        ).then((ok) => {
                          if (ok) onDeleted();
                        });
                    }}
                  >
                    {t("community.deleteTeam", "Delete team")}
                  </Button>
                </Group>
              </Stack>
            </form>
          ) : (
            <Text>
              {team.canProcessPdf
                ? t(
                    "community.processingEnabled",
                    "PDF processing is allowed for this team",
                  )
                : t(
                    "community.processingDisabled",
                    "PDF processing is disabled for this team",
                  )}
            </Text>
          )}
          {manager ? (
            <form onSubmit={(event) => void add(event)}>
              <Group align="end">
                <TextInput
                  label={t(
                    "community.existingUsername",
                    "Existing account username",
                  )}
                  value={username}
                  onChange={(event) => setUsername(event.currentTarget.value)}
                  required
                />
                <Select
                  label={t("community.teamRole", "Team role")}
                  data={roles}
                  value={role}
                  allowDeselect={false}
                  onChange={(value) => {
                    if (value === "OWNER" || value === "MEMBER") setRole(value);
                  }}
                />
                <Button type="submit" loading={busy}>
                  {t("community.addMember", "Add or update member")}
                </Button>
              </Group>
            </form>
          ) : null}
          {!canView ? (
            <Text>
              {t(
                "community.privateDirectory",
                "Only this team's owners and administrators can view its directory.",
              )}
            </Text>
          ) : members.isLoading ? (
            <Loader />
          ) : members.isError ? (
            <Alert color="var(--c-danger)" role="alert">
              {members.error.message}
            </Alert>
          ) : (
            <div className="community-table-wrap">
              <table className="community-table">
                <thead>
                  <tr>
                    <th>{t("community.username", "Username")}</th>
                    <th>{t("community.teamRole", "Team role")}</th>
                    {manager ? (
                      <th>{t("community.actions", "Actions")}</th>
                    ) : null}
                  </tr>
                </thead>
                <tbody>
                  {members.data?.map((member) => (
                    <tr key={member.account.id}>
                      <td>{member.account.username}</td>
                      <td>
                        {member.role === "OWNER"
                          ? t("community.teamOwner", "Team owner")
                          : t("community.member", "User")}
                      </td>
                      {manager ? (
                        <td>
                          <Group>
                            <Button
                              variant="secondary"
                              disabled={busy}
                              onClick={() =>
                                void run(() =>
                                  communityRequest(
                                    `/teams/${team.id}/members`,
                                    {
                                      method: "PUT",
                                      body: {
                                        username: member.account.username,
                                        role:
                                          member.role === "OWNER"
                                            ? "MEMBER"
                                            : "OWNER",
                                      },
                                    },
                                  ),
                                )
                              }
                            >
                              {member.role === "OWNER"
                                ? t("community.makeMember", "Make member")
                                : t("community.makeOwner", "Make owner")}
                            </Button>
                            <Button
                              variant="quiet"
                              accent="danger"
                              disabled={busy}
                              onClick={() =>
                                void run(() =>
                                  communityRequest(
                                    `/teams/${team.id}/members/${member.account.id}`,
                                    { method: "DELETE" },
                                  ),
                                )
                              }
                            >
                              {t("community.removeMember", "Remove")}
                            </Button>
                          </Group>
                        </td>
                      ) : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Stack>
      </Paper>
    </Stack>
  );
}

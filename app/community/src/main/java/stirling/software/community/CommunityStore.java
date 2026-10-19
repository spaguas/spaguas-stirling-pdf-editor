package stirling.software.community;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import stirling.software.community.CommunityModels.*;

/**
 * Bound SQL persistence. Mutations serialize on the guard row within their caller's transaction.
 */
@Repository
public class CommunityStore {
    private final JdbcTemplate jdbc;
    private static final RowMapper<CommunityPrincipal> USER = CommunityStore::readUser;

    public CommunityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock() {
        jdbc.queryForObject("SELECT id FROM community_guard WHERE id=1 FOR UPDATE", Integer.class);
    }

    public Optional<CommunityPrincipal> user(String id) {
        return jdbc.query("SELECT * FROM community_users WHERE id=?", USER, id).stream()
                .findFirst();
    }

    public Optional<CommunityPrincipal> byUsername(String username) {
        return jdbc
                .query("SELECT * FROM community_users WHERE username=?", USER, normalize(username))
                .stream()
                .findFirst();
    }

    public List<CommunityPrincipal> users() {
        return jdbc.query("SELECT * FROM community_users ORDER BY username", USER);
    }

    public int userCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM community_users", Integer.class);
    }

    public int adminCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM community_users WHERE user_role='ADMIN' AND enabled=TRUE",
                Integer.class);
    }

    public void insertUser(CommunityPrincipal user) {
        jdbc.update(
                "INSERT INTO community_users (id, username, password_hash, user_role, enabled, must_change_password, credential_version) VALUES (?,?,?,?,?,?,?)",
                user.id(),
                user.username(),
                user.passwordHash(),
                user.role().name(),
                user.enabled(),
                user.mustChangePassword(),
                user.version());
    }

    public void updateUser(String id, Role role, boolean enabled) {
        jdbc.update(
                "UPDATE community_users SET user_role=?,enabled=?,credential_version=credential_version+1 WHERE id=?",
                role.name(),
                enabled,
                id);
    }

    public void password(String id, String hash, boolean mustChange) {
        jdbc.update(
                "UPDATE community_users SET password_hash=?,must_change_password=?,credential_version=credential_version+1 WHERE id=?",
                hash,
                mustChange,
                id);
    }

    public void deleteUser(String id) {
        jdbc.update("DELETE FROM community_users WHERE id=?", id);
    }

    public Optional<String> setting(String key) {
        return jdbc
                .query(
                        "SELECT setting_value FROM community_settings WHERE setting_key=?",
                        (rs, row) -> rs.getString(1),
                        key)
                .stream()
                .findFirst();
    }

    public void setting(String key, String value) {
        jdbc.update(
                "INSERT INTO community_settings (setting_key,setting_value) VALUES (?,?)",
                key,
                value);
    }

    public List<Team> teams(String viewerId, boolean admin) {
        return jdbc.query(
                "SELECT t.*,m.member_role,(SELECT COUNT(*) FROM community_memberships n WHERE n.team_id=t.id) AS member_count FROM community_teams t LEFT JOIN community_memberships m ON m.team_id=t.id AND m.user_id=? "
                        + (admin ? "" : "WHERE m.user_id IS NOT NULL ")
                        + "ORDER BY t.normalized_name",
                CommunityStore::readTeam,
                viewerId);
    }

    public Optional<Team> team(String id) {
        return jdbc
                .query(
                        "SELECT t.*,NULL AS member_role,(SELECT COUNT(*) FROM community_memberships n WHERE n.team_id=t.id) AS member_count FROM community_teams t WHERE t.id=?",
                        CommunityStore::readTeam,
                        id)
                .stream()
                .findFirst();
    }

    public void insertTeam(String id, TeamInput input) {
        jdbc.update(
                "INSERT INTO community_teams (id,name,normalized_name,can_process_pdf,members_can_view_directory) VALUES (?,?,?,?,?)",
                id,
                input.name().trim(),
                normalize(input.name()),
                input.canProcessPdf(),
                input.membersCanViewDirectory());
    }

    public void updateTeam(String id, TeamInput input) {
        jdbc.update(
                "UPDATE community_teams SET name=?,normalized_name=?,can_process_pdf=?,members_can_view_directory=? WHERE id=?",
                input.name().trim(),
                normalize(input.name()),
                input.canProcessPdf(),
                input.membersCanViewDirectory(),
                id);
    }

    public void deleteTeam(String id) {
        jdbc.update("DELETE FROM community_teams WHERE id=?", id);
    }

    public Optional<MemberRole> membership(String team, String user) {
        return jdbc
                .query(
                        "SELECT member_role FROM community_memberships WHERE team_id=? AND user_id=?",
                        (rs, row) -> MemberRole.valueOf(rs.getString(1)),
                        team,
                        user)
                .stream()
                .findFirst();
    }

    public List<Member> members(String team) {
        return jdbc.query(
                "SELECT u.*,m.member_role FROM community_users u JOIN community_memberships m ON m.user_id=u.id WHERE m.team_id=? ORDER BY u.username",
                (rs, row) ->
                        new Member(
                                readUser(rs, row).account(),
                                MemberRole.valueOf(rs.getString("member_role"))),
                team);
    }

    public void putMember(String team, String user, MemberRole role) {
        jdbc.update(
                "MERGE INTO community_memberships (team_id,user_id,member_role) KEY(team_id,user_id) VALUES (?,?,?)",
                team,
                user,
                role.name());
    }

    public void deleteMember(String team, String user) {
        jdbc.update("DELETE FROM community_memberships WHERE team_id=? AND user_id=?", team, user);
    }

    public int enabledOwners(String team, String except) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM community_memberships m JOIN community_users u ON u.id=m.user_id WHERE m.team_id=? AND m.member_role='OWNER' AND u.enabled=TRUE AND u.id<>?",
                Integer.class,
                team,
                except);
    }

    public boolean canProcess(String id) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM community_memberships m JOIN community_teams t ON t.id=m.team_id WHERE m.user_id=? AND t.can_process_pdf=TRUE",
                        Integer.class,
                        id)
                > 0;
    }

    private static CommunityPrincipal readUser(ResultSet rs, int row) throws SQLException {
        return new CommunityPrincipal(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("user_role")),
                rs.getBoolean("enabled"),
                rs.getBoolean("must_change_password"),
                rs.getInt("credential_version"));
    }

    private static Team readTeam(ResultSet rs, int row) throws SQLException {
        String role = rs.getString("member_role");
        return new Team(
                rs.getString("id"),
                rs.getString("name"),
                rs.getBoolean("can_process_pdf"),
                rs.getBoolean("members_can_view_directory"),
                role == null ? null : MemberRole.valueOf(role),
                rs.getInt("member_count"));
    }

    public static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}

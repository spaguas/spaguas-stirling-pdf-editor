package stirling.software.community;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import stirling.software.community.CommunityModels.*;

/** One-time, transactional data migration from a stopped H2 backup opened strictly read-only. */
@Service
@Slf4j
public class CommunityLegacyImport {
    private final CommunityStore store;
    private final CommunityProperties properties;
    private final PasswordEncoder passwords;

    public CommunityLegacyImport(
            CommunityStore store, CommunityProperties properties, PasswordEncoder passwords) {
        this.store = store;
        this.properties = properties;
        this.passwords = passwords;
    }

    @Transactional(rollbackFor = Exception.class)
    public void runIfConfigured() throws SQLException {
        String url = properties.getLegacyJdbcUrl();
        if (url.isBlank()) {
            return;
        }
        store.lock();
        if (store.setting("legacy-import-completed").isPresent()) {
            return;
        }
        if (!url.startsWith("jdbc:h2:file:") || url.contains(";")) {
            throw new IllegalArgumentException(
                    "COMMUNITY_LEGACY_JDBC_URL must name a local H2 backup without connection options");
        }
        if (store.userCount() != 0) {
            throw new IllegalStateException(
                    "Legacy import requires an empty Community identity database");
        }
        try (Connection connection =
                DriverManager.getConnection(
                        url + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r",
                        properties.getLegacyUsername(),
                        properties.getLegacyPassword())) {
            connection.setReadOnly(true);
            JdbcTemplate legacy =
                    new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            Map<String, String> tables = tables(connection);
            String usersTable = requireTable(tables, "users");
            String authoritiesTable = requireTable(tables, "authorities");
            Map<String, Set<String>> roles = new HashMap<>();
            legacy.query(
                    "SELECT user_id,authority FROM " + authoritiesTable,
                    rs -> {
                        roles.computeIfAbsent(rs.getString(1), ignored -> new HashSet<>())
                                .add(rs.getString(2));
                    });
            Map<String, CommunityPrincipal> users = new LinkedHashMap<>();
            Map<String, String> primaryTeams = new HashMap<>();
            Set<String> usernames = new HashSet<>();
            Set<String> userColumns = columns(connection, tables.get("users"));
            boolean hasTeam = userColumns.contains("team_id");
            boolean hasAuthenticationType = userColumns.contains("authenticationtype");
            legacy.query(
                    "SELECT user_id,username,password,enabled,"
                            + (hasTeam ? "team_id" : "NULL")
                            + ","
                            + (hasAuthenticationType ? "authenticationtype" : "'web'")
                            + " FROM "
                            + usersTable,
                    rs -> {
                        String oldId = rs.getString(1);
                        Set<String> userRoles = roles.getOrDefault(oldId, Set.of());
                        String internalName =
                                stirling.software.common.model.enumeration.Role.INTERNAL_API_USER
                                        .getRoleId();
                        if (userRoles.contains(internalName)
                                || userRoles.contains("ROLE_INTERNAL_API_USER")
                                || internalName.equalsIgnoreCase(rs.getString(2))) {
                            return;
                        }
                        String username = CommunityAccounts.validUsername(rs.getString(2));
                        if (!usernames.add(username)) {
                            throw new IllegalStateException(
                                    "Legacy usernames collide after case normalization; import was rolled back");
                        }
                        String hash = rs.getString(3);
                        if (hash != null && hash.startsWith("{bcrypt}")) {
                            hash = hash.substring(8);
                        }
                        boolean validHash =
                                hash != null
                                        && hash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}");
                        boolean supported =
                                userRoles.contains("ROLE_ADMIN")
                                        || (userRoles.contains("ROLE_USER")
                                                && !userRoles.contains("ROLE_WEB_ONLY_USER")
                                                && !userRoles.contains("ROLE_DEMO_USER"));
                        boolean enabled =
                                rs.getBoolean(4)
                                        && validHash
                                        && supported
                                        && "web".equalsIgnoreCase(rs.getString(6));
                        users.put(
                                oldId,
                                new CommunityPrincipal(
                                        id("user", oldId),
                                        username,
                                        validHash
                                                ? hash
                                                : passwords.encode(UUID.randomUUID().toString()),
                                        userRoles.contains("ROLE_ADMIN") ? Role.ADMIN : Role.USER,
                                        enabled,
                                        true,
                                        0));
                        primaryTeams.put(oldId, rs.getString(5));
                    });
            CommunityPrincipal administrator =
                    users.values().stream()
                            .filter(u -> u.enabled() && u.role() == Role.ADMIN)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Legacy import needs an active local-password administrator; no accounts were imported"));
            users.values().forEach(store::insertUser);
            Map<String, String> teams = new HashMap<>();
            if (tables.containsKey("teams")) {
                legacy.query(
                        "SELECT team_id,name FROM " + quoted(tables.get("teams")),
                        rs -> {
                            String name = rs.getString(2);
                            if ("Internal".equalsIgnoreCase(name)) {
                                return;
                            }
                            if (name == null || name.isBlank() || name.trim().length() > 100) {
                                throw new IllegalStateException(
                                        "Legacy team name is invalid; import was rolled back");
                            }
                            String target = id("team", rs.getString(1));
                            store.insertTeam(target, new TeamInput(name, true, true));
                            teams.put(rs.getString(1), target);
                        });
            }
            String unassigned = id("team", "community-unassigned");
            boolean needsUnassigned =
                    primaryTeams.values().stream().anyMatch(value -> value == null);
            if (needsUnassigned) {
                store.insertTeam(unassigned, new TeamInput("Imported accounts", true, true));
            }
            for (Map.Entry<String, CommunityPrincipal> entry : users.entrySet()) {
                String oldTeam = primaryTeams.get(entry.getKey());
                String target = oldTeam == null ? unassigned : teams.get(oldTeam);
                if (target == null) {
                    throw new IllegalStateException(
                            "Legacy account references a missing or system team; import was rolled back");
                }
                store.putMember(target, entry.getValue().id(), MemberRole.MEMBER);
            }
            if (tables.containsKey("team_memberships")) {
                legacy.query(
                        "SELECT team_id,user_id,role FROM "
                                + quoted(tables.get("team_memberships")),
                        rs -> {
                            String target = teams.get(rs.getString(1));
                            CommunityPrincipal user = users.get(rs.getString(2));
                            if (target == null || user == null) {
                                return;
                            }
                            boolean owner =
                                    user.enabled()
                                            && ("LEADER".equals(rs.getString(3))
                                                    || "OWNER".equals(rs.getString(3)));
                            store.putMember(
                                    target,
                                    user.id(),
                                    owner ? MemberRole.OWNER : MemberRole.MEMBER);
                        });
            }
            List<String> allTeams = new ArrayList<>(teams.values());
            if (needsUnassigned) {
                allTeams.add(unassigned);
            }
            for (String team : allTeams) {
                if (store.enabledOwners(team, "") == 0) {
                    store.putMember(team, administrator.id(), MemberRole.OWNER);
                }
            }
            store.setting("legacy-import-completed", "true");
            log.info(
                    "Community import complete: {} accounts, {} teams, {} disabled accounts requiring review",
                    users.size(),
                    allTeams.size(),
                    users.values().stream().filter(u -> !u.enabled()).count());
        }
    }

    private static String id(String kind, String oldId) {
        return UUID.nameUUIDFromBytes(
                        ("community-import:" + kind + ":" + oldId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static Map<String, String> tables(Connection connection) throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (var rows =
                connection
                        .getMetaData()
                        .getTables(null, "PUBLIC", "%", new String[] {"TABLE", "BASE TABLE"})) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                result.put(name.toLowerCase(java.util.Locale.ROOT), name);
            }
        }
        return result;
    }

    private static Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (var rows = connection.getMetaData().getColumns(null, "PUBLIC", table, "%")) {
            while (rows.next()) {
                result.add(rows.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
            }
        }
        return result;
    }

    private static String requireTable(Map<String, String> tables, String name) {
        String actual = tables.get(name);
        if (actual == null) {
            throw new IllegalStateException("Legacy backup is missing the " + name + " table");
        }
        return quoted(actual);
    }

    private static String quoted(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}

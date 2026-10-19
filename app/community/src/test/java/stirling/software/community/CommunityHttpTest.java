package stirling.software.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.community.CommunityModels.*;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        classes = CommunityHttpTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.servlet.context-path=/editor-pdf",
            "community.database-url=jdbc:h2:mem:community-http;DB_CLOSE_DELAY=-1",
            "community.data-directory=/tmp/stirling-community-http-test",
            "community.initial-admin-password=InitialAdmin123!"
        })
class CommunityHttpTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = CommunityController.class)
    static class Application {
        @Bean
        ApplicationProperties applicationProperties() {
            return org.mockito.Mockito.mock(
                    ApplicationProperties.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        }

        @Bean
        @Primary
        PasswordEncoder testPasswords() {
            return new BCryptPasswordEncoder(4);
        }
    }

    @RestController
    static class Probe {
        @GetMapping({"/api/v1/test/process", "/api/v1/info/health"})
        String process() {
            return "processed";
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired CommunityAccounts accounts;
    @Autowired CommunityProperties properties;
    @Autowired CommunityStore store;
    @Autowired CommunityApiKeys keys;
    final JsonMapper json = JsonMapper.builder().build();
    static final String INITIAL = "InitialAdmin123!";
    static final String PASSWORD = "ChangedSecret123!";

    @BeforeEach
    void reset() {
        properties.setLegacyJdbcUrl("");
        jdbc.update("DELETE FROM community_settings WHERE setting_key='legacy-import-completed'");
        jdbc.update("DELETE FROM community_memberships");
        jdbc.update("DELETE FROM community_teams");
        jdbc.update("DELETE FROM community_users");
        accounts.bootstrap(properties);
    }

    @Test
    void requiresCsrfAndForcesPasswordChangeBeforeAdministration() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.call("GET", "/users", null).statusCode()).isEqualTo(401);
        assertThat(
                        browser.send("POST", "/login", "username=admin&password=" + INITIAL, false)
                                .statusCode())
                .isEqualTo(403);
        browser.login("admin", INITIAL);
        assertThat(browser.call("GET", "/users", null).statusCode()).isEqualTo(403);
        assertThat(browser.pdf().statusCode()).isEqualTo(403);
        browser.change(INITIAL, PASSWORD);
        assertThat(browser.call("GET", "/users", null).statusCode()).isEqualTo(401);
        browser.login("admin", PASSWORD);
        var response = browser.call("GET", "/users", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("passwordHash", "password_hash", "$2", PASSWORD);
        assertThat(browser.pdf().statusCode()).isEqualTo(200);
    }

    @Test
    void accountsHaveNoSeatLimitAndGlobalPrivilegesCannotBeGrantedByMembers() throws Exception {
        Browser admin = admin();
        for (int i = 0; i < 8; i++) create(admin, "person" + i, null);
        assertThat(json.readTree(admin.call("GET", "/users", null).body()).size()).isEqualTo(9);
        Browser member = member("person0");
        assertThat(member.call("GET", "/users", null).statusCode()).isEqualTo(403);
        assertThat(
                        member.call(
                                        "POST",
                                        "/users",
                                        Map.of(
                                                "username",
                                                "intruder",
                                                "password",
                                                INITIAL,
                                                "role",
                                                "ADMIN"))
                                .statusCode())
                .isEqualTo(403);
        assertThat(member.pdf().statusCode()).isEqualTo(403);
        assertThat(
                        admin.call(
                                        "POST",
                                        "/users",
                                        Map.of(
                                                "username",
                                                "anonymousUser",
                                                "password",
                                                INITIAL,
                                                "role",
                                                "USER"))
                                .statusCode())
                .isEqualTo(400);
    }

    @Test
    void teamOwnersManageOnlyTheirMembersAndPolicyIsEnforcedOnEveryPdfRequest() throws Exception {
        Browser admin = admin();
        String team = team(admin, "Operations", true, false);
        String other = team(admin, "Other", true, true);
        String ownerId = create(admin, "owner", team);
        String memberId = create(admin, "worker", team);
        create(admin, "outsider", other);
        put(admin, team, "owner", "OWNER", 204);
        assertThat(
                        admin.call(
                                        "DELETE",
                                        "/teams/"
                                                + team
                                                + "/members/"
                                                + store.byUsername("admin").orElseThrow().id(),
                                        null)
                                .statusCode())
                .isEqualTo(204);
        Browser owner = member("owner"), worker = member("worker"), outsider = member("outsider");
        assertThat(owner.call("GET", "/teams/" + team + "/members", null).statusCode())
                .isEqualTo(200);
        assertThat(worker.call("GET", "/teams/" + team + "/members", null).statusCode())
                .isEqualTo(403);
        assertThat(owner.call("GET", "/teams/" + other + "/members", null).statusCode())
                .isEqualTo(403);
        assertThat(outsider.call("GET", "/teams", null).body()).doesNotContain("Operations");
        assertThat(
                        owner.call(
                                        "PUT",
                                        "/teams/" + team,
                                        Map.of(
                                                "name",
                                                "Hijack",
                                                "canProcessPdf",
                                                true,
                                                "membersCanViewDirectory",
                                                true))
                                .statusCode())
                .isEqualTo(403);
        put(owner, team, "worker", "MEMBER", 204);
        assertThat(worker.pdf().statusCode()).isEqualTo(200);
        assertThat(
                        owner.call("DELETE", "/teams/" + team + "/members/" + ownerId, null)
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        admin.call(
                                        "PUT",
                                        "/users/" + ownerId,
                                        Map.of("role", "USER", "enabled", false))
                                .statusCode())
                .isEqualTo(409);
        assertThat(
                        admin.call(
                                        "PUT",
                                        "/teams/" + team,
                                        Map.of(
                                                "name",
                                                "Operations",
                                                "canProcessPdf",
                                                false,
                                                "membersCanViewDirectory",
                                                false))
                                .statusCode())
                .isEqualTo(204);
        assertThat(worker.pdf().statusCode()).isEqualTo(403);
        put(owner, team, "worker", "OWNER", 204);
        assertThat(
                        owner.call("DELETE", "/teams/" + team + "/members/" + ownerId, null)
                                .statusCode())
                .isEqualTo(204);
        assertThat(store.membership(team, memberId)).contains(MemberRole.OWNER);
    }

    @Test
    void resettingOrDisablingAnAccountRevokesSessionsAndSignedApiKeys() throws Exception {
        Browser admin = admin();
        String team = team(admin, "Allowed", true, true);
        String id = create(admin, "revoked", team);
        Browser member = member("revoked");
        String key = keys.forUsername("revoked");
        assertThat(api(key)).isEqualTo(200);
        assertThat(
                        admin.call(
                                        "POST",
                                        "/users/" + id + "/password",
                                        Map.of("password", "Replacement123!"))
                                .statusCode())
                .isEqualTo(204);
        assertThat(member.pdf().statusCode()).isEqualTo(401);
        assertThat(api(key)).isEqualTo(401);
        member.login("revoked", "Replacement123!");
        assertThat(member.pdf().statusCode()).isEqualTo(403);
        member.change("Replacement123!", PASSWORD);
        member.login("revoked", PASSWORD);
        assertThat(member.pdf().statusCode()).isEqualTo(200);
        assertThat(
                        admin.call("PUT", "/users/" + id, Map.of("role", "USER", "enabled", false))
                                .statusCode())
                .isEqualTo(204);
        assertThat(member.pdf().statusCode()).isEqualTo(401);
        assertThat(api("invalid")).isEqualTo(401);
    }

    @Test
    void processingAccessDoesNotGrantServerAdministration() throws Exception {
        Browser admin = admin();
        String team = team(admin, "Processing only", true, true);
        create(admin, "limitedadmin", team);
        Browser worker = member("limitedadmin");
        assertThat(worker.pdf().statusCode()).isEqualTo(200);
        assertThat(
                        worker.client
                                .send(
                                        HttpRequest.newBuilder(
                                                        URI.create(base() + "/api/v1/info/health"))
                                                .build(),
                                        HttpResponse.BodyHandlers.ofString())
                                .statusCode())
                .isEqualTo(403);
        assertThat(
                        admin.client
                                .send(
                                        HttpRequest.newBuilder(
                                                        URI.create(base() + "/api/v1/info/health"))
                                                .build(),
                                        HttpResponse.BodyHandlers.ofString())
                                .statusCode())
                .isEqualTo(200);
        String csrf =
                worker.cookies.getCookieStore().getCookies().stream()
                        .filter(c -> c.getName().equals("XSRF-TOKEN"))
                        .findFirst()
                        .orElseThrow()
                        .getValue();
        assertThat(
                        worker.client
                                .send(
                                        HttpRequest.newBuilder(
                                                        URI.create(
                                                                base()
                                                                        + "/api/v1/settings/update-enable-analytics"))
                                                .header("X-XSRF-TOKEN", csrf)
                                                .POST(HttpRequest.BodyPublishers.noBody())
                                                .build(),
                                        HttpResponse.BodyHandlers.ofString())
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void lastAdministratorAndPasswordRulesAreProtected() throws Exception {
        Browser admin = admin();
        String id = store.byUsername("admin").orElseThrow().id();
        assertThat(
                        admin.call("PUT", "/users/" + id, Map.of("role", "USER", "enabled", true))
                                .statusCode())
                .isEqualTo(409);
        assertThat(admin.call("DELETE", "/users/" + id, null).statusCode()).isEqualTo(409);
        assertThat(
                        admin.call(
                                        "POST",
                                        "/account/password",
                                        Map.of("currentPassword", PASSWORD, "newPassword", "short"))
                                .statusCode())
                .isEqualTo(400);
        assertThat(
                        admin.call(
                                        "POST",
                                        "/account/password",
                                        Map.of("currentPassword", "wrong", "newPassword", INITIAL))
                                .statusCode())
                .isEqualTo(400);
        assertThat(new CreateAccount("person", INITIAL, Role.USER, null).toString())
                .doesNotContain(INITIAL);
    }

    @Test
    void repeatedFailedLoginsAreLimitedWithoutTrustingForwardedAddresses() throws Exception {
        Browser browser = new Browser();
        for (int i = 0; i < 10; i++)
            assertThat(browser.loginResponse("ratelimit", "wrong").statusCode()).isEqualTo(401);
        assertThat(browser.loginResponse("ratelimit", "wrong").statusCode()).isEqualTo(429);
    }

    @Autowired CommunityLegacyImport migration;
    @Autowired PasswordEncoder encoder;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;

    @Test
    void legacyImportPreservesHashesAndMembershipWithoutWritingSourceAndIsIdempotent()
            throws Exception {
        String url = legacy("Operations");
        String sourceHash = digest(temporary.resolve("legacy.mv.db"));
        emptyTarget();
        properties.setLegacyJdbcUrl(url);
        migration.runIfConfigured();
        var administrator = store.byUsername("oldadmin").orElseThrow();
        var imported = store.byUsername("oldmember").orElseThrow();
        assertThat(encoder.matches(INITIAL, administrator.passwordHash())).isTrue();
        assertThat(administrator.mustChangePassword()).isTrue();
        assertThat(store.byUsername("ssoaccount").orElseThrow().enabled()).isFalse();
        assertThat(store.userCount()).isEqualTo(3);
        assertThat(store.teams(imported.id(), false)).hasSize(1);
        String team = store.teams(imported.id(), false).getFirst().id();
        assertThat(store.membership(team, imported.id())).contains(MemberRole.OWNER);
        assertThat(digest(temporary.resolve("legacy.mv.db"))).isEqualTo(sourceHash);
        migration.runIfConfigured();
        assertThat(store.userCount()).isEqualTo(3);
        Browser browser = new Browser();
        browser.login("oldadmin", INITIAL);
        assertThat(browser.call("GET", "/users", null).statusCode()).isEqualTo(403);
    }

    @Test
    void inconsistentLegacyTeamsRollBackEveryTargetWrite() throws Exception {
        String url = legacy("Operations");
        try (var connection = java.sql.DriverManager.getConnection(url, "sa", "")) {
            connection
                    .createStatement()
                    .executeUpdate("UPDATE users SET team_id=999 WHERE user_id=2");
        }
        emptyTarget();
        properties.setLegacyJdbcUrl(url);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> migration.runIfConfigured())
                .hasMessageContaining("missing or system team");
        assertThat(store.userCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_teams", Integer.class))
                .isZero();
        assertThat(store.setting("legacy-import-completed")).isEmpty();
    }

    @Test
    void migrationRefusesExistingAccountsAndUnsafeConnectionOptions() throws Exception {
        properties.setLegacyJdbcUrl(legacy("Operations"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> migration.runIfConfigured())
                .hasMessageContaining("empty Community");
        properties.setLegacyJdbcUrl("jdbc:h2:file:/tmp/nonexistent;INIT=RUNSCRIPT FROM 'remote'");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> migration.runIfConfigured())
                .hasMessageContaining("without connection options");
        assertThat(store.userCount()).isEqualTo(1);
    }

    void emptyTarget() {
        jdbc.update("DELETE FROM community_memberships");
        jdbc.update("DELETE FROM community_teams");
        jdbc.update("DELETE FROM community_users");
    }

    String legacy(String teamName) throws Exception {
        String url = "jdbc:h2:file:" + temporary.resolve("legacy");
        try (var connection = java.sql.DriverManager.getConnection(url, "sa", "")) {
            JdbcTemplate source =
                    new JdbcTemplate(
                            new org.springframework.jdbc.datasource.SingleConnectionDataSource(
                                    connection, true));
            source.execute(
                    "CREATE TABLE users (user_id BIGINT PRIMARY KEY,username VARCHAR(64),password VARCHAR(200),enabled BOOLEAN,team_id BIGINT,authenticationtype VARCHAR(20))");
            source.execute("CREATE TABLE authorities (user_id BIGINT,authority VARCHAR(64))");
            source.execute("CREATE TABLE teams (team_id BIGINT PRIMARY KEY,name VARCHAR(100))");
            source.execute(
                    "CREATE TABLE team_memberships (team_id BIGINT,user_id BIGINT,role VARCHAR(32))");
            source.update("INSERT INTO teams VALUES (10,?)", teamName);
            source.update(
                    "INSERT INTO users VALUES (1,'OldAdmin',?,TRUE,10,'web')",
                    encoder.encode(INITIAL));
            source.update(
                    "INSERT INTO users VALUES (2,'OldMember',?,TRUE,10,'web')",
                    encoder.encode(INITIAL));
            source.update(
                    "INSERT INTO users VALUES (3,'SsoAccount',?,TRUE,NULL,'oauth2')",
                    encoder.encode(INITIAL));
            source.update(
                    "INSERT INTO users VALUES (4,'STIRLING-PDF-BACKEND-API-USER',NULL,TRUE,NULL,'web')");
            source.update(
                    "INSERT INTO authorities VALUES (1,'ROLE_ADMIN'),(2,'ROLE_USER'),(3,'ROLE_USER'),(4,'STIRLING-PDF-BACKEND-API-USER')");
            source.update("INSERT INTO team_memberships VALUES (10,2,'LEADER')");
        }
        return url;
    }

    static String digest(java.nio.file.Path file) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(java.nio.file.Files.readAllBytes(file)));
    }

    Browser admin() throws Exception {
        Browser browser = new Browser();
        browser.login("admin", INITIAL);
        browser.change(INITIAL, PASSWORD);
        browser.login("admin", PASSWORD);
        return browser;
    }

    Browser member(String username) throws Exception {
        Browser browser = new Browser();
        browser.login(username, INITIAL);
        browser.change(INITIAL, PASSWORD);
        browser.login(username, PASSWORD);
        return browser;
    }

    String create(Browser browser, String username, String team) throws Exception {
        var input = new CreateAccount(username, INITIAL, Role.USER, team);
        var response = browser.call("POST", "/users", input);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asString();
    }

    String team(Browser browser, String name, boolean processing, boolean directory)
            throws Exception {
        var response = browser.call("POST", "/teams", new TeamInput(name, processing, directory));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asString();
    }

    void put(Browser browser, String team, String user, String role, int status) throws Exception {
        assertThat(
                        browser.call(
                                        "PUT",
                                        "/teams/" + team + "/members",
                                        Map.of("username", user, "role", role))
                                .statusCode())
                .isEqualTo(status);
    }

    int api(String key) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(base() + "/api/v1/test/process"))
                                .header("X-API-KEY", key)
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }

    String base() {
        return "http://localhost:" + port + "/editor-pdf";
    }

    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> call(String method, String path, Object body) throws Exception {
            return send(method, path, body == null ? null : json.writeValueAsString(body), true);
        }

        HttpResponse<String> send(String method, String path, String body, boolean csrf)
                throws Exception {
            var request = HttpRequest.newBuilder(URI.create(base() + "/api/v1/community" + path));
            if (csrf)
                cookies.getCookieStore().getCookies().stream()
                        .filter(c -> c.getName().equals("XSRF-TOKEN"))
                        .findFirst()
                        .ifPresent(c -> request.header("X-XSRF-TOKEN", c.getValue()));
            request.header(
                    "Content-Type",
                    path.equals("/login")
                            ? "application/x-www-form-urlencoded"
                            : "application/json");
            request.method(
                    method,
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> loginResponse(String username, String password) throws Exception {
            call("GET", "/session", null);
            return send("POST", "/login", "username=" + username + "&password=" + password, true);
        }

        void login(String username, String password) throws Exception {
            assertThat(loginResponse(username, password).statusCode()).isEqualTo(204);
            call("GET", "/session", null);
        }

        void change(String before, String after) throws Exception {
            var response = call("POST", "/account/password", new ChangePassword(before, after));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(204);
        }

        HttpResponse<String> pdf() throws Exception {
            return client.send(
                    HttpRequest.newBuilder(URI.create(base() + "/api/v1/test/process")).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}

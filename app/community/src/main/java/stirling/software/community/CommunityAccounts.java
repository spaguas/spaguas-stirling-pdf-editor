package stirling.software.community;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import stirling.software.common.service.UserServiceInterface;
import stirling.software.community.CommunityModels.*;

/** Local accounts and global privileges; every write protects the last active administrator. */
@Service
public class CommunityAccounts implements UserDetailsService, UserServiceInterface {
    private final CommunityStore store;
    private final PasswordEncoder passwords;
    private final CommunityApiKeys keys;

    public CommunityAccounts(
            CommunityStore store, PasswordEncoder passwords, CommunityApiKeys keys) {
        this.store = store;
        this.passwords = passwords;
        this.keys = keys;
    }

    @Override
    public CommunityPrincipal loadUserByUsername(String username) {
        return store.byUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    public CommunityPrincipal current() {
        return store.byUsername(getCurrentUsername())
                .filter(CommunityPrincipal::enabled)
                .orElseThrow(() -> problem(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public CommunityPrincipal ready() {
        CommunityPrincipal user = current();
        if (user.mustChangePassword()) {
            throw problem(HttpStatus.FORBIDDEN, "Change your password before continuing");
        }
        return user;
    }

    public CommunityPrincipal admin() {
        CommunityPrincipal user = ready();
        if (user.role() != Role.ADMIN) {
            throw problem(HttpStatus.FORBIDDEN, "Administrator permission required");
        }
        return user;
    }

    public List<Account> directory() {
        admin();
        return store.users().stream().map(CommunityPrincipal::account).toList();
    }

    public boolean canProcessPdf(String username) {
        return store.byUsername(username)
                .filter(u -> u.enabled() && !u.mustChangePassword())
                .map(u -> u.role() == Role.ADMIN || store.canProcess(u.id()))
                .orElse(false);
    }

    @Transactional
    public void bootstrap(CommunityProperties properties) {
        store.lock();
        if (store.userCount() > 0) {
            if (store.adminCount() == 0) {
                throw new IllegalStateException("Community identity has no active administrator");
            }
            return;
        }
        if (properties.getInitialAdminPassword().isBlank()) {
            throw new IllegalStateException(
                    "Set COMMUNITY_INITIAL_ADMIN_PASSWORD before starting an empty Community database");
        }
        String username = validUsername(properties.getInitialAdminUsername());
        validatePassword(properties.getInitialAdminPassword());
        String userId = UUID.randomUUID().toString();
        String teamId = UUID.randomUUID().toString();
        store.insertUser(
                new CommunityPrincipal(
                        userId,
                        username,
                        passwords.encode(properties.getInitialAdminPassword()),
                        Role.ADMIN,
                        true,
                        true,
                        0));
        store.insertTeam(teamId, new TeamInput("Default", true, true));
        store.putMember(teamId, userId, MemberRole.OWNER);
    }

    @Transactional
    public Account create(CreateAccount input) {
        admin();
        store.lock();
        String username = validUsername(input.username());
        validatePassword(input.password());
        if (input.role() == null) {
            throw problem(HttpStatus.BAD_REQUEST, "An account role is required");
        }
        if (store.byUsername(username).isPresent()) {
            throw problem(HttpStatus.CONFLICT, "Username already exists");
        }
        if (input.teamId() != null) {
            store.team(input.teamId())
                    .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Team not found"));
        }
        CommunityPrincipal user =
                new CommunityPrincipal(
                        UUID.randomUUID().toString(),
                        username,
                        passwords.encode(input.password()),
                        input.role(),
                        true,
                        true,
                        0);
        store.insertUser(user);
        if (input.teamId() != null) {
            store.putMember(input.teamId(), user.id(), MemberRole.MEMBER);
        }
        return user.account();
    }

    @Transactional
    public void update(String id, UpdateAccount input) {
        admin();
        store.lock();
        CommunityPrincipal user = requireUser(id);
        if (input.role() == null) {
            throw problem(HttpStatus.BAD_REQUEST, "An account role is required");
        }
        if (user.enabled()
                && user.role() == Role.ADMIN
                && (!input.enabled() || input.role() != Role.ADMIN)
                && store.adminCount() <= 1) {
            throw problem(HttpStatus.CONFLICT, "Keep at least one active administrator");
        }
        if (!input.enabled()) {
            protectTeamOwners(user);
        }
        store.updateUser(id, input.role(), input.enabled());
    }

    @Transactional
    public void delete(String id) {
        admin();
        store.lock();
        CommunityPrincipal user = requireUser(id);
        if (user.enabled() && user.role() == Role.ADMIN && store.adminCount() <= 1) {
            throw problem(HttpStatus.CONFLICT, "Keep at least one active administrator");
        }
        protectTeamOwners(user);
        store.deleteUser(id);
    }

    @Transactional
    public void resetPassword(String id, String password) {
        admin();
        store.lock();
        requireUser(id);
        validatePassword(password);
        store.password(id, passwords.encode(password), true);
    }

    @Transactional
    public void changePassword(ChangePassword input) {
        store.lock();
        CommunityPrincipal user = current();
        validatePassword(input.newPassword());
        if (!passwords.matches(
                input.currentPassword() == null ? "" : input.currentPassword(),
                user.passwordHash())) {
            throw problem(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwords.matches(input.newPassword(), user.passwordHash())) {
            throw problem(HttpStatus.BAD_REQUEST, "Choose a different password");
        }
        store.password(user.id(), passwords.encode(input.newPassword()), false);
    }

    private void protectTeamOwners(CommunityPrincipal user) {
        for (Team team : store.teams(user.id(), false)) {
            if (team.membership() == MemberRole.OWNER
                    && store.enabledOwners(team.id(), user.id()) == 0) {
                throw problem(
                        HttpStatus.CONFLICT,
                        "Assign another active owner before disabling or deleting this account");
            }
        }
    }

    public CommunityPrincipal requireUser(String id) {
        return store.user(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Account not found"));
    }

    public static String validUsername(String username) {
        String normalized = CommunityStore.normalize(username);
        if (!normalized.matches("[a-z0-9][a-z0-9._@+-]{2,63}")
                || java.util.Set.of(
                                "anonymoususer",
                                "community-internal",
                                CommunityStore.normalize(
                                        stirling.software.common.model.enumeration.Role
                                                .INTERNAL_API_USER
                                                .getRoleId()))
                        .contains(normalized)) {
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "Username must contain 3 to 64 letters, digits or . _ @ + -");
        }
        return normalized;
    }

    public static void validatePassword(String password) {
        if (password == null
                || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw problem(
                    HttpStatus.BAD_REQUEST,
                    "Passwords must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
    }

    public static ResponseStatusException problem(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    @Override
    public String getCurrentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                        || !authentication.isAuthenticated()
                        || authentication instanceof AnonymousAuthenticationToken
                ? null
                : authentication.getName();
    }

    @Override
    public boolean isCurrentUserAdmin() {
        return store.byUsername(getCurrentUsername())
                .map(u -> u.enabled() && u.role() == Role.ADMIN)
                .orElse(false);
    }

    @Override
    public boolean isCurrentUserFirstLogin() {
        return store.byUsername(getCurrentUsername())
                .map(CommunityPrincipal::mustChangePassword)
                .orElse(false);
    }

    @Override
    public long getTotalUsersCount() {
        return store.userCount();
    }

    @Override
    public boolean usernameExists(String username) {
        return store.byUsername(username)
                .map(user -> user.username().equals(username))
                .orElse(false);
    }

    @Override
    public String getApiKeyForUser(String username) {
        return keys.forUsername(username);
    }

    @Override
    public String getCurrentUserApiKey() {
        return getApiKeyForUser(getCurrentUsername());
    }
}

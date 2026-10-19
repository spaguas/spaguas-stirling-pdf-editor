package stirling.software.community;

import static stirling.software.community.CommunityAccounts.problem;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import stirling.software.community.CommunityModels.*;

/**
 * Team ownership grants membership management, never global administration or processing-policy
 * changes.
 */
@Service
public class CommunityTeams {
    private final CommunityStore store;
    private final CommunityAccounts accounts;

    public CommunityTeams(CommunityStore store, CommunityAccounts accounts) {
        this.store = store;
        this.accounts = accounts;
    }

    public List<Team> directory() {
        var viewer = accounts.ready();
        return store.teams(viewer.id(), viewer.role() == Role.ADMIN);
    }

    public List<Member> members(String id) {
        var viewer = accounts.ready();
        Team team = requireTeam(id);
        MemberRole role = store.membership(id, viewer.id()).orElse(null);
        if (viewer.role() != Role.ADMIN
                && role != MemberRole.OWNER
                && (role == null || !team.membersCanViewDirectory())) {
            throw problem(HttpStatus.FORBIDDEN, "Team directory permission required");
        }
        return store.members(id);
    }

    @Transactional
    public Team create(TeamInput input) {
        var viewer = accounts.admin();
        store.lock();
        validate(input);
        String id = UUID.randomUUID().toString();
        store.insertTeam(id, input);
        store.putMember(id, viewer.id(), MemberRole.OWNER);
        return requireTeam(id);
    }

    @Transactional
    public void update(String id, TeamInput input) {
        accounts.admin();
        store.lock();
        requireTeam(id);
        validate(input);
        store.updateTeam(id, input);
    }

    @Transactional
    public void delete(String id) {
        accounts.admin();
        store.lock();
        requireTeam(id);
        store.deleteTeam(id);
    }

    @Transactional
    public void putMember(String id, MembershipInput input) {
        store.lock();
        requireManager(id);
        if (input.role() == null) {
            throw problem(HttpStatus.BAD_REQUEST, "A team role is required");
        }
        var user =
                store.byUsername(input.username())
                        .filter(CommunityPrincipal::enabled)
                        .orElseThrow(
                                () -> problem(HttpStatus.NOT_FOUND, "Active account not found"));
        protectLastOwner(id, user.id(), input.role());
        store.putMember(id, user.id(), input.role());
    }

    @Transactional
    public void removeMember(String id, String userId) {
        store.lock();
        requireManager(id);
        if (store.membership(id, userId).isEmpty()) {
            throw problem(HttpStatus.NOT_FOUND, "Membership not found");
        }
        protectLastOwner(id, userId, null);
        store.deleteMember(id, userId);
    }

    private void protectLastOwner(String id, String userId, MemberRole next) {
        if (store.membership(id, userId).orElse(null) == MemberRole.OWNER
                && next != MemberRole.OWNER
                && store.enabledOwners(id, userId) == 0) {
            throw problem(HttpStatus.CONFLICT, "Keep at least one active team owner");
        }
    }

    private void requireManager(String id) {
        var viewer = accounts.ready();
        requireTeam(id);
        if (viewer.role() != Role.ADMIN
                && store.membership(id, viewer.id()).orElse(null) != MemberRole.OWNER) {
            throw problem(HttpStatus.FORBIDDEN, "Team owner permission required");
        }
    }

    private Team requireTeam(String id) {
        return store.team(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Team not found"));
    }

    private void validate(TeamInput input) {
        if (input.name() == null || input.name().isBlank() || input.name().trim().length() > 100) {
            throw problem(HttpStatus.BAD_REQUEST, "Team names must contain 1 to 100 characters");
        }
    }
}

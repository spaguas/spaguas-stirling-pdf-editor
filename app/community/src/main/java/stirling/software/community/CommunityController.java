package stirling.software.community;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import stirling.software.community.CommunityModels.*;

/**
 * Community identity endpoints. Permissions are enforced by the services, including non-browser
 * callers.
 */
@RestController
@RequestMapping("/api/v1/community")
public class CommunityController {
    private final CommunityAccounts accounts;
    private final CommunityTeams teams;

    public CommunityController(CommunityAccounts accounts, CommunityTeams teams) {
        this.accounts = accounts;
        this.teams = teams;
    }

    @GetMapping("/session")
    public Session session(Authentication authentication, CsrfToken csrf) {
        csrf.getToken();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CommunityPrincipal)) {
            return new Session(false, null, false);
        }
        var user = accounts.current();
        return new Session(true, user.account(), accounts.canProcessPdf(user.username()));
    }

    @GetMapping("/users")
    public List<Account> users() {
        return accounts.directory();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Account create(@RequestBody CreateAccount input) {
        return accounts.create(input);
    }

    @PutMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String id, @RequestBody UpdateAccount input) {
        accounts.update(id, input);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        accounts.delete(id);
    }

    @PostMapping("/users/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable String id, @RequestBody ResetPassword input) {
        accounts.resetPassword(id, input.password());
    }

    @PostMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@RequestBody ChangePassword input, HttpServletRequest request) {
        accounts.changePassword(input);
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @GetMapping("/teams")
    public List<Team> teams() {
        return teams.directory();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public Team createTeam(@RequestBody TeamInput input) {
        return teams.create(input);
    }

    @PutMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTeam(@PathVariable String id, @RequestBody TeamInput input) {
        teams.update(id, input);
    }

    @DeleteMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(@PathVariable String id) {
        teams.delete(id);
    }

    @GetMapping("/teams/{id}/members")
    public List<Member> members(@PathVariable String id) {
        return teams.members(id);
    }

    @PutMapping("/teams/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void membership(@PathVariable String id, @RequestBody MembershipInput input) {
        teams.putMember(id, input);
    }

    @DeleteMapping("/teams/{id}/members/{user}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String id, @PathVariable String user) {
        teams.removeMember(id, user);
    }
}

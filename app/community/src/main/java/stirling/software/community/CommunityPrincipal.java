package stirling.software.community;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import stirling.software.community.CommunityModels.Account;
import stirling.software.community.CommunityModels.Role;

/**
 * Session identity snapshot; the request filter checks its version against current account state.
 */
public record CommunityPrincipal(
        String id,
        String username,
        String passwordHash,
        Role role,
        boolean enabled,
        boolean mustChangePassword,
        int version)
        implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public Account account() {
        return new Account(id, username, role, enabled, mustChangePassword);
    }

    @Override
    public String toString() {
        return "CommunityPrincipal[" + id + "," + username + "," + role + "]";
    }
}

package stirling.software.community;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Disabling an account or changing its password/role revokes its existing sessions and API keys.
 */
final class CommunityIdentityFilter extends OncePerRequestFilter {
    private final CommunityStore store;
    private final CommunityApiKeys keys;

    CommunityIdentityFilter(CommunityStore store, CommunityApiKeys keys) {
        this.store = store;
        this.keys = keys;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CommunityPrincipal principal) {
            boolean valid =
                    store.user(principal.id())
                            .map(user -> user.enabled() && user.version() == principal.version())
                            .orElse(false);
            if (!valid) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            }
        }
        String credential = request.getHeader("X-API-KEY");
        if (credential != null) {
            var verified = keys.authenticate(credential);
            if (verified.isEmpty()) {
                reject(response, 401, "Invalid API credential");
                return;
            }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(verified.get());
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    static void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"detail\":\"" + message + "\"}");
    }
}

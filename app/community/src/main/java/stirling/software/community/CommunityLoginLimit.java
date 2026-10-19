package stirling.software.community;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bounds failed login attempts per network peer and username without keeping an unbounded IP map.
 */
final class CommunityLoginLimit extends OncePerRequestFilter {
    private record Window(long since, int attempts) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    CommunityLoginLimit(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/api/v1/community/login".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        String key =
                request.getRemoteAddr()
                        + ":"
                        + CommunityStore.normalize(request.getParameter("username"));
        if (key.length() > 200) {
            CommunityIdentityFilter.reject(response, 400, "Invalid login request");
            return;
        }
        if (windows.size() >= 4096) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().since() >= 900_000);
            if (!windows.containsKey(key) && windows.size() >= 4096) {
                CommunityIdentityFilter.reject(response, 429, "Too many login requests");
                return;
            }
        }
        Window current =
                windows.compute(
                        key,
                        (ignored, previous) ->
                                previous == null || now - previous.since() >= 900_000
                                        ? new Window(now, 1)
                                        : new Window(previous.since(), previous.attempts() + 1));
        if (current.attempts() > 10) {
            response.setHeader("Retry-After", "900");
            CommunityIdentityFilter.reject(
                    response, 429, "Too many login attempts; try again later");
            return;
        }
        chain.doFilter(request, response);
        if (response.getStatus() < 400) {
            windows.remove(key);
        }
    }
}

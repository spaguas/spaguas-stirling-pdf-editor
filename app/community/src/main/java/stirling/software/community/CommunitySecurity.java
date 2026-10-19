package stirling.software.community;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Browser sessions require CSRF protection; PDF processing also checks current team permissions.
 */
@Configuration
@EnableMethodSecurity
public class CommunitySecurity {
    @Bean
    SecurityFilterChain communityFilterChain(
            HttpSecurity http,
            CommunityAccounts accounts,
            CommunityStore store,
            CommunityApiKeys keys,
            PasswordEncoder passwords,
            Clock clock)
            throws Exception {
        var provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwords);
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        http.authenticationProvider(provider)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(
                        config ->
                                config.csrfTokenRepository(csrf)
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers(
                                                request -> {
                                                    var authentication =
                                                            org.springframework.security.core
                                                                    .context.SecurityContextHolder
                                                                    .getContext()
                                                                    .getAuthentication();
                                                    return authentication != null
                                                            && "community-api-key"
                                                                    .equals(
                                                                            authentication
                                                                                    .getDetails());
                                                }))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/api/v1/community/session",
                                                "/api/v1/community/login",
                                                "/error")
                                        .permitAll()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/v1/info/status",
                                                "/api/v1/config/**",
                                                "/api/v1/ui-data/footer-info")
                                        .permitAll()
                                        .requestMatchers("/api/v1/settings/update-enable-analytics", "/api/v1/info/**", "/actuator/**")
                                        .access((authentication, context) -> new AuthorizationDecision(accounts.isCurrentUserAdmin() && accounts.canProcessPdf(authentication.get().getName())))
                                        .requestMatchers("/api/v1/community/**")
                                        .authenticated()
                                        .requestMatchers("/api/**")
                                        .access(
                                                (authentication, context) -> {
                                                    var identity = authentication.get();
                                                    boolean internal =
                                                            "community-api-key"
                                                                            .equals(
                                                                                    identity
                                                                                            .getDetails())
                                                                    && identity.getPrincipal()
                                                                            instanceof String
                                                                    && "community-internal"
                                                                            .equals(
                                                                                    identity
                                                                                            .getName());
                                                    boolean authenticated =
                                                            identity.isAuthenticated()
                                                                    && !(identity
                                                                            instanceof
                                                                            org.springframework
                                                                                    .security
                                                                                    .authentication
                                                                                    .AnonymousAuthenticationToken);
                                                    return new AuthorizationDecision(
                                                            internal
                                                                    || (authenticated
                                                                            && accounts
                                                                                    .canProcessPdf(
                                                                                            identity
                                                                                                    .getName())));
                                                })
                                        .anyRequest()
                                        .permitAll())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        CommunityIdentityFilter.reject(
                                                                response,
                                                                401,
                                                                "Authentication required"))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        CommunityIdentityFilter.reject(
                                                                response,
                                                                403,
                                                                "Permission denied or CSRF token missing")))
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .loginProcessingUrl("/api/v1/community/login")
                                        .successHandler(
                                                (request, response, authentication) ->
                                                        response.setStatus(204))
                                        .failureHandler(
                                                (request, response, exception) ->
                                                        CommunityIdentityFilter.reject(
                                                                response,
                                                                401,
                                                                "Invalid credentials")))
                .logout(
                        logout ->
                                logout.logoutUrl("/api/v1/community/logout")
                                        .invalidateHttpSession(true)
                                        .deleteCookies("JSESSIONID")
                                        .logoutSuccessHandler(
                                                (request, response, authentication) ->
                                                        response.setStatus(204)))
                .addFilterAfter(
                        new CommunityIdentityFilter(store, keys), SecurityContextHolderFilter.class)
                .addFilterBefore(new CommunityLoginLimit(clock), CsrfFilter.class);
        return http.build();
    }
}

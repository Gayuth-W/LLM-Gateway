package com.llmgateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

/**
 * Stateless JWT security for the operator surface.
 *
 * <p><b>What is protected and what is not.</b> Adding
 * {@code spring-boot-starter-security} locks everything down by default, which
 * would have broken three things at once: the {@code /v1/**} proxy (which
 * authenticates with team API keys, not JWTs), the Prometheus scrape endpoint
 * (Grafana would have gone flat), and the clean 404 on unmapped paths. Each is
 * permitted explicitly below.
 *
 * <p><b>Defence in depth.</b> {@code /admin/**} requires a token here at the path
 * level, and each handler additionally carries a {@code @PreAuthorize} for its
 * role. The path rule is the safety net: a new admin endpoint added later
 * without an annotation still cannot be called anonymously.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    /** Actuator endpoints that stay open so the existing scrape/health wiring keeps working. */
    private static final String[] OPEN_ACTUATOR = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/actuator/metrics/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) {

        return http
                // Stateless bearer-token API: no session, no cookies, so CSRF and the
                // browser-oriented login flows have nothing to protect and are removed.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/", "/auth/login").permitAll()
                        .pathMatchers(OPEN_ACTUATOR).permitAll()

                        // The proxy surface keeps its own auth: long-lived team API keys
                        // resolved by TeamAuthFilter. Machine clients should not have to
                        // run a login/refresh loop to call a completions endpoint.
                        .pathMatchers("/v1/**").permitAll()

                        .pathMatchers("/admin/**").authenticated()
                        .pathMatchers("/auth/me").authenticated()

                        // Anything unmapped falls through to the global error handler,
                        // which answers 404. Requiring auth here would turn every typo
                        // into a misleading 401.
                        .anyExchange().permitAll())

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * BCrypt at the default cost (10). Note that verification is CPU-bound and
     * blocking -- {@code AdminUserService} keeps it off the event loop.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

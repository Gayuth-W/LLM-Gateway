package com.llmgateway.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Turns a valid {@code Authorization: Bearer <jwt>} into a populated reactive
 * SecurityContext. Installed at {@code SecurityWebFiltersOrder.AUTHENTICATION}
 * by {@code SecurityConfig}.
 *
 * <p>Two design notes:
 *
 * <p><b>Path guard.</b> The {@code /v1/**} surface also sends a Bearer header, but
 * carrying a team API key ({@code sk-...}), not a JWT. Parsing those would be
 * pure waste and would fill the logs with bogus "invalid token" noise, so this
 * filter only looks at the surfaces that use JWTs. {@code TeamAuthFilter} owns
 * {@code /v1/**} and is untouched by this change.
 *
 * <p><b>No throwing.</b> A bad token does not raise here; it simply leaves the
 * context empty so the authorization rules produce a clean 401 through the
 * entry point. The reason is stashed on the exchange so the 401 body can say
 * "Token expired" instead of the useless "Unauthorized".
 */
@Component
public class JwtAuthenticationFilter implements WebFilter {

    /** Exchange attribute read by {@link JsonAuthenticationEntryPoint}. */
    public static final String AUTH_ERROR = "gateway.authError";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!usesJwt(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return chain.filter(exchange);
        }

        Actor actor;
        try {
            actor = jwtService.verify(header.substring(BEARER.length()).trim());
        } catch (ExpiredJwtException e) {
            exchange.getAttributes().put(AUTH_ERROR, "Token expired. Sign in again.");
            return chain.filter(exchange);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected bearer token: {}", e.getMessage());
            exchange.getAttributes().put(AUTH_ERROR, "Invalid token.");
            return chain.filter(exchange);
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                actor.username(), null, SecurityUtils.authorities(actor.role()));

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    /** Surfaces that authenticate with a JWT rather than a team API key. */
    private boolean usesJwt(String path) {
        return path.startsWith("/admin/") || path.equals("/auth/me");
    }
}

package com.llmgateway.security;

import com.llmgateway.model.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * Bridge between the domain {@link Role} and Spring Security's authority strings,
 * plus a reactive accessor for the caller behind the current request.
 */
public final class SecurityUtils {

    public static final String ROLE_PREFIX = "ROLE_";

    private SecurityUtils() {}

    /**
     * The role and everything it outranks, as {@code ROLE_*} authorities.
     * Expanding the hierarchy here is what makes {@code hasRole('VIEWER')} pass
     * for an ADMIN without any {@code RoleHierarchy} wiring.
     */
    public static List<GrantedAuthority> authorities(Role role) {
        return role.implied().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + r.name()))
                .toList();
    }

    /**
     * The authenticated caller for the in-flight request. Empty if the request is
     * anonymous, which for {@code /admin/**} cannot happen -- the filter chain
     * rejects those before a controller runs.
     */
    public static Mono<Actor> currentActor() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .map(SecurityUtils::toActor);
    }

    /** Convenience for the common case: just the name to stamp on an audit row. */
    public static Mono<String> currentUsername() {
        return currentActor().map(Actor::username);
    }

    private static Actor toActor(Authentication auth) {
        Role granted = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()).toUpperCase(Locale.ROOT))
                .map(Role::valueOf)
                // Authorities are the expanded hierarchy, so the highest one is
                // the role actually granted.
                .max(Enum::compareTo)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated principal has no ROLE_* authority"));
        return new Actor(auth.getName(), granted);
    }
}

package com.llmgateway.model.enums;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Console operator roles, ordered least- to most-privileged.
 *
 * The three tiers map onto genuinely different blast radii in the admin API:
 *   VIEWER    read-only: team config, provider health, spend reports
 *   OPERATOR  the above + tuning quotas, budgets and alert thresholds
 *   ADMIN     the above + issuing team API keys (i.e. creating a principal)
 *
 * The hierarchy is materialised here as {@link #implied()} rather than wired
 * through Spring Security's {@code RoleHierarchy}. Keeping it in the domain
 * enum makes it unit-testable and visible at the point where roles are
 * defined, instead of depending on expression-handler configuration taking
 * effect. The security layer maps these onto {@code ROLE_*} authorities.
 */
public enum Role {

    VIEWER,
    OPERATOR,
    ADMIN;

    /** This role plus every role it outranks. ADMIN &gt; OPERATOR &gt; VIEWER. */
    public Set<Role> implied() {
        return switch (this) {
            case ADMIN -> EnumSet.of(ADMIN, OPERATOR, VIEWER);
            case OPERATOR -> EnumSet.of(OPERATOR, VIEWER);
            case VIEWER -> EnumSet.of(VIEWER);
        };
    }

    /** True if this role is at least as privileged as {@code other}. */
    public boolean outranks(Role other) {
        return implied().contains(other);
    }

    /**
     * Parse a persisted or claimed role name. Unknown values are rejected
     * rather than silently downgraded, so a typo in the database or a forged
     * claim fails loudly instead of quietly granting VIEWER.
     */
    public static Role from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Role is missing");
        }
        return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}

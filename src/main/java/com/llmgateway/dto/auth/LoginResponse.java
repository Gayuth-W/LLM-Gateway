package com.llmgateway.dto.auth;

/**
 * Issued token plus the identity it carries.
 *
 * The username and role are returned alongside the token purely so the console
 * can render the right controls without decoding the JWT client-side. They are
 * a convenience, never the source of truth: every authorization decision is made
 * server-side from the signed claims.
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String username,
        String role
) {}

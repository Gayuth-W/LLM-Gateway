package com.llmgateway.security;

import com.llmgateway.model.AdminUser;
import com.llmgateway.model.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Mints and verifies the operator access tokens.
 *
 * HS256 (symmetric) rather than RS256: this service is both the only issuer and
 * the only verifier, so an asymmetric key pair plus a JWKS endpoint would buy
 * nothing. RS256 earns its keep when independent services need to verify tokens
 * without holding the signing key -- not the case here.
 *
 * There is no refresh token and no server-side revocation list. A token is valid
 * until it expires ({@code gateway.security.jwt.ttl}, 1h by default). That is a
 * deliberate scoping call: rotation plus a denylist is a lot of machinery for a
 * console with three seeded accounts, and the honest mitigation is a short TTL.
 */
@Service
public class JwtService {

    /** HS256 requires a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        byte[] secret = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);

        // Fail fast and loudly at startup. A short or missing secret is a silent
        // security hole otherwise -- jjwt would reject it later, per request.
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "gateway.security.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256 (got " + secret.length + "). "
                            + "Set GATEWAY_SECURITY_JWT_SECRET to a long random string.");
        }

        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = properties.ttl();
        this.issuer = properties.issuer();
    }

    public Duration ttl() {
        return ttl;
    }

    /** Issue a token for a successfully authenticated operator. */
    public String issue(AdminUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getUsername())
                .claim(ROLE_CLAIM, user.roleEnum().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Verify signature, issuer and expiry, and return the identity inside.
     *
     * @throws JwtException             if the token is malformed, unsigned, signed
     *                                  with the wrong key, expired, or from another issuer
     * @throws IllegalArgumentException if the role claim is absent or unrecognised
     */
    public Actor verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Token has no subject");
        }
        Role role = Role.from(claims.get(ROLE_CLAIM, String.class));
        return new Actor(username, role);
    }
}

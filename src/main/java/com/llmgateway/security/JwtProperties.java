package com.llmgateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binding for {@code gateway.security.jwt}. Picked up by the application's
 * {@code @ConfigurationPropertiesScan}.
 *
 * @param secret HMAC signing key. HS256 requires at least 32 bytes; the app
 *               refuses to start on anything shorter (see {@code JwtService}).
 * @param ttl    Access-token lifetime. There is no refresh token by design --
 *               when this expires the operator logs in again.
 * @param issuer {@code iss} claim, also verified on the way back in.
 */
@ConfigurationProperties(prefix = "gateway.security.jwt")
public record JwtProperties(
        String secret,
        Duration ttl,
        String issuer
) {
    public JwtProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "llm-gateway";
        }
    }
}

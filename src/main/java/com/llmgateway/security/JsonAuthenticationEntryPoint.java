package com.llmgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 401 handler. Without this, Spring Security returns an empty body, which would
 * be the only endpoint family in the gateway that does not speak
 * {@code ErrorResponse}. Reuses the reason recorded by
 * {@link JwtAuthenticationFilter} so an expired token says so.
 */
@Component
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        Object reason = exchange.getAttribute(JwtAuthenticationFilter.AUTH_ERROR);
        String message = reason instanceof String s ? s : "Missing bearer token. Sign in at POST /auth/login.";
        return ErrorWriter.write(exchange, objectMapper, 401, "unauthorized", message);
    }
}

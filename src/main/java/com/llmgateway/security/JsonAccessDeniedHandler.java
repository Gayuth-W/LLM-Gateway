package com.llmgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 403 handler: the caller is authenticated but their role does not reach this
 * endpoint. Catches denials from both the path rules and the {@code @PreAuthorize}
 * annotations on the admin controllers.
 *
 * <p>The message deliberately does not name the required role -- telling an
 * unauthorised caller exactly which privilege to hunt for is free reconnaissance.
 */
@Component
public class JsonAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        return ErrorWriter.write(exchange, objectMapper, 403, "forbidden",
                "Your role does not permit this operation.");
    }
}

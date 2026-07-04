package com.llmgateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Translates exceptions (including those thrown by {@code WebFilter}s, which never reach
 * an {@code @RestControllerAdvice}) into the uniform {@link ErrorResponse} envelope.
 *
 * Ordered at -2 so it runs ahead of Spring Boot's DefaultErrorWebExceptionHandler (-1).
 */
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ErrorResponse body;
        if (ex instanceof GatewayException ge) {
            body = ErrorResponse.of(ge.status(), ge.code(), ge.getMessage());
            if (ex instanceof RateLimitExceededException rl) {
                exchange.getResponse().getHeaders()
                        .add(HttpHeaders.RETRY_AFTER, String.valueOf(rl.retryAfterSeconds()));
            }
            if (ge.status() >= 500) {
                log.warn("Gateway 5xx [{}]: {}", ge.code(), ge.getMessage());
            }
        } else if (ex instanceof ServerWebInputException sie) {
            body = ErrorResponse.of(400, "bad_request", sie.getReason());
        } else if (ex instanceof ResponseStatusException rse) {
            // Covers NoResourceFoundException (unmapped routes / favicon) and any
            // ResponseStatusException we throw deliberately (e.g. 404 "Team not found").
            // Respect the exception's real status instead of masking it as a 500.
            int status = rse.getStatusCode().value();
            HttpStatus resolved = HttpStatus.resolve(status);
            String code = resolved != null ? resolved.name().toLowerCase(Locale.ROOT) : "error";
            String message = rse.getReason() != null
                    ? rse.getReason()
                    : (resolved != null ? resolved.getReasonPhrase() : "Request failed");
            body = ErrorResponse.of(status, code, message);
            if (status >= 500) {
                log.warn("Downstream error {} [{}]: {}", status, code, message);
            }
        } else {
            log.error("Unhandled error", ex);
            body = ErrorResponse.of(500, "internal_error", "An unexpected error occurred.");
        }

        exchange.getResponse().setStatusCode(HttpStatus.valueOf(body.status()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationError) {
            bytes = ("{\"status\":" + body.status() + ",\"error\":\"" + body.error() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

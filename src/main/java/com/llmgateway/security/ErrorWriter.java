package com.llmgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.dto.ErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Writes the standard {@link ErrorResponse} envelope directly to the response.
 *
 * Security rejections are produced inside the filter chain, not by a controller,
 * so they never reach {@code GlobalErrorWebExceptionHandler}. This keeps 401/403
 * bodies identical in shape to every other gateway error.
 */
final class ErrorWriter {

    private ErrorWriter() {}

    static Mono<Void> write(ServerWebExchange exchange, ObjectMapper objectMapper,
                            int status, String code, String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        ErrorResponse body = ErrorResponse.of(status, code, message);
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationError) {
            bytes = ("{\"status\":" + status + ",\"error\":\"" + code + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

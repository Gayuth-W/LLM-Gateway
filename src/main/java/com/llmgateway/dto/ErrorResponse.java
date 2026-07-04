package com.llmgateway.dto;

import java.time.Instant;

/** Consistent error envelope returned for every non-2xx gateway response. */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}

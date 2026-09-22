package com.llmgateway.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Credentials posted to {@code POST /auth/login}. */
public record LoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password
) {}

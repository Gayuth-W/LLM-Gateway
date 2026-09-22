package com.llmgateway.dto.auth;

/** Identity behind the presented token. Used by the console to re-hydrate a session. */
public record MeResponse(String username, String role) {}

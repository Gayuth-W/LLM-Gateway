package com.llmgateway.dto;

/**
 * Unified non-streaming response. {@code providerUsed} reflects the model that
 * actually served the request (may differ from the requested model after fallback).
 */
public record LLMResponse(
        String model,          // the model that served the request
        String content,        // assistant message text
        int inputTokens,
        int outputTokens,
        long latencyMs,
        boolean fallbackTriggered
) {
    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}

package com.llmgateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The provider-agnostic request that every gateway endpoint accepts.
 * No provider-specific types leak past this boundary.
 */
public record LLMRequest(

        @NotNull
        String model,

        @NotEmpty
        List<ChatMessage> messages,

        boolean stream,

        @JsonProperty("max_tokens")
        Integer maxTokens,

        Double temperature
) {
    /** Defensive copy + sensible defaults so downstream code never sees nulls it cannot handle. */
    public LLMRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public LLMRequest withModel(String newModel) {
        return new LLMRequest(newModel, messages, stream, maxTokens, temperature);
    }

    public LLMRequest withMessages(List<ChatMessage> newMessages) {
        return new LLMRequest(model, newMessages, stream, maxTokens, temperature);
    }
}

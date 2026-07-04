package com.llmgateway.dto.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Request body sent to Ollama's POST /api/chat endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
        String model,
        List<OllamaMessage> messages,
        boolean stream,
        OllamaOptions options
) {}

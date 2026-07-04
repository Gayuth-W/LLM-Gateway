package com.llmgateway.dto.ollama;

/** Ollama /api/chat message format. */
public record OllamaMessage(String role, String content) {}

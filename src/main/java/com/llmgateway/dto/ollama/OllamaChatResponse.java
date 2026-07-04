package com.llmgateway.dto.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One line of an Ollama /api/chat response. In streaming mode Ollama emits one of
 * these per token-ish chunk (NDJSON). The final line has {@code done == true} and
 * includes prompt/eval token counts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
        String model,
        OllamaMessage message,
        boolean done,
        @JsonProperty("prompt_eval_count") Integer promptEvalCount,
        @JsonProperty("eval_count") Integer evalCount
) {
    public String contentOrEmpty() {
        return message == null || message.content() == null ? "" : message.content();
    }

    public int inputTokens() {
        return promptEvalCount == null ? 0 : promptEvalCount;
    }

    public int outputTokens() {
        return evalCount == null ? 0 : evalCount;
    }
}

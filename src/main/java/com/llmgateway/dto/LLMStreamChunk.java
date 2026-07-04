package com.llmgateway.dto;

/**
 * One streamed delta. The terminal chunk has {@code done == true} and carries the
 * final token accounting (Ollama reports prompt_eval_count / eval_count on completion).
 */
public record LLMStreamChunk(
        String model,
        String contentDelta,
        boolean done,
        int inputTokens,    // populated only when done == true
        int outputTokens    // populated only when done == true
) {
    public static LLMStreamChunk delta(String model, String text) {
        return new LLMStreamChunk(model, text, false, 0, 0);
    }

    public static LLMStreamChunk terminal(String model, int inputTokens, int outputTokens) {
        return new LLMStreamChunk(model, "", true, inputTokens, outputTokens);
    }
}

package com.llmgateway.provider;

import com.llmgateway.dto.LLMRequest;
import com.llmgateway.dto.LLMResponse;
import com.llmgateway.dto.LLMStreamChunk;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The single abstraction every backend implements. Nothing past this interface
 * sees provider-specific request/response shapes. This build ships one
 * implementation ({@link OllamaProvider}); adding a hosted provider later is just
 * another class behind this interface, registered in the {@link ProviderRegistry}.
 */
public interface LLMProvider {

    /** Stable provider id (e.g. "ollama"). */
    String name();

    /** Non-streaming completion. */
    Mono<LLMResponse> complete(LLMRequest request);

    /** Streaming completion. The terminal chunk carries final token counts. */
    Flux<LLMStreamChunk> stream(LLMRequest request);

    /**
     * Minimal liveness probe for a specific model, used by the health service.
     * Completes empty on success and errors on failure.
     */
    Mono<Void> ping(String model);
}

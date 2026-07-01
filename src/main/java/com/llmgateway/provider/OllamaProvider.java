package com.llmgateway.provider;

import com.llmgateway.config.GatewayProperties;
import com.llmgateway.dto.ChatMessage;
import com.llmgateway.dto.LLMRequest;
import com.llmgateway.dto.LLMResponse;
import com.llmgateway.dto.LLMStreamChunk;
import com.llmgateway.dto.ollama.OllamaChatRequest;
import com.llmgateway.dto.ollama.OllamaChatResponse;
import com.llmgateway.dto.ollama.OllamaMessage;
import com.llmgateway.dto.ollama.OllamaOptions;
import com.llmgateway.exception.UpstreamAuthException;
import com.llmgateway.exception.UpstreamException;
import com.llmgateway.exception.UpstreamRateLimitException;
import com.llmgateway.exception.UpstreamTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Talks to a local Ollama server via POST /api/chat. Maps the unified request to
 * Ollama's wire format and normalises every failure into the gateway's
 * {@code Upstream*Exception} taxonomy so retry/fallback logic is provider-agnostic.
 */
@Component
public class OllamaProvider implements LLMProvider {

    private final WebClient ollamaWebClient;
    private final GatewayProperties props;

    public OllamaProvider(WebClient ollamaWebClient, GatewayProperties props) {
        this.ollamaWebClient = ollamaWebClient;
        this.props = props;
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public Mono<LLMResponse> complete(LLMRequest request) {
        long start = System.nanoTime();
        OllamaChatRequest body = toOllama(request, false);
        return ollamaWebClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OllamaChatResponse.class)
                .timeout(props.ollama().requestTimeout())
                .map(resp -> new LLMResponse(
                        request.model(),
                        resp.contentOrEmpty(),
                        resp.inputTokens(),
                        resp.outputTokens(),
                        elapsedMs(start),
                        false))
                .onErrorMap(this::mapError);
    }

    @Override
    public Flux<LLMStreamChunk> stream(LLMRequest request) {
        OllamaChatRequest body = toOllama(request, true);
        // Ollama streams newline-delimited JSON (application/x-ndjson). Spring's
        // non-blocking Jackson decoder tokenises one OllamaChatResponse per line,
        // correctly across network buffer boundaries.
        return ollamaWebClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(OllamaChatResponse.class)
                .timeout(props.ollama().requestTimeout())
                .map(resp -> resp.done()
                        ? LLMStreamChunk.terminal(request.model(), resp.inputTokens(), resp.outputTokens())
                        : LLMStreamChunk.delta(request.model(), resp.contentOrEmpty()))
                .onErrorMap(this::mapError);
    }

    @Override
    public Mono<Void> ping(String model) {
        OllamaChatRequest body = new OllamaChatRequest(
                model,
                List.of(new OllamaMessage("user", "ping")),
                false,
                new OllamaOptions(0.0, props.resilience().healthCheck().pingNumPredict()));
        return ollamaWebClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OllamaChatResponse.class)
                .timeout(props.ollama().requestTimeout())
                .then()
                .onErrorMap(this::mapError);
    }

    // ---- mapping helpers ----

    private OllamaChatRequest toOllama(LLMRequest request, boolean stream) {
        List<OllamaMessage> messages = request.messages().stream()
                .map(this::toOllamaMessage)
                .toList();
        OllamaOptions options = new OllamaOptions(request.temperature(), request.maxTokens());
        return new OllamaChatRequest(request.model(), messages, stream, options);
    }

    private OllamaMessage toOllamaMessage(ChatMessage m) {
        return new OllamaMessage(m.role(), m.content());
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Normalise transport/HTTP failures into the gateway taxonomy. The retryable
     * flag on each exception is what drives retry-vs-fail-fast downstream.
     */
    private Throwable mapError(Throwable t) {
        if (t instanceof UpstreamException) {
            return t; // already normalised
        }
        if (t instanceof TimeoutException) {
            return new UpstreamTimeoutException("Ollama request timed out", t);
        }
        if (t instanceof WebClientResponseException w) {
            int sc = w.getStatusCode().value();
            if (sc == 429) {
                return new UpstreamRateLimitException("Ollama returned 429");
            }
            if (sc == 401 || sc == 403) {
                return new UpstreamAuthException("Ollama auth rejected (" + sc + ")");
            }
            if (sc == 404) {
                // Model not loaded/pulled: retrying the same model is pointless -> fail fast,
                // which lets the router fall back to another model.
                return new UpstreamException("Ollama model unavailable (404): " + w.getResponseBodyAsString(), false);
            }
            // 5xx and anything else: transient, retryable.
            return new UpstreamException("Ollama HTTP " + sc, true);
        }
        if (t instanceof WebClientRequestException) {
            // Connection refused / reset / DNS: transient.
            return new UpstreamException("Ollama connection error: " + t.getMessage(), true);
        }
        return new UpstreamException("Ollama call failed: " + t.getMessage(), true);
    }
}

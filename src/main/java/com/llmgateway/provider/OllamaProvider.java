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

}

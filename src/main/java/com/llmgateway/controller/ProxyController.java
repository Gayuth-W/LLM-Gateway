package com.llmgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmgateway.budget.BudgetService;
import com.llmgateway.budget.CostCalculator;
import com.llmgateway.config.GatewayProperties;
import com.llmgateway.dto.ChatMessage;
import com.llmgateway.dto.LLMRequest;
import com.llmgateway.dto.LLMResponse;
import com.llmgateway.exception.BudgetExhaustedException;
import com.llmgateway.exception.ModelNotAllowedException;
import com.llmgateway.exception.RateLimitExceededException;
import com.llmgateway.model.Team;
import com.llmgateway.model.enums.RequestPriority;
import com.llmgateway.observability.GatewayMetrics;
import com.llmgateway.observability.SpanAttributes;
import com.llmgateway.observability.Telemetry;
import com.llmgateway.provider.ProviderRegistry;
import com.llmgateway.ratelimit.RateLimiterService;
import com.llmgateway.resilience.FallbackRouter;
import com.llmgateway.service.ContentFilterService;
import com.llmgateway.service.EnrichmentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single public inference surface, OpenAI-shaped at {@code POST /v1/chat/completions}.
 *
 * Per-request pipeline (auth already done by the WebFilter):
 *   live budget guard -> content screen -> model-allow check -> token-aware rate-limit
 *   admission -> enrichment -> fallback-routed provider call -> disclaimer ->
 *   usage/cost accounting + token reconciliation + metrics.
 *
 * Both streaming (SSE) and non-streaming responses are produced here based on the
 * request's {@code stream} flag.
 */
@RestController
@RequestMapping("/v1")
public class ProxyController {

    private static final String PROVIDER = "ollama";
    private final EnrichmentService enrichment;
    private final RateLimiterService rateLimiter;
    private final BudgetService budget;
    private final CostCalculator costCalculator;
    private final FallbackRouter router;
    private final GatewayMetrics metrics;
    private final Telemetry telemetry;
    private final ObjectMapper json;

    public ProxyController(GatewayProperties props, ProviderRegistry registry,
                           ContentFilterService contentFilter, EnrichmentService enrichment,
                           RateLimiterService rateLimiter, BudgetService budget,
                           CostCalculator costCalculator, FallbackRouter router,
                           GatewayMetrics metrics, Telemetry telemetry, ObjectMapper json) {

        this.enrichment = enrichment;
        this.rateLimiter = rateLimiter;
        this.budget = budget;
        this.costCalculator = costCalculator;
        this.router = router;
        this.metrics = metrics;
        this.telemetry = telemetry;
        this.json = json;
    }

    // ---------- non-streaming ----------

    private Mono<ResponseEntity<?>> syncResponse(Team team, LLMRequest requested, int estimate) {
        LLMRequest enriched = enrichment.enrichRequest(team, requested);
        long start = System.nanoTime();
        return router.route(team, enriched)
                .flatMap(resp -> finalizeSync(team, requested, resp, estimate, start));
    }
    
    private Mono<ResponseEntity<?>> finalizeSync(Team team, LLMRequest requested, LLMResponse resp,
                                                 int estimate, long startNanos) {
        String served = resp.model();
        String content = enrichment.applyDisclaimer(team, resp.content());
        BigDecimal cost = costCalculator.cost(served, resp.inputTokens(), resp.outputTokens());
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        Map<String, Object> bodyJson = openAiResponse(served, content, resp.inputTokens(), resp.outputTokens());

        return budget.recordUsage(team, served, PROVIDER, resp.inputTokens(), resp.outputTokens(), cost)
                .then(rateLimiter.reconcileTokenUsage(team, estimate, resp.totalTokens()))
                .then(Mono.fromRunnable(() -> {
                    metrics.recordRequest(team.getName(), requested.model(), served, PROVIDER, "success");
                    metrics.recordLatency(served, PROVIDER, latencyMs);
                    metrics.recordTokens(team.getName(), served, resp.inputTokens(), resp.outputTokens());
                    metrics.recordCost(team.getName(), served, cost);
                    telemetry.tag(SpanAttributes.SERVED_MODEL, served);
                    telemetry.tag(SpanAttributes.FALLBACK, Boolean.toString(resp.fallbackTriggered()));
                    telemetry.tag(SpanAttributes.INPUT_TOKENS, resp.inputTokens());
                    telemetry.tag(SpanAttributes.OUTPUT_TOKENS, resp.outputTokens());
                }))
                .thenReturn(ResponseEntity.ok()
                        .header("X-Provider-Used", served)
                        .header("X-Gateway-Fallback", Boolean.toString(resp.fallbackTriggered()))
                        .header("X-Request-Tokens", Integer.toString(resp.totalTokens()))
                        .body((Object) bodyJson));
    }
}

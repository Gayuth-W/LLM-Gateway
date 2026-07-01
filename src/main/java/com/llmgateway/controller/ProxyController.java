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
}

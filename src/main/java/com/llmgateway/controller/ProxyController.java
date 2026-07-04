package com.llmgateway.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private static final int DEFAULT_MAX_TOKENS = 256;

    private final GatewayProperties props;
    private final ProviderRegistry registry;
    private final ContentFilterService contentFilter;
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
        this.props = props;
        this.registry = registry;
        this.contentFilter = contentFilter;
        this.enrichment = enrichment;
        this.rateLimiter = rateLimiter;
        this.budget = budget;
        this.costCalculator = costCalculator;
        this.router = router;
        this.metrics = metrics;
        this.telemetry = telemetry;
        this.json = json;
    }

    @PostMapping(value = "/chat/completions",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Mono<ResponseEntity<?>> chatCompletions(ServerWebExchange exchange,
                                                   @Valid @RequestBody LLMRequest body) {
        Team team = exchange.getAttribute(com.llmgateway.filter.RequestContext.TEAM);
        RequestPriority priority = exchange.getAttributeOrDefault(
                com.llmgateway.filter.RequestContext.PRIORITY, RequestPriority.HIGH);
        LLMRequest request = normalizeModel(body);

        telemetry.tag(SpanAttributes.TEAM_NAME, team.getName());
        telemetry.tag(SpanAttributes.REQUESTED_MODEL, request.model());
        telemetry.tag(SpanAttributes.PRIORITY, priority.name());
        telemetry.tag(SpanAttributes.STREAMING, Boolean.toString(request.stream()));

        return preflight(team, priority, request).flatMap(estimatedTokens ->
                request.stream()
                        ? Mono.just(streamingResponse(team, request, estimatedTokens))
                        : syncResponse(team, request, estimatedTokens));
    }

    // ---------- pre-call gates (shared by both paths) ----------

    /** Runs budget/content/model/rate-limit gates; returns the admitted token estimate. */
    private Mono<Integer> preflight(Team team, RequestPriority priority, LLMRequest request) {
        int estimate = estimateTokens(request);
        return budgetGuard(team)
                .then(contentFilter.screen(request))
                .then(Mono.defer(() -> ensureModelAllowed(team, request)))
                .then(Mono.defer(() -> rateLimiter.tryAdmit(team, priority, estimate)))
                .flatMap(decision -> decision.allowed()
                        ? Mono.just(estimate)
                        : Mono.error(new RateLimitExceededException(
                                "Rate limit exceeded", decision.retryAfterSeconds())));
    }

    private Mono<Void> budgetGuard(Team team) {
        BigDecimal cap = team.getDailyBudgetUsd();
        if (cap == null || cap.signum() <= 0) {
            return Mono.empty();
        }
        return budget.spentToday(team.getId())
                .flatMap(spent -> spent.compareTo(cap) >= 0
                        ? Mono.error(new BudgetExhaustedException(
                                "Daily budget exhausted for team " + team.getName()))
                        : Mono.empty());
    }

    private Mono<Void> ensureModelAllowed(Team team, LLMRequest request) {
        String model = request.model();
        if (!registry.isKnown(model)) {
            return Mono.error(new ModelNotAllowedException("Unknown model: " + model));
        }
        if (!team.allows(model)) {
            return Mono.error(new ModelNotAllowedException(
                    "Team " + team.getName() + " is not allowed to use model " + model));
        }
        return Mono.empty();
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

    // ---------- streaming (SSE) ----------

    private ResponseEntity<?> streamingResponse(Team team, LLMRequest requested, int estimate) {
        Flux<ServerSentEvent<String>> body = streamBody(team, requested, estimate);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("X-Streaming", "true")
                .body(body);
    }

    private Flux<ServerSentEvent<String>> streamBody(Team team, LLMRequest requested, int estimate) {
        LLMRequest enriched = enrichment.enrichRequest(team, requested);
        String disclaimer = enrichment.disclaimer(team);
        long start = System.nanoTime();
        AtomicReference<String> served = new AtomicReference<>(requested.model());
        AtomicInteger inputTokens = new AtomicInteger();
        AtomicInteger outputTokens = new AtomicInteger();

        Flux<ServerSentEvent<String>> deltas = router.routeStream(team, enriched)
                .flatMap(chunk -> {
                    served.set(chunk.model());
                    if (chunk.done()) {
                        inputTokens.set(chunk.inputTokens());
                        outputTokens.set(chunk.outputTokens());
                        List<ServerSentEvent<String>> tail = new ArrayList<>();
                        if (disclaimer != null && !disclaimer.isBlank()) {
                            tail.add(sse(chunkJson(chunk.model(), disclaimer, null)));
                        }
                        tail.add(sse(chunkJson(chunk.model(), "", "stop")));
                        return Flux.fromIterable(tail);
                    }
                    return Flux.just(sse(chunkJson(chunk.model(), chunk.contentDelta(), null)));
                })
                .onErrorResume(err -> Flux.just(sseEvent("error", errorJson(err))));

        return deltas
                .concatWith(Flux.just(sseDone()))
                .doOnComplete(() -> finalizeStream(
                        team, requested.model(), served.get(),
                        inputTokens.get(), outputTokens.get(), estimate, start).subscribe());
    }

    private Mono<Void> finalizeStream(Team team, String requestedModel, String served,
                                      int inTok, int outTok, int estimate, long startNanos) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (inTok == 0 && outTok == 0) {
            // Stream failed before producing output; record an error and stop.
            metrics.recordRequest(team.getName(), requestedModel, served, PROVIDER, "error");
            return Mono.empty();
        }
        BigDecimal cost = costCalculator.cost(served, inTok, outTok);
        return budget.recordUsage(team, served, PROVIDER, inTok, outTok, cost)
                .then(rateLimiter.reconcileTokenUsage(team, estimate, (long) inTok + outTok))
                .then(Mono.fromRunnable(() -> {
                    metrics.recordRequest(team.getName(), requestedModel, served, PROVIDER, "success");
                    metrics.recordLatency(served, PROVIDER, latencyMs);
                    metrics.recordTokens(team.getName(), served, inTok, outTok);
                    metrics.recordCost(team.getName(), served, cost);
                }))
                .then();
    }

    // ---------- helpers ----------

    private LLMRequest normalizeModel(LLMRequest request) {
        if (request.model() != null && !request.model().isBlank()) {
            return request;
        }
        List<String> models = props.modelNames();
        String fallback = models.isEmpty() ? "llama3.1" : models.get(0);
        return request.withModel(fallback);
    }

    private int estimateTokens(LLMRequest request) {
        int estimate = 0;
        for (ChatMessage message : request.messages()) {
            if (message.content() != null) {
                estimate += message.content().length() / 4; // ~4 chars/token heuristic
            }
        }
        estimate += request.maxTokens() != null ? request.maxTokens() : DEFAULT_MAX_TOKENS;
        return Math.max(1, estimate);
    }

    private Map<String, Object> openAiResponse(String model, String content, int inTok, int outTok) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", inTok);
        usage.put("completion_tokens", outTok);
        usage.put("total_tokens", inTok + outTok);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", "chatcmpl-" + UUID.randomUUID());
        response.put("object", "chat.completion");
        response.put("model", model);
        response.put("choices", List.of(choice));
        response.put("usage", usage);
        return response;
    }

    /** One streaming chunk in OpenAI chat.completion.chunk shape. */
    private String chunkJson(String model, String contentDelta, String finishReason) {
        Map<String, Object> delta = new LinkedHashMap<>();
        if (contentDelta != null && !contentDelta.isEmpty()) {
            delta.put("content", contentDelta);
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);

        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", "chatcmpl-" + UUID.randomUUID());
        chunk.put("object", "chat.completion.chunk");
        chunk.put("model", model);
        chunk.put("choices", List.of(choice));
        return write(chunk);
    }

    private String errorJson(Throwable err) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage());
        error.put("type", "upstream_error");
        return write(Map.of("error", error));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ServerSentEvent<String> sse(String data) {
        return ServerSentEvent.<String>builder().data(data).build();
    }

    private ServerSentEvent<String> sseEvent(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private ServerSentEvent<String> sseDone() {
        return ServerSentEvent.<String>builder().data("[DONE]").build();
    }
}

package com.llmgateway.resilience;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.llmgateway.config.GatewayProperties;
import com.llmgateway.dto.LLMRequest;
import com.llmgateway.dto.LLMResponse;
import com.llmgateway.dto.LLMStreamChunk;
import com.llmgateway.exception.ProviderUnavailableException;
import com.llmgateway.model.FallbackEvent;
import com.llmgateway.model.Team;
import com.llmgateway.model.enums.ProviderStatus;
import com.llmgateway.observability.GatewayMetrics;
import com.llmgateway.provider.LLMProvider;
import com.llmgateway.provider.ProviderRegistry;
import com.llmgateway.repository.FallbackEventRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Resolves which model actually serves a request and executes the call with
 * retry + circuit breaking, falling back along the configured chain on failure.
 *
 * Candidate ordering (the DSA centrepiece) is a breadth-first traversal of the
 * fallback graph starting at the requested model, using a visited set to dedup and
 * to make cyclic chains (A->B, B->A) safe. DOWN models are filtered out.
 *
 * Per attempt: CircuitBreaker (inner) + Retry (outer) for non-streaming; streaming
 * uses the breaker only and can fall back solely before the first byte is emitted
 * (you cannot un-send a partial stream).
 */
@Service
public class FallbackRouter {

    private static final Logger log = LoggerFactory.getLogger(FallbackRouter.class);

    private final ProviderRegistry registry;
    private final GatewayProperties props;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final ProviderHealthService health;
    private final FallbackEventRepository fallbackRepo;
    private final GatewayMetrics metrics;

    public FallbackRouter(ProviderRegistry registry,
                          GatewayProperties props,
                          CircuitBreakerRegistry circuitBreakerRegistry,
                          RetryRegistry retryRegistry,
                          ProviderHealthService health,
                          FallbackEventRepository fallbackRepo,
                          GatewayMetrics metrics) {
        this.registry = registry;
        this.props = props;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.health = health;
        this.fallbackRepo = fallbackRepo;
        this.metrics = metrics;
    }

    // ---------- candidate resolution (BFS over the fallback graph) ----------

    /** Ordered, de-duplicated, cycle-safe list of models to try, DOWN models removed. */
    //Builds an ordered list of candidate models using Breadth-First Search 
    List<String> healthyCandidates(String requested) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(requested);
        visited.add(requested);
        while (!queue.isEmpty()) {
            String model = queue.poll();
            if (registry.isKnown(model)) {
                order.add(model);
            }
            for (String next : props.fallbackChain(model)) {
                if (visited.add(next)) {     // visited set prevents cycles + dups
                    queue.add(next);
                }
            }
        }
        // Keep models that are not DOWN; preserve discovery order.
        List<String> healthy = new ArrayList<>();
        for (String model : order) {
            if (health.status(model) != ProviderStatus.DOWN) {
                healthy.add(model);
            }
        }
        return healthy;
    }

    // ---------- non-streaming ----------

    //Entry point for non-streaming requests.
    //Resolves all healthy candidate models and begins the fallback execution chain
    public Mono<LLMResponse> route(Team team, LLMRequest request) {
        List<String> candidates = healthyCandidates(request.model());
        if (candidates.isEmpty()) {
            return Mono.error(new ProviderUnavailableException(
                    "No healthy provider for model " + request.model()));
        }
        return attemptChain(team, request.model(), candidates, 0, request);
    }

    private Mono<LLMResponse> attemptChain(Team team, String requested,
                                           List<String> candidates, int idx, LLMRequest request) {
        if (idx >= candidates.size()) {
            return Mono.error(new ProviderUnavailableException(
                    "All providers exhausted for model " + requested));
        }
        String candidate = candidates.get(idx);
        boolean isFallback = !candidate.equals(requested);

        LLMProvider provider = registry.forModel(candidate);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(candidate); //It protects the individual candidate model. If a specific model, if fails repeatedly, the circuit breaker opens and instantly trips any future requests to that model with a CallNotPermittedException
        Retry retry = retryRegistry.retry(candidate);

        return provider.complete(request.withModel(candidate))
                .transformDeferred(CircuitBreakerOperator.of(cb))   // inner: records each attempt
                .transformDeferred(RetryOperator.of(retry))          // outer: retries retryable failures
                .map(resp -> new LLMResponse(candidate, resp.content(),
                        resp.inputTokens(), resp.outputTokens(), resp.latencyMs(), isFallback))
                .delayUntil(resp -> health.recordOutcome(candidate, true, resp.latencyMs()))
                .delayUntil(resp -> isFallback
                        ? recordFallback(team, requested, candidate)
                        : Mono.empty())
                .onErrorResume(err -> health.recordOutcome(candidate, false, 0L)
                        .then(Mono.defer(() -> {
                            log.warn("Model {} failed ({}); trying next candidate", candidate, err.toString());
                            return attemptChain(team, requested, candidates, idx + 1, request);
                        })));
    }

    // ---------- streaming ----------

    //Entry point for streaming requests. Resolves healthy candidate models and begins the streaming fallback chain.
    public Flux<LLMStreamChunk> routeStream(Team team, LLMRequest request) {
        List<String> candidates = healthyCandidates(request.model());
        if (candidates.isEmpty()) {
            return Flux.error(new ProviderUnavailableException(
                    "No healthy provider for model " + request.model()));
        }
        return streamChain(team, request.model(), candidates, 0, request);
    }

    //Attempts to stream responses from candidate models sequentially.
    private Flux<LLMStreamChunk> streamChain(Team team, String requested,
                                             List<String> candidates, int idx, LLMRequest request) {
        if (idx >= candidates.size()) {
            return Flux.error(new ProviderUnavailableException(
                    "All providers exhausted for model " + requested));
        }
        String candidate = candidates.get(idx);
        boolean isFallback = !candidate.equals(requested);

        LLMProvider provider = registry.forModel(candidate);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(candidate);
        AtomicBoolean emitted = new AtomicBoolean(false);

        Flux<LLMStreamChunk> attempt = provider.stream(request.withModel(candidate))
                .doOnNext(c -> emitted.set(true))
                .transformDeferred(CircuitBreakerOperator.of(cb))   // no retry: replay would duplicate output
                .doOnComplete(() -> health.recordOutcome(candidate, true, 0L).subscribe());

        if (isFallback) {
            attempt = attempt.doOnSubscribe(s -> recordFallback(team, requested, candidate).subscribe());
        }

        return attempt.onErrorResume(err -> {
            if (emitted.get()) {
                // Bytes already on the wire; cannot transparently fall back.
                return Flux.error(err);
            }
            log.warn("Streaming model {} failed before first chunk ({}); falling back", candidate, err.toString());
            return health.recordOutcome(candidate, false, 0L)
                    .thenMany(streamChain(team, requested, candidates, idx + 1, request));
        });
    }

}

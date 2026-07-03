package com.llmgateway.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.llmgateway.config.GatewayProperties;
import com.llmgateway.observability.GatewayMetrics;
import com.llmgateway.provider.ProviderRegistry;
import com.llmgateway.repository.FallbackEventRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;

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

}

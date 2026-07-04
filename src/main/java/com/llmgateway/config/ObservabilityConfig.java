package com.llmgateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * Binds the programmatic Resilience4j registries to Micrometer (so breaker state,
 * failure rate, retry counts, etc. land in Prometheus) and enables automatic Reactor
 * context propagation so trace context flows across operator boundaries.
 */
@Configuration
public class ObservabilityConfig {

    public ObservabilityConfig(CircuitBreakerRegistry circuitBreakerRegistry,
                               RetryRegistry retryRegistry,
                               MeterRegistry meterRegistry) {
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry).bindTo(meterRegistry);
        TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry);
    }

    @PostConstruct
    void enableContextPropagation() {
        // Propagate tracing (and other ThreadLocal) context automatically across the
        // reactive chain. Required for correct spans through flatMap/transformDeferred.
        Hooks.enableAutomaticContextPropagation();
    }
}

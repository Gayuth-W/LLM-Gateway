package com.llmgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.llmgateway.exception.UpstreamException;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Programmatic Resilience4j registries. Per-model instances are obtained by name
 * (the model name) at call time, so every model gets its own independent breaker
 * and retry budget.
 *
 *  - A breaker only counts {@link UpstreamException}s as failures; client errors
 *    (auth, budget, rate-limit) never trip a breaker.
 *  - Retry only fires for upstream failures explicitly marked retryable, with
 *    exponential backoff.
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(GatewayProperties props) {
        var cb = props.resilience().circuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.slidingWindowSize())
                .failureRateThreshold(cb.failureRateThreshold())
                .minimumNumberOfCalls(cb.minimumNumberOfCalls())
                .waitDurationInOpenState(cb.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(cb.permittedCallsInHalfOpen())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(t -> t instanceof UpstreamException)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public RetryRegistry retryRegistry(GatewayProperties props) {
        var r = props.resilience().retry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(r.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        r.initialBackoff().toMillis(), r.multiplier()))
                .retryOnException(t -> t instanceof UpstreamException ue && ue.isRetryable())
                .build();
        return RetryRegistry.of(config);
    }
}

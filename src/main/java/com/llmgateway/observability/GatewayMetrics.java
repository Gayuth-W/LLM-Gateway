package com.llmgateway.observability;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Business/SLO metrics published to Prometheus via Micrometer. Counters and timers are
 * tagged by team/model/provider so Grafana can slice RPS, error rate, latency, token
 * throughput, cost, and fallback rate per consumer.
 *
 * (Circuit-breaker and retry metrics are bound separately from the Resilience4j
 * registries in ObservabilityConfig.)
 */
@Component
public class GatewayMetrics {

    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String teamName, String requestedModel, String servedModel,
                              String provider, String outcome) {
        Counter.builder("gateway.requests")
                .description("Total gateway requests")
                .tag("team", teamName)
                .tag("requested_model", requestedModel)
                .tag("served_model", servedModel)
                .tag("provider", provider)
                .tag("outcome", outcome) // success | error | rate_limited | budget_exhausted
                .register(registry)
                .increment();
    }

    public void recordLatency(String servedModel, String provider, long millis) {
        Timer.builder("gateway.request.latency")
                .description("End-to-end gateway latency")
                .tag("served_model", servedModel)
                .tag("provider", provider)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordTokens(String teamName, String servedModel, long input, long output) {
        registry.counter("gateway.tokens.input", "team", teamName, "served_model", servedModel)
                .increment(input);
        registry.counter("gateway.tokens.output", "team", teamName, "served_model", servedModel)
                .increment(output);
    }

    public void recordCost(String teamName, String servedModel, BigDecimal costUsd) {
        registry.counter("gateway.cost.usd", "team", teamName, "served_model", servedModel)
                .increment(costUsd.doubleValue());
    }

    public void recordFallback(String requestedModel, String servedModel) {
        registry.counter("gateway.fallback",
                "requested_model", requestedModel, "served_model", servedModel)
                .increment();
    }
}

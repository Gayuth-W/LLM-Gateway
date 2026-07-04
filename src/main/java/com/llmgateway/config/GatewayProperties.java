package com.llmgateway.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding of the {@code gateway:} section of application.yml.
 * Everything the policy layer needs is here and is hot-reloadable in principle
 * (a Spring Cloud Config refresh would re-bind these beans).
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        Ollama ollama,
        List<ModelDef> models,
        Map<String, Price> pricing,
        Map<String, List<String>> fallbackChains,
        Enrichment enrichment,
        Resilience resilience,
        Budget budget,
        Alerts alerts
) {

    public record Ollama(
            String baseUrl,
            Duration requestTimeout,
            Duration connectTimeout
    ) {}

    public record ModelDef(String name, String tier) {}

    public record Price(double inputPer1k, double outputPer1k) {}

    public record Enrichment(Map<String, Profile> profiles) {
        public record Profile(String systemPrefix, String disclaimerSuffix) {}

        public Profile profile(String name) {
            if (name == null || profiles == null) {
                return null;
            }
            return profiles.get(name);
        }
    }

    public record Resilience(HealthCheck healthCheck, Retry retry, CircuitBreaker circuitBreaker) {
        public record HealthCheck(
                Duration interval,
                Duration rollingWindow,
                double degradedErrorRate,
                double downErrorRate,
                int pingNumPredict
        ) {}

        public record Retry(
                int maxAttempts,
                Duration initialBackoff,
                double multiplier,
                Duration maxBackoff
        ) {}

        public record CircuitBreaker(
                int slidingWindowSize,
                float failureRateThreshold,
                int minimumNumberOfCalls,
                Duration waitDurationInOpenState,
                int permittedCallsInHalfOpen
        ) {}
    }

    public record Budget(Duration flushInterval, int warnThresholdPct) {}

    public record Alerts(Slack slack) {
        public record Slack(String webhookUrl) {}
    }

    // ---- convenience accessors ----

    public List<String> modelNames() {
        return models == null ? List.of() : models.stream().map(ModelDef::name).toList();
    }

    public List<String> fallbackChain(String model) {
        if (fallbackChains == null) {
            return List.of();
        }
        return fallbackChains.getOrDefault(model, List.of());
    }

    public Price priceFor(String model) {
        Price p = pricing == null ? null : pricing.get(model);
        return p == null ? new Price(0.0, 0.0) : p;
    }
}

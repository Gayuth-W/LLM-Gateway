package com.llmgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LLM API Gateway.
 *
 * A non-blocking (Spring WebFlux) proxy that sits in front of all LLM traffic and provides:
 *   - per-team authentication, model allow-listing, and request enrichment (Phase 1)
 *   - distributed token-bucket rate limiting and USD budget enforcement (Phase 2)
 *   - health checking, fallback routing, retry and circuit breaking (Phase 3)
 *   - OpenTelemetry tracing + Micrometer/Prometheus metrics (Phase 4)
 *
 * This build targets LOCAL Ollama models only.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class LlmGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmGatewayApplication.class, args);
    }
}

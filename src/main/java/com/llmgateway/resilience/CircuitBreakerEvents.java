package com.llmgateway.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.llmgateway.model.CircuitBreakerEvent;
import com.llmgateway.repository.CircuitBreakerEventRepository;
import com.llmgateway.service.AlertService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;

/**
 * Bridges Resilience4j circuit-breaker state transitions into persistence + alerting.
 * Kept separate from {@code Resilience4jConfig} (which builds the registry) to avoid a
 * bean cycle: this component depends on the registry plus the alert/persistence beans.
 *
 * A registry-level listener attaches a per-breaker transition listener as breakers are
 * created on first use (one breaker per model, created lazily by the router).
 */
@Component
public class CircuitBreakerEvents {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEvents.class);

    private final CircuitBreakerRegistry registry;
    private final CircuitBreakerEventRepository repository;
    private final AlertService alertService;

    public CircuitBreakerEvents(CircuitBreakerRegistry registry,
                                CircuitBreakerEventRepository repository,
                                AlertService alertService) {
        this.registry = registry;
        this.repository = repository;
        this.alertService = alertService;
    }

    
    /**
     * Initializes listeners after Spring context is ready.
     *
     * Responsibilities:
     * 1. Attach listeners to already existing circuit breakers
     * 2. Register callbacks for any future circuit breakers created dynamically
     *
     * This is important because circuit breakers are created lazily per model
     * inside the ProviderRegistry / FallbackRouter.
     */
    @PostConstruct
    void wireListeners() {
        // Existing breakers (if any) + any created later.
        registry.getAllCircuitBreakers().forEach(this::attach);
        registry.getEventPublisher()
                .onEntryAdded(event -> attach(event.getAddedEntry()))
                .onEntryReplaced(event -> attach(event.getNewEntry()));
    }

}

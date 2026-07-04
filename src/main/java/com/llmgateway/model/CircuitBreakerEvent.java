package com.llmgateway.model;

import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Persisted on every Resilience4j circuit-breaker state transition. */
@Table("circuit_breaker_events")
public class CircuitBreakerEvent {

    @Id
    private Long id;
    private String provider;        // the model the breaker guards

    @Column("from_state")
    private String fromState;

    @Column("to_state")
    private String toState;

    @Column("created_at")
    private OffsetDateTime createdAt;

    public CircuitBreakerEvent() {}

    public CircuitBreakerEvent(String provider, String fromState, String toState) {
        this.provider = provider;
        this.fromState = fromState;
        this.toState = toState;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }
    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

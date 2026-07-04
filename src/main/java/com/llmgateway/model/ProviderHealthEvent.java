package com.llmgateway.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/** Persisted whenever a model's health classification changes. */
@Table("provider_health_events")
public class ProviderHealthEvent {

    @Id
    private Long id;
    private String model;

    @Column("previous_status")
    private String previousStatus;

    @Column("new_status")
    private String newStatus;

    @Column("error_rate")
    private double errorRate;

    @Column("p99_latency_ms")
    private double p99LatencyMs;

    @Column("created_at")
    private OffsetDateTime createdAt;

    public ProviderHealthEvent() {}

    public ProviderHealthEvent(String model, String previousStatus, String newStatus,
                               double errorRate, double p99LatencyMs) {
        this.model = model;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.errorRate = errorRate;
        this.p99LatencyMs = p99LatencyMs;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public double getErrorRate() { return errorRate; }
    public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
    public double getP99LatencyMs() { return p99LatencyMs; }
    public void setP99LatencyMs(double p99LatencyMs) { this.p99LatencyMs = p99LatencyMs; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

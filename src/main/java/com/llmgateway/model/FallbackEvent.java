package com.llmgateway.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/** Persisted whenever the router serves a different model than requested. */
@Table("fallback_events")
public class FallbackEvent {

    @Id
    private Long id;

    @Column("team_id")
    private Long teamId;

    @Column("requested_model")
    private String requestedModel;

    @Column("served_model")
    private String servedModel;

    private String reason;

    @Column("created_at")
    private OffsetDateTime createdAt;

    public FallbackEvent() {}

    public FallbackEvent(Long teamId, String requestedModel, String servedModel, String reason) {
        this.teamId = teamId;
        this.requestedModel = requestedModel;
        this.servedModel = servedModel;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getRequestedModel() { return requestedModel; }
    public void setRequestedModel(String requestedModel) { this.requestedModel = requestedModel; }
    public String getServedModel() { return servedModel; }
    public void setServedModel(String servedModel) { this.servedModel = servedModel; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

package com.llmgateway.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/** Admin mutation audit trail entry. */
@Table("audit_logs")
public class AuditLog {

    @Id
    private Long id;

    @Column("team_id")
    private Long teamId;

    private String actor;
    private String action;
    private String details;

    @Column("created_at")
    private OffsetDateTime createdAt;

    public AuditLog() {}

    public AuditLog(Long teamId, String actor, String action, String details) {
        this.teamId = teamId;
        this.actor = actor;
        this.action = action;
        this.details = details;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

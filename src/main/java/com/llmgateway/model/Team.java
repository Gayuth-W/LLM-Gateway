package com.llmgateway.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A consuming team. Loaded on every request by {@code TeamAuthFilter} and cached.
 * R2DBC entity: status/priority style fields are kept as primitive/String to avoid
 * custom enum codecs on the Postgres driver.
 */
@Table("teams")
public class Team {

    @Id
    private Long id;

    @Column("api_key")
    private String apiKey;

    private String name;

    @Column("allowed_models")
    private String allowedModels;          // comma-separated

    @Column("rpm_limit")
    private int rpmLimit;

    @Column("tpm_limit")
    private int tpmLimit;

    @Column("low_priority_rpm")
    private int lowPriorityRpm;

    @Column("daily_budget_usd")
    private BigDecimal dailyBudgetUsd;

    @Column("monthly_budget_usd")
    private BigDecimal monthlyBudgetUsd;

    @Column("budget_exhausted")
    private boolean budgetExhausted;

    @Column("enrichment_profile")
    private String enrichmentProfile;

    @Column("alert_threshold_pct")
    private int alertThresholdPct;

    @Column("slack_channel")
    private String slackChannel;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    // ---- derived helpers ----

    /** Allowed models as a list (order preserved). */
    public List<String> allowedModelList() {
        if (allowedModels == null || allowedModels.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedModels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public boolean allows(String model) {
        Set<String> set = allowedModelList().stream().collect(Collectors.toSet());
        return set.isEmpty() || set.contains(model);
    }

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAllowedModels() { return allowedModels; }
    public void setAllowedModels(String allowedModels) { this.allowedModels = allowedModels; }
    public int getRpmLimit() { return rpmLimit; }
    public void setRpmLimit(int rpmLimit) { this.rpmLimit = rpmLimit; }
    public int getTpmLimit() { return tpmLimit; }
    public void setTpmLimit(int tpmLimit) { this.tpmLimit = tpmLimit; }
    public int getLowPriorityRpm() { return lowPriorityRpm; }
    public void setLowPriorityRpm(int lowPriorityRpm) { this.lowPriorityRpm = lowPriorityRpm; }
    public BigDecimal getDailyBudgetUsd() { return dailyBudgetUsd; }
    public void setDailyBudgetUsd(BigDecimal dailyBudgetUsd) { this.dailyBudgetUsd = dailyBudgetUsd; }
    public BigDecimal getMonthlyBudgetUsd() { return monthlyBudgetUsd; }
    public void setMonthlyBudgetUsd(BigDecimal monthlyBudgetUsd) { this.monthlyBudgetUsd = monthlyBudgetUsd; }
    public boolean isBudgetExhausted() { return budgetExhausted; }
    public void setBudgetExhausted(boolean budgetExhausted) { this.budgetExhausted = budgetExhausted; }
    public String getEnrichmentProfile() { return enrichmentProfile; }
    public void setEnrichmentProfile(String enrichmentProfile) { this.enrichmentProfile = enrichmentProfile; }
    public int getAlertThresholdPct() { return alertThresholdPct; }
    public void setAlertThresholdPct(int alertThresholdPct) { this.alertThresholdPct = alertThresholdPct; }
    public String getSlackChannel() { return slackChannel; }
    public void setSlackChannel(String slackChannel) { this.slackChannel = slackChannel; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

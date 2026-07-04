package com.llmgateway.dto.admin;

import org.springframework.data.relational.core.mapping.Column;

import java.math.BigDecimal;

/** One row of the spending dashboard, broken down by team and model. */
public record TeamSpendingRow(
        @Column("team_id") Long teamId,
        @Column("team_name") String teamName,
        @Column("model") String model,
        @Column("provider") String provider,
        @Column("input_tokens") long inputTokens,
        @Column("output_tokens") long outputTokens,
        @Column("cost_usd") BigDecimal costUsd
) {}

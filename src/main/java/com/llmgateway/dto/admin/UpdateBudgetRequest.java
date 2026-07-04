package com.llmgateway.dto.admin;

import java.math.BigDecimal;

/** Partial update of a team's budget ceilings. Null fields are left unchanged. */
public record UpdateBudgetRequest(
        BigDecimal dailyBudgetUsd,
        BigDecimal monthlyBudgetUsd
) {}

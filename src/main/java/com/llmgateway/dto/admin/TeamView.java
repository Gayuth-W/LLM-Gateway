package com.llmgateway.dto.admin;

import com.llmgateway.model.Team;

import java.math.BigDecimal;
import java.util.List;

/** Read model for a team, including live spend pulled from Redis. */
public record TeamView(
        Long id,
        String name,
        List<String> allowedModels,
        int rpmLimit,
        int tpmLimit,
        int lowPriorityRpm,
        BigDecimal dailyBudgetUsd,
        BigDecimal monthlyBudgetUsd,
        boolean budgetExhausted,
        String enrichmentProfile,
        int alertThresholdPct,
        BigDecimal spentTodayUsd,
        double dailyBudgetUtilisation
) {
    public static TeamView from(Team t, BigDecimal spentToday) {
        double util = t.getDailyBudgetUsd().signum() == 0
                ? 0.0
                : spentToday.divide(t.getDailyBudgetUsd(), java.math.MathContext.DECIMAL64).doubleValue();
        return new TeamView(
                t.getId(), t.getName(), t.allowedModelList(),
                t.getRpmLimit(), t.getTpmLimit(), t.getLowPriorityRpm(),
                t.getDailyBudgetUsd(), t.getMonthlyBudgetUsd(), t.isBudgetExhausted(),
                t.getEnrichmentProfile(), t.getAlertThresholdPct(),
                spentToday, util);
    }
}

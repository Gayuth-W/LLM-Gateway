package com.llmgateway.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record CreateTeamRequest(
        @NotBlank String apiKey,
        @NotBlank String name,
        List<String> allowedModels,
        @Positive int rpmLimit,
        @Positive int tpmLimit,
        @Positive int lowPriorityRpm,
        BigDecimal dailyBudgetUsd,
        BigDecimal monthlyBudgetUsd,
        String enrichmentProfile
) {}

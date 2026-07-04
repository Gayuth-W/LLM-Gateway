package com.llmgateway.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AlertThresholdRequest(
        @Min(1) @Max(100) int alertThresholdPct,
        String slackChannel
) {}

package com.llmgateway.dto.admin;

import jakarta.validation.constraints.Positive;

/** Partial update of a team's rate limits. Null fields are left unchanged. */
public record UpdateLimitsRequest(
        @Positive Integer rpmLimit,
        @Positive Integer tpmLimit,
        @Positive Integer lowPriorityRpm
) {}

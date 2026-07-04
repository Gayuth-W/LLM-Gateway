package com.llmgateway.dto.admin;

import com.llmgateway.model.enums.ProviderStatus;

/** Live health snapshot for one model, served from GET /admin/providers/health. */
public record ProviderHealthView(
        String model,
        ProviderStatus status,
        double errorRate,
        double p99LatencyMs,
        long sampleCount
) {}

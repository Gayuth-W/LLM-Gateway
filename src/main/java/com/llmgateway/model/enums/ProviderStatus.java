package com.llmgateway.model.enums;

/**
 * Health classification of a single model endpoint, derived from the rolling
 * error-rate window maintained by {@code ProviderHealthService}.
 */
public enum ProviderStatus {
    /** Serving normally. Eligible as a primary or fallback target. */
    HEALTHY,
    /** Elevated error rate / latency. Still usable but de-prioritised. */
    DEGRADED,
    /** Failing. Skipped entirely by the fallback router. */
    DOWN
}

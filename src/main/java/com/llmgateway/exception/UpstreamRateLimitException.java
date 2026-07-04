package com.llmgateway.exception;

/** Provider returned 429. Retryable (after backoff). */
public class UpstreamRateLimitException extends UpstreamException {
    public UpstreamRateLimitException(String message) { super(message, true); }
    @Override public int status() { return 502; }
    @Override public String code() { return "upstream_rate_limited"; }
}

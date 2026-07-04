package com.llmgateway.exception;

/** A team token bucket was empty. Carries the computed Retry-After hint. */
public class RateLimitExceededException extends GatewayException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() { return retryAfterSeconds; }

    @Override public int status() { return 429; }
    @Override public String code() { return "rate_limited"; }
}

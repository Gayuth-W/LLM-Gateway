package com.llmgateway.exception;

/**
 * A failure originating from a provider call. The {@code retryable} flag is what
 * {@code RetryClassifier} keys on: retryable errors are retried with backoff on the
 * same provider; non-retryable errors fail fast and trigger fallback immediately.
 */
public class UpstreamException extends GatewayException {

    private final boolean retryable;

    public UpstreamException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public UpstreamException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() { return retryable; }

    @Override public int status() { return 502; }
    @Override public String code() { return "upstream_error"; }
}

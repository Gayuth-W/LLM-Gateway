package com.llmgateway.exception;

/** Provider call timed out. Retryable. */
public class UpstreamTimeoutException extends UpstreamException {
    public UpstreamTimeoutException(String message) { super(message, true); }
    public UpstreamTimeoutException(String message, Throwable cause) { super(message, true, cause); }
    @Override public int status() { return 504; }
    @Override public String code() { return "upstream_timeout"; }
}

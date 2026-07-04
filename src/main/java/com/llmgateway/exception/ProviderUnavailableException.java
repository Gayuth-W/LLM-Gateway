package com.llmgateway.exception;

/** Every provider in the fallback chain is unhealthy or exhausted. */
public class ProviderUnavailableException extends GatewayException {
    public ProviderUnavailableException(String message) { super(message); }
    public ProviderUnavailableException(String message, Throwable cause) { super(message, cause); }
    @Override public int status() { return 503; }
    @Override public String code() { return "provider_unavailable"; }
}

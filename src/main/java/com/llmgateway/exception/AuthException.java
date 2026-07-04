package com.llmgateway.exception;

/** Invalid or missing team API key. */
public class AuthException extends GatewayException {
    public AuthException(String message) { super(message); }
    @Override public int status() { return 401; }
    @Override public String code() { return "unauthorized"; }
}

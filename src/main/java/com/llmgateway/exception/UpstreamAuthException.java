package com.llmgateway.exception;

/** Provider rejected auth (misconfiguration). NOT retryable — fail fast. */
public class UpstreamAuthException extends UpstreamException {
    public UpstreamAuthException(String message) { super(message, false); }
    @Override public int status() { return 502; }
    @Override public String code() { return "upstream_auth_error"; }
}

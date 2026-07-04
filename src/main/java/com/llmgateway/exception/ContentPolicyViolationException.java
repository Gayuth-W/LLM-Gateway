package com.llmgateway.exception;

/** Request blocked by a content filter. NEVER retried (deterministic failure). */
public class ContentPolicyViolationException extends UpstreamException {
    public ContentPolicyViolationException(String message) { super(message, false); }
    @Override public int status() { return 422; }
    @Override public String code() { return "content_policy_violation"; }
}

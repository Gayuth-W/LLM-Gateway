package com.llmgateway.filter;

/** Keys for per-request data stashed on the ServerWebExchange by the auth filter. */
public final class RequestContext {

    private RequestContext() {}

    public static final String TEAM = "gateway.team";
    public static final String PRIORITY = "gateway.priority";
}

package com.llmgateway.observability;

/** Stable span/tag attribute keys, so tracing and metrics use one vocabulary. */
public final class SpanAttributes {

    private SpanAttributes() {}

    public static final String TEAM_ID = "gateway.team.id";
    public static final String TEAM_NAME = "gateway.team.name";
    public static final String REQUESTED_MODEL = "gateway.model.requested";
    public static final String SERVED_MODEL = "gateway.model.served";
    public static final String PROVIDER = "gateway.provider";
    public static final String PRIORITY = "gateway.priority";
    public static final String FALLBACK = "gateway.fallback";
    public static final String INPUT_TOKENS = "gateway.tokens.input";
    public static final String OUTPUT_TOKENS = "gateway.tokens.output";
    public static final String STREAMING = "gateway.streaming";
}

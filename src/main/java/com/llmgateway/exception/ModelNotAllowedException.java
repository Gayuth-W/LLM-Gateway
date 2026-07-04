package com.llmgateway.exception;

/** The team is not permitted to use the requested model. */
public class ModelNotAllowedException extends GatewayException {
    public ModelNotAllowedException(String message) { super(message); }
    @Override public int status() { return 403; }
    @Override public String code() { return "model_not_allowed"; }
}

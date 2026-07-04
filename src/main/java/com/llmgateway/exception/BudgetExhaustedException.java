package com.llmgateway.exception;

/** The team has hit its budget ceiling. */
public class BudgetExhaustedException extends GatewayException {
    public BudgetExhaustedException(String message) { super(message); }
    @Override public int status() { return 402; }
    @Override public String code() { return "budget_exhausted"; }
}

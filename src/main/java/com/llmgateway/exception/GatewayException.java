package com.llmgateway.exception;

/** Base for all gateway errors that map cleanly to an HTTP status + error code. */
public abstract class GatewayException extends RuntimeException {

    protected GatewayException(String message) {
        super(message);
    }

    protected GatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    /** HTTP status to return to the caller. */
    public abstract int status();

    /** Stable machine-readable error code. */
    public abstract String code();
}

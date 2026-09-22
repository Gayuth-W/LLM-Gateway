package com.llmgateway.exception;

/**
 * Login failed. Deliberately does not distinguish "no such user" from "wrong
 * password" -- the message and the status are identical either way, so the
 * endpoint cannot be used to enumerate valid usernames.
 */
public class InvalidCredentialsException extends GatewayException {

    public InvalidCredentialsException() {
        super("Incorrect username or password.");
    }

    @Override public int status() { return 401; }

    @Override public String code() { return "invalid_credentials"; }
}

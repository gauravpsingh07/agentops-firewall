package com.agentops.firewall.common.error;

/** Thrown when an operation conflicts with current state. Translated to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

package com.agentops.firewall.common.error;

/** Thrown when a requested resource does not exist. Translated to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

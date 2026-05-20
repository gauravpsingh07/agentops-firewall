package com.agentops.firewall.common.error;

/**
 * Thrown when an X-Agent-Key authentication attempt fails for any
 * reason (missing key, missing agent, wrong key, disabled agent).
 * Mapped to HTTP 401 by {@link GlobalExceptionHandler} with a uniform
 * message so the caller cannot enumerate which precondition failed.
 */
public class AgentAuthenticationException extends RuntimeException {

    public AgentAuthenticationException(String message) {
        super(message);
    }
}
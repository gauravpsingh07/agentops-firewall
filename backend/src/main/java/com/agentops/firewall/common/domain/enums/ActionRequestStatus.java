package com.agentops.firewall.common.domain.enums;

/**
 * Lifecycle status of a submitted agent action. Transitions are written to
 * the audit log; the terminal state for a given request is one of
 * {@code ALLOWED}, {@code DENIED}, {@code APPROVED}, or {@code REJECTED}.
 */
public enum ActionRequestStatus {
    RECEIVED,
    ALLOWED,
    DENIED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}

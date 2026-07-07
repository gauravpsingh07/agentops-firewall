package com.agentops.firewall.common.domain.enums;

/**
 * Lifecycle status of a submitted agent action.
 *
 * <p>A decision moves an action to {@code ALLOWED}, {@code DENIED}, or
 * {@code PENDING_APPROVAL}; a reviewer then moves a pending action to
 * {@code APPROVED} or {@code REJECTED}. Once an action is cleared to run
 * ({@code ALLOWED} or {@code APPROVED}) the agent reports the execution
 * outcome, moving it to {@code COMPLETED} or {@code FAILED}. Transitions are
 * written to the audit log.
 */
public enum ActionRequestStatus {
    RECEIVED,
    ALLOWED,
    DENIED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    COMPLETED,
    FAILED
}

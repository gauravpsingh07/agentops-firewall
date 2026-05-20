package com.agentops.firewall.common.domain.enums;

/**
 * The three possible outcomes of evaluating a policy against an action
 * request. Mirrors the firewall's externally-visible decision contract.
 */
public enum PolicyOutcome {
    ALLOW,
    DENY,
    NEEDS_APPROVAL
}

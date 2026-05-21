package com.agentops.firewall.dashboard.dto;

import com.agentops.firewall.common.domain.enums.ActionRequestStatus;

/**
 * A single entry in the decision-distribution response: one
 * action-request status and its count. Uses ActionRequestStatus (the
 * final status) rather than PolicyOutcome so that APPROVED and REJECTED
 * are visible in the distribution.
 */
public record DecisionDistributionEntry(
        ActionRequestStatus status,
        long count
) {
}

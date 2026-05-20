package com.agentops.firewall.policy.dto;

import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.policy.PolicyEvaluationResult;

import java.util.UUID;

/**
 * Result of POST /api/policies/simulate. Mirrors the live ingestion
 * decision response shape so dashboards can render the two side by side.
 */
public record PolicySimulationResponse(
        PolicyOutcome decision,
        UUID matchedPolicyId,
        String matchedPolicyName,
        String reason,
        boolean simulated
) {
    public static PolicySimulationResponse from(PolicyEvaluationResult result) {
        return new PolicySimulationResponse(
                result.outcome(),
                result.matchedPolicyId(),
                result.matchedPolicyName(),
                result.reason(),
                true);
    }
}
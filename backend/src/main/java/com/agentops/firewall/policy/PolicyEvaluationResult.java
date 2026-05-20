package com.agentops.firewall.policy;

import com.agentops.firewall.common.domain.enums.PolicyOutcome;

import java.util.UUID;

/**
 * Outcome of running {@link PolicyEvaluator#evaluate}. When no policy
 * matches, {@code matchedPolicyId} is null and {@code matchedPolicyName}
 * describes the default ("no-match default").
 */
public record PolicyEvaluationResult(
        PolicyOutcome outcome,
        UUID matchedPolicyId,
        String matchedPolicyName,
        String reason
) {
    public static PolicyEvaluationResult matched(PolicyOutcome outcome, UUID policyId,
                                                  String policyName, String reason) {
        return new PolicyEvaluationResult(outcome, policyId, policyName, reason);
    }

    public static PolicyEvaluationResult defaultOutcome(PolicyOutcome outcome, String reason) {
        return new PolicyEvaluationResult(outcome, null, "no-match default", reason);
    }
}

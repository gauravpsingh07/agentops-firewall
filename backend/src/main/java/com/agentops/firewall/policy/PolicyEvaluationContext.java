package com.agentops.firewall.policy;

import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;

import java.util.Map;

/**
 * Read-only snapshot of the input the policy evaluator considers when
 * deciding the outcome of an action. The same record is used for live
 * action ingestion and for the simulator endpoint (the simulator passes
 * a synthetic context but never persists anything).
 */
public record PolicyEvaluationContext(
        ActionType actionType,
        String resource,
        RiskLevel riskLevel,
        Map<String, Object> metadata,
        Agent agent
) {
    public PolicyEvaluationContext {
        if (metadata == null) metadata = Map.of();
    }
}

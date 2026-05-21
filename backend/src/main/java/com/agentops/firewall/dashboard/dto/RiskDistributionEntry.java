package com.agentops.firewall.dashboard.dto;

import com.agentops.firewall.common.domain.enums.RiskLevel;

/**
 * A single entry in the risk-distribution response: one risk level
 * and its count.
 */
public record RiskDistributionEntry(
        RiskLevel riskLevel,
        long count
) {
}

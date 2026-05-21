package com.agentops.firewall.dashboard.dto;

/**
 * Aggregated system-wide summary for the dashboard landing page.
 */
public record DashboardSummaryResponse(
        long totalActions,
        long allowedActions,
        long deniedActions,
        long pendingApprovals,
        long approvedActions,
        long rejectedActions,
        long totalAgents,
        long activeAgents,
        long totalPolicies,
        long enabledPolicies
) {
}

package com.agentops.firewall.dashboard;

import com.agentops.firewall.action.dto.ActionRequestResponse;
import com.agentops.firewall.audit.dto.AuditLogResponse;
import com.agentops.firewall.dashboard.dto.DashboardSummaryResponse;
import com.agentops.firewall.dashboard.dto.DecisionDistributionEntry;
import com.agentops.firewall.dashboard.dto.RiskDistributionEntry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only dashboard aggregation endpoints. Any authenticated user
 * (ADMIN, REVIEWER, or VIEWER) can access these endpoints. No
 * method-level role restriction — the global security config requires
 * authentication for all {@code /api/**} endpoints.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {
        return dashboardService.summary();
    }

    @GetMapping("/recent-actions")
    public List<ActionRequestResponse> recentActions() {
        return dashboardService.recentActions();
    }

    @GetMapping("/risk-distribution")
    public List<RiskDistributionEntry> riskDistribution() {
        return dashboardService.riskDistribution();
    }

    @GetMapping("/decision-distribution")
    public List<DecisionDistributionEntry> decisionDistribution() {
        return dashboardService.decisionDistribution();
    }

    @GetMapping("/recent-audit-events")
    public List<AuditLogResponse> recentAuditEvents() {
        return dashboardService.recentAuditEvents();
    }
}

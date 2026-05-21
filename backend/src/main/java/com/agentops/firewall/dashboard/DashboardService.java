package com.agentops.firewall.dashboard;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.action.ActionRequestRepository;
import com.agentops.firewall.action.dto.ActionRequestResponse;
import com.agentops.firewall.agent.AgentRepository;
import com.agentops.firewall.audit.AuditLog;
import com.agentops.firewall.audit.AuditLogRepository;
import com.agentops.firewall.audit.dto.AuditLogResponse;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.dashboard.dto.DashboardSummaryResponse;
import com.agentops.firewall.dashboard.dto.DecisionDistributionEntry;
import com.agentops.firewall.dashboard.dto.RiskDistributionEntry;
import com.agentops.firewall.policy.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregation service for the dashboard APIs. Queries are read-only
 * and may return slightly stale data — this is acceptable for a
 * dashboard overview.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ActionRequestRepository actionRequestRepository;
    private final AgentRepository agentRepository;
    private final PolicyRepository policyRepository;
    private final AuditLogRepository auditLogRepository;

    public DashboardService(ActionRequestRepository actionRequestRepository,
                            AgentRepository agentRepository,
                            PolicyRepository policyRepository,
                            AuditLogRepository auditLogRepository) {
        this.actionRequestRepository = actionRequestRepository;
        this.agentRepository = agentRepository;
        this.policyRepository = policyRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public DashboardSummaryResponse summary() {
        long totalActions = actionRequestRepository.count();
        long allowed = actionRequestRepository.countByStatus(ActionRequestStatus.ALLOWED);
        long denied = actionRequestRepository.countByStatus(ActionRequestStatus.DENIED);
        long pending = actionRequestRepository.countByStatus(ActionRequestStatus.PENDING_APPROVAL);
        long approved = actionRequestRepository.countByStatus(ActionRequestStatus.APPROVED);
        long rejected = actionRequestRepository.countByStatus(ActionRequestStatus.REJECTED);

        long totalAgents = agentRepository.count();
        long activeAgents = agentRepository.countByStatus(AgentStatus.ACTIVE);

        long totalPolicies = policyRepository.count();
        long enabledPolicies = policyRepository.countByEnabledTrue();

        return new DashboardSummaryResponse(
                totalActions, allowed, denied, pending, approved, rejected,
                totalAgents, activeAgents, totalPolicies, enabledPolicies
        );
    }

    public List<ActionRequestResponse> recentActions() {
        return actionRequestRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(ActionRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<RiskDistributionEntry> riskDistribution() {
        return actionRequestRepository.countGroupedByRiskLevel()
                .stream()
                .map(row -> new RiskDistributionEntry(
                        (RiskLevel) row[0],
                        (long) row[1]
                ))
                .collect(Collectors.toList());
    }

    public List<DecisionDistributionEntry> decisionDistribution() {
        return actionRequestRepository.countGroupedByStatus()
                .stream()
                .map(row -> new DecisionDistributionEntry(
                        (ActionRequestStatus) row[0],
                        (long) row[1]
                ))
                .collect(Collectors.toList());
    }

    public List<AuditLogResponse> recentAuditEvents() {
        return auditLogRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toAuditResponse)
                .collect(Collectors.toList());
    }

    private AuditLogResponse toAuditResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getEventType(), log.getActorType(),
                log.getActorId(), log.getSubjectType(), log.getSubjectId(),
                log.getSummary(), log.getDetailsJson(), log.getCreatedAt()
        );
    }
}

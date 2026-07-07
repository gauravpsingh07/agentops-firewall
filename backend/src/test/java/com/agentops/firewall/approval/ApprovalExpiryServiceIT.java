package com.agentops.firewall.approval;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.action.ActionRequestRepository;
import com.agentops.firewall.audit.AuditLogRepository;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.messaging.AgentActionEventPublisher;
import com.agentops.firewall.messaging.ApprovalTaskPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for {@link ApprovalExpiryService}. Drives the worker
 * method directly (no clock waiting) against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApprovalExpiryServiceIT {

    @Autowired ApprovalExpiryService expiryService;
    @Autowired ApprovalRequestRepository approvalRequestRepository;
    @Autowired ActionRequestRepository actionRequestRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockBean AgentActionEventPublisher eventPublisher;
    @MockBean ApprovalTaskPublisher approvalTaskPublisher;

    @BeforeEach
    @AfterEach
    void clean() {
        approvalRequestRepository.deleteAll();
        actionRequestRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("a PENDING approval past its deadline is expired and its action is denied")
    void expiresStalePendingApproval() {
        UUID actionId = persistAction();
        persistApproval(actionId, ApprovalStatus.PENDING, Instant.now().minus(1, ChronoUnit.HOURS));

        int expired = expiryService.expireStalePendingApprovals(Instant.now());

        assertThat(expired).isEqualTo(1);
        ApprovalRequest approval = approvalRequestRepository.findByActionRequestId(actionId).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.EXPIRED);
        assertThat(approval.getDecidedAt()).isNotNull();

        ActionRequest action = actionRequestRepository.findById(actionId).orElseThrow();
        assertThat(action.getStatus()).isEqualTo(ActionRequestStatus.DENIED);
        assertThat(action.getDecisionReason()).contains("expired");

        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "APPROVAL_EXPIRED".equals(l.getEventType()));

        // The completion is fanned out to the brokers after commit.
        verify(eventPublisher).publishCompleted(any(), any(), any());
        verify(approvalTaskPublisher).publishApprovalCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("a PENDING approval with a future deadline is left untouched")
    void leavesFuturePendingApproval() {
        UUID actionId = persistAction();
        persistApproval(actionId, ApprovalStatus.PENDING, Instant.now().plus(1, ChronoUnit.HOURS));

        int expired = expiryService.expireStalePendingApprovals(Instant.now());

        assertThat(expired).isZero();
        ApprovalRequest approval = approvalRequestRepository.findByActionRequestId(actionId).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("an already-decided approval is never re-expired")
    void ignoresAlreadyDecidedApproval() {
        UUID actionId = persistAction();
        persistApproval(actionId, ApprovalStatus.APPROVED, Instant.now().minus(1, ChronoUnit.HOURS));

        int expired = expiryService.expireStalePendingApprovals(Instant.now());

        assertThat(expired).isZero();
        ApprovalRequest approval = approvalRequestRepository.findByActionRequestId(actionId).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    private UUID persistAction() {
        ActionRequest action = new ActionRequest();
        action.setAgentId(UUID.randomUUID());
        action.setActionType(ActionType.DELETE_FILE);
        action.setResource("/tmp/x");
        action.setRiskLevel(RiskLevel.MEDIUM);
        action.setStatus(ActionRequestStatus.PENDING_APPROVAL);
        return actionRequestRepository.save(action).getId();
    }

    private void persistApproval(UUID actionId, ApprovalStatus status, Instant expiresAt) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setActionRequestId(actionId);
        approval.setStatus(status);
        approval.setExpiresAt(expiresAt);
        approvalRequestRepository.save(approval);
    }
}

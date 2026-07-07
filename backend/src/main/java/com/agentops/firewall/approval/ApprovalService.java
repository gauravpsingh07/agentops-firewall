package com.agentops.firewall.approval;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.action.ActionRequestRepository;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.agent.AgentRepository;
import com.agentops.firewall.approval.dto.ApprovalResponse;
import com.agentops.firewall.audit.AuditService;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.common.error.ConflictException;
import com.agentops.firewall.common.error.NotFoundException;
import com.agentops.firewall.messaging.ActionCompletedNotification;
import com.agentops.firewall.user.User;
import com.agentops.firewall.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Core approval logic: listing, detail, approve, and reject.
 *
 * <p>Approve and reject are terminal transitions from PENDING.
 * Attempting to act on a non-PENDING request produces a 409 Conflict.
 * Expired requests (past {@code expiresAt}) cannot be approved but can
 * still be rejected.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ActionRequestRepository actionRequestRepository;
    private final AgentRepository agentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public ApprovalService(ApprovalRequestRepository approvalRequestRepository,
                           ActionRequestRepository actionRequestRepository,
                           AgentRepository agentRepository,
                           UserRepository userRepository,
                           AuditService auditService,
                           ApplicationEventPublisher events) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.actionRequestRepository = actionRequestRepository;
        this.agentRepository = agentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalResponse> findAll(ApprovalStatus status,
                                          UUID agentId,
                                          ActionType actionType,
                                          RiskLevel riskLevel,
                                          Instant from,
                                          Instant to,
                                          Pageable pageable) {
        Specification<ApprovalRequest> spec =
                ApprovalSpecifications.withFilters(status, agentId, actionType, riskLevel, from, to);
        return approvalRequestRepository.findAll(spec, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ApprovalResponse findById(UUID id) {
        ApprovalRequest approval = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Approval request not found: " + id));
        return toResponse(approval);
    }

    @Transactional
    public ApprovalResponse approve(UUID id, UUID reviewerUserId, String note) {
        ApprovalRequest approval = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Approval request not found: " + id));

        validatePending(approval);
        validateNotExpired(approval);

        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setReviewerUserId(reviewerUserId);
        approval.setReviewerNote(note);
        approval.setDecidedAt(Instant.now());
        approvalRequestRepository.save(approval);

        ActionRequest action = actionRequestRepository.findById(approval.getActionRequestId())
                .orElseThrow(() -> new NotFoundException(
                        "Action request not found: " + approval.getActionRequestId()));
        action.setStatus(ActionRequestStatus.APPROVED);
        actionRequestRepository.save(action);

        auditService.record(
                AuditService.EVENT_APPROVAL_APPROVED,
                AuditService.ACTOR_USER, reviewerUserId,
                AuditService.SUBJECT_APPROVAL_REQUEST, approval.getId(),
                "Approval approved for action: " + action.getActionType().name(),
                AuditService.details(
                        "actionRequestId", action.getId(),
                        "agentId", action.getAgentId(),
                        "actionType", action.getActionType().name(),
                        "reviewerNote", note
                )
        );

        events.publishEvent(new ActionCompletedNotification(action, approval, reviewerUserId, "APPROVED"));

        return toResponse(approval);
    }

    @Transactional
    public ApprovalResponse reject(UUID id, UUID reviewerUserId, String note) {
        ApprovalRequest approval = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Approval request not found: " + id));

        validatePending(approval);

        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setReviewerUserId(reviewerUserId);
        approval.setReviewerNote(note);
        approval.setDecidedAt(Instant.now());
        approvalRequestRepository.save(approval);

        ActionRequest action = actionRequestRepository.findById(approval.getActionRequestId())
                .orElseThrow(() -> new NotFoundException(
                        "Action request not found: " + approval.getActionRequestId()));
        action.setStatus(ActionRequestStatus.REJECTED);
        actionRequestRepository.save(action);

        auditService.record(
                AuditService.EVENT_APPROVAL_REJECTED,
                AuditService.ACTOR_USER, reviewerUserId,
                AuditService.SUBJECT_APPROVAL_REQUEST, approval.getId(),
                "Approval rejected for action: " + action.getActionType().name(),
                AuditService.details(
                        "actionRequestId", action.getId(),
                        "agentId", action.getAgentId(),
                        "actionType", action.getActionType().name(),
                        "reviewerNote", note
                )
        );

        events.publishEvent(new ActionCompletedNotification(action, approval, reviewerUserId, "REJECTED"));

        return toResponse(approval);
    }

    private void validatePending(ApprovalRequest approval) {
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("Approval request is already " +
                    approval.getStatus().name().toLowerCase() +
                    " and cannot be modified.");
        }
    }

    private void validateNotExpired(ApprovalRequest approval) {
        if (approval.getExpiresAt() != null && Instant.now().isAfter(approval.getExpiresAt())) {
            throw new ConflictException("Approval request has expired and cannot be approved.");
        }
    }

    private ApprovalResponse toResponse(ApprovalRequest approval) {
        ActionRequest action = actionRequestRepository.findById(approval.getActionRequestId())
                .orElse(null);

        String agentName = null;
        UUID agentId = null;
        ActionType actionType = null;
        String resource = null;
        RiskLevel riskLevel = null;
        ActionRequestStatus actionStatus = null;
        UUID matchedPolicyId = null;
        String decisionReason = null;

        if (action != null) {
            agentId = action.getAgentId();
            actionType = action.getActionType();
            resource = action.getResource();
            riskLevel = action.getRiskLevel();
            actionStatus = action.getStatus();
            matchedPolicyId = action.getMatchedPolicyId();
            decisionReason = action.getDecisionReason();

            Agent agent = agentRepository.findById(action.getAgentId()).orElse(null);
            if (agent != null) {
                agentName = agent.getName();
            }
        }

        String reviewerUsername = null;
        if (approval.getReviewerUserId() != null) {
            User reviewer = userRepository.findById(approval.getReviewerUserId()).orElse(null);
            if (reviewer != null) {
                reviewerUsername = reviewer.getUsername();
            }
        }

        return new ApprovalResponse(
                approval.getId(),
                approval.getStatus(),
                approval.getActionRequestId(),
                actionType,
                resource,
                riskLevel,
                actionStatus,
                agentId,
                agentName,
                matchedPolicyId,
                decisionReason,
                approval.getReviewerUserId(),
                reviewerUsername,
                approval.getReviewerNote(),
                approval.getCreatedAt(),
                approval.getDecidedAt(),
                approval.getExpiresAt()
        );
    }
}

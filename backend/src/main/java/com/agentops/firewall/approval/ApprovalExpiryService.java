package com.agentops.firewall.approval;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.action.ActionRequestRepository;
import com.agentops.firewall.audit.AuditService;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import com.agentops.firewall.messaging.ActionCompletedNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Expires approval requests that have sat PENDING past their
 * {@code expiresAt} deadline. Without this sweep an unattended approval
 * stays PENDING forever — the {@code EXPIRED} status, the
 * {@code APPROVAL_EXPIRED} audit event, and the dashboard's EXPIRED filter
 * would all be dead wiring.
 *
 * <p>Expiry is <em>fail-closed</em>: the approval moves to
 * {@link ApprovalStatus#EXPIRED} and the underlying action is terminally
 * {@link ActionRequestStatus#DENIED}. An action the firewall could not get
 * a human to confirm is denied rather than left in limbo.
 *
 * <p>The scheduled trigger is registered under the {@code local}/production
 * profiles via {@code @EnableScheduling}; the worker method is public so
 * tests can invoke it deterministically without waiting on the clock.
 */
@Service
public class ApprovalExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalExpiryService.class);

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ActionRequestRepository actionRequestRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public ApprovalExpiryService(ApprovalRequestRepository approvalRequestRepository,
                                 ActionRequestRepository actionRequestRepository,
                                 AuditService auditService,
                                 ApplicationEventPublisher events) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.actionRequestRepository = actionRequestRepository;
        this.auditService = auditService;
        this.events = events;
    }

    /**
     * Scheduled entry point. Interval is configurable so a demo can tighten
     * it; the default of 60s is unobtrusive.
     */
    @Scheduled(
            fixedDelayString = "${agentops.approvals.expiry.check-interval-ms:60000}",
            initialDelayString = "${agentops.approvals.expiry.initial-delay-ms:60000}")
    public void sweep() {
        int expired = expireStalePendingApprovals(Instant.now());
        if (expired > 0) {
            log.info("Approval expiry sweep expired {} stale pending request(s).", expired);
        }
    }

    /**
     * Expire every PENDING approval whose deadline is before {@code now}.
     *
     * @return the number of approvals expired.
     */
    @Transactional
    public int expireStalePendingApprovals(Instant now) {
        List<ApprovalRequest> due =
                approvalRequestRepository.findByStatusAndExpiresAtBefore(ApprovalStatus.PENDING, now);
        for (ApprovalRequest approval : due) {
            expire(approval, now);
        }
        return due.size();
    }

    private void expire(ApprovalRequest approval, Instant now) {
        approval.setStatus(ApprovalStatus.EXPIRED);
        approval.setDecidedAt(now);
        approvalRequestRepository.save(approval);

        ActionRequest action = actionRequestRepository.findById(approval.getActionRequestId())
                .orElse(null);
        if (action != null) {
            action.setStatus(ActionRequestStatus.DENIED);
            action.setDecisionReason("Approval window expired; auto-denied by the expiry sweeper.");
            actionRequestRepository.save(action);
        }

        auditService.record(
                AuditService.EVENT_APPROVAL_EXPIRED,
                AuditService.ACTOR_SYSTEM, null,
                AuditService.SUBJECT_APPROVAL_REQUEST, approval.getId(),
                "Approval expired without a decision.",
                AuditService.details(
                        "actionRequestId", approval.getActionRequestId(),
                        "expiresAt", approval.getExpiresAt() == null ? null : approval.getExpiresAt().toString(),
                        "expiredAt", now.toString()
                )
        );

        if (action != null) {
            // Fan out the completion the same way approve/reject do, with a
            // null reviewer (the sweeper is the SYSTEM actor).
            events.publishEvent(new ActionCompletedNotification(action, approval, null, "EXPIRED"));
        }
    }
}

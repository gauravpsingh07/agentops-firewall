package com.agentops.firewall.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges the persistence layer's internal domain events to the outbound
 * Kafka / RabbitMQ publishers, deferring every send until <em>after</em>
 * the surrounding transaction commits.
 *
 * <p>Publishing inside the transaction (the previous design) risked a
 * dual-write: if the transaction rolled back after a send, the broker had
 * already received an event describing state that never became durable. By
 * listening with {@link TransactionPhase#AFTER_COMMIT}, a rollback simply
 * discards the pending notifications and nothing is emitted.
 *
 * <p>The publishers themselves remain fire-and-forget (broker failures are
 * swallowed and logged), so a broker outage after a successful commit never
 * propagates back to the caller.
 */
@Component
public class TransactionalMessagingForwarder {

    private final AgentActionEventPublisher eventPublisher;
    private final ApprovalTaskPublisher approvalTaskPublisher;

    public TransactionalMessagingForwarder(AgentActionEventPublisher eventPublisher,
                                           ApprovalTaskPublisher approvalTaskPublisher) {
        this.eventPublisher = eventPublisher;
        this.approvalTaskPublisher = approvalTaskPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActionReceived(ActionReceivedNotification event) {
        eventPublisher.publishReceived(event.action(), event.agent());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActionDecided(ActionDecidedNotification event) {
        eventPublisher.publishDecided(event.action(), event.result());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalRequested(ApprovalRequestedNotification event) {
        approvalTaskPublisher.publishApprovalRequested(event.approval(), event.action(), event.agent());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActionCompleted(ActionCompletedNotification event) {
        eventPublisher.publishCompleted(event.action(), event.approval(), event.reviewerUserId());
        // A self-reported completion has no approval workflow to notify.
        if (event.approval() != null) {
            approvalTaskPublisher.publishApprovalCompleted(event.approval(), event.action(), event.finalStatus());
        }
    }
}

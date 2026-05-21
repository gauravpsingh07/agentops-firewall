package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.approval.ApprovalRequest;

/**
 * Outbound channel for approval-workflow task messages. The action
 * ingestion pipeline depends on this interface; the production
 * implementation publishes to RabbitMQ queues and tests provide a
 * Mockito mock so no broker is required.
 */
public interface ApprovalTaskPublisher {

    /**
     * Publish a structured approval task to the approval queue when an
     * action requires human review.
     */
    void publishApprovalRequested(ApprovalRequest approval, ActionRequest action, Agent agent);

    /**
     * Publish a notification after a reviewer has approved or rejected
     * the request. Published to the notifications queue.
     */
    void publishApprovalCompleted(ApprovalRequest approval, ActionRequest action, String finalStatus);
}

package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.approval.ApprovalRequest;

/**
 * Internal Spring application event signalling that an action needs human
 * approval and an {@link ApprovalRequest} was created. Forwarded to the
 * RabbitMQ approval-requests queue after the transaction commits.
 */
public record ApprovalRequestedNotification(ApprovalRequest approval,
                                            ActionRequest action,
                                            Agent agent) {
}

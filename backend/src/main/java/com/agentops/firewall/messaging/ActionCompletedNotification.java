package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.approval.ApprovalRequest;

import java.util.UUID;

/**
 * Internal Spring application event signalling that a reviewer approved or
 * rejected an approval request, closing out the action. Forwarded to the
 * Kafka completed topic and the RabbitMQ notifications queue after the
 * transaction commits.
 *
 * @param finalStatus terminal approval status name ({@code APPROVED} /
 *                    {@code REJECTED}) used for the RabbitMQ notification.
 */
public record ActionCompletedNotification(ActionRequest action,
                                          ApprovalRequest approval,
                                          UUID reviewerUserId,
                                          String finalStatus) {
}

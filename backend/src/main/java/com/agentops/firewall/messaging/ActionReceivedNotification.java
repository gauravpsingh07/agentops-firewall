package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;

/**
 * Internal Spring application event signalling that an action was received
 * and persisted. Published inside the ingestion transaction and forwarded
 * to the Kafka broker only after the transaction commits (see
 * {@link TransactionalMessagingForwarder}), so a rollback never emits a
 * phantom event.
 */
public record ActionReceivedNotification(ActionRequest action, Agent agent) {
}

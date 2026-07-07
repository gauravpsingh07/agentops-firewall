package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.policy.PolicyEvaluationResult;

/**
 * Internal Spring application event signalling that a policy decision was
 * reached for an action. Forwarded to Kafka after the transaction commits.
 */
public record ActionDecidedNotification(ActionRequest action, PolicyEvaluationResult result) {
}

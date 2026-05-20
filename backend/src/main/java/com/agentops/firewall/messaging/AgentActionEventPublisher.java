package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.policy.PolicyEvaluationResult;

/**
 * Outbound channel for agent-action lifecycle events. The action
 * ingestion pipeline depends on the interface; the production
 * implementation publishes to Kafka topics and tests provide a Mockito
 * mock so no broker is required.
 */
public interface AgentActionEventPublisher {

    void publishReceived(ActionRequest action, Agent agent);

    void publishDecided(ActionRequest action, PolicyEvaluationResult result);
}
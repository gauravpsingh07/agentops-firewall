package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.domain.enums.PolicyOutcome;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.policy.PolicyEvaluationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaAgentActionEventPublisher}. The Kafka
 * template is mocked; no broker is required.
 */
class KafkaAgentActionEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, String> kafkaTemplate = mock(KafkaOperations.class);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private KafkaProperties topics;
    private KafkaAgentActionEventPublisher publisher;

    @BeforeEach
    void setUp() {
        topics = new KafkaProperties();
        topics.setActionsReceived("agent.actions.received");
        topics.setActionsDecided("agent.actions.decided");
        topics.setActionsCompleted("agent.actions.completed");
        publisher = new KafkaAgentActionEventPublisher(kafkaTemplate, topics, objectMapper);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("publishReceived sends a JSON event to the configured topic keyed by action id")
    void publishReceived() throws Exception {
        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setName("agent-x");
        agent.setStatus(AgentStatus.ACTIVE);

        ActionRequest action = new ActionRequest();
        action.setId(UUID.randomUUID());
        action.setAgentId(agent.getId());
        action.setActionType(ActionType.SEND_EMAIL);
        action.setResource("external_email");
        action.setRiskLevel(RiskLevel.MEDIUM);
        action.setStatus(ActionRequestStatus.RECEIVED);

        publisher.publishReceived(action, agent);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("agent.actions.received"), eq(action.getId().toString()), valueCaptor.capture());

        var json = objectMapper.readTree(valueCaptor.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("ACTION_RECEIVED");
        assertThat(json.get("actionRequestId").asText()).isEqualTo(action.getId().toString());
        assertThat(json.get("agentId").asText()).isEqualTo(agent.getId().toString());
        assertThat(json.get("agentName").asText()).isEqualTo("agent-x");
        assertThat(json.get("actionType").asText()).isEqualTo("SEND_EMAIL");
        assertThat(json.get("riskLevel").asText()).isEqualTo("MEDIUM");
        // Safety: no secret-looking fields leaked into the event.
        assertThat(valueCaptor.getValue()).doesNotContain("apiKey");
        assertThat(valueCaptor.getValue()).doesNotContain("password");
        assertThat(valueCaptor.getValue()).doesNotContain("Bearer ");
    }

    @Test
    @DisplayName("publishDecided sends a JSON event containing the decision outcome and matched policy")
    void publishDecided() throws Exception {
        UUID actionId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ActionRequest action = new ActionRequest();
        action.setId(actionId);
        action.setAgentId(agentId);
        action.setActionType(ActionType.DELETE_FILE);
        action.setRiskLevel(RiskLevel.HIGH);
        action.setStatus(ActionRequestStatus.PENDING_APPROVAL);

        PolicyEvaluationResult result = PolicyEvaluationResult.matched(
                PolicyOutcome.NEEDS_APPROVAL, policyId, "Require approval for DELETE_FILE",
                "Matched");

        publisher.publishDecided(action, result);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("agent.actions.decided"), eq(actionId.toString()), valueCaptor.capture());

        var json = objectMapper.readTree(valueCaptor.getValue());
        assertThat(json.get("eventType").asText()).isEqualTo("ACTION_DECIDED");
        assertThat(json.get("decision").asText()).isEqualTo("NEEDS_APPROVAL");
        assertThat(json.get("status").asText()).isEqualTo("PENDING_APPROVAL");
        assertThat(json.get("matchedPolicyId").asText()).isEqualTo(policyId.toString());
        assertThat(json.get("matchedPolicyName").asText()).contains("DELETE_FILE");
    }

    @Test
    @DisplayName("publish swallows broker exceptions so callers are never blocked")
    void publishSwallowsBrokerErrors() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("broker down"));

        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setName("a");
        ActionRequest action = new ActionRequest();
        action.setId(UUID.randomUUID());
        action.setAgentId(agent.getId());
        action.setActionType(ActionType.SEND_EMAIL);
        action.setRiskLevel(RiskLevel.LOW);
        action.setStatus(ActionRequestStatus.RECEIVED);

        // Must not throw.
        publisher.publishReceived(action, agent);
        verify(kafkaTemplate).send(anyString(), any(), any());
    }
}
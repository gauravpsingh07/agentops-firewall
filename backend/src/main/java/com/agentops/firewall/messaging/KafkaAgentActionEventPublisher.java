package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.approval.ApprovalRequest;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.messaging.events.ActionCompletedEvent;
import com.agentops.firewall.messaging.events.ActionDecidedEvent;
import com.agentops.firewall.messaging.events.ActionReceivedEvent;
import com.agentops.firewall.policy.PolicyEvaluationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Default {@link AgentActionEventPublisher} implementation. Serializes
 * the event records to JSON via Jackson and publishes them to the
 * configured Kafka topics using the action request id as the message
 * key so events for the same action land on the same partition.
 *
 * <p>Failures are logged but not rethrown — the synchronous decision
 * the firewall returns to the agent is not blocked on broker
 * availability. Reliability concerns are deferred to the next phase
 * (outbox pattern, retry, dead-letter topic).
 */
@Component
public class KafkaAgentActionEventPublisher implements AgentActionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAgentActionEventPublisher.class);

    private final KafkaOperations<String, String> kafkaTemplate;
    private final KafkaProperties topics;
    private final ObjectMapper objectMapper;

    public KafkaAgentActionEventPublisher(KafkaOperations<String, String> kafkaTemplate,
                                           KafkaProperties topics,
                                           ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishReceived(ActionRequest action, Agent agent) {
        ActionReceivedEvent event = new ActionReceivedEvent(
                UUID.randomUUID().toString(),
                ActionReceivedEvent.TYPE,
                action.getId(),
                agent.getId(),
                agent.getName(),
                action.getActionType(),
                action.getResource(),
                action.getRiskLevel(),
                // The received event is, by definition, emitted for an action
                // in RECEIVED state. Use the constant rather than reading the
                // entity's status, which is mutated later in the ingestion
                // transaction and would otherwise leak into the deferred send.
                ActionRequestStatus.RECEIVED,
                Instant.now()
        );
        publish(topics.getActionsReceived(), action.getId().toString(), event);
    }

    @Override
    public void publishDecided(ActionRequest action, PolicyEvaluationResult result) {
        ActionDecidedEvent event = new ActionDecidedEvent(
                UUID.randomUUID().toString(),
                ActionDecidedEvent.TYPE,
                action.getId(),
                action.getAgentId(),
                action.getActionType(),
                action.getRiskLevel(),
                result.outcome(),
                action.getStatus(),
                result.matchedPolicyId(),
                result.matchedPolicyName(),
                result.reason(),
                Instant.now()
        );
        publish(topics.getActionsDecided(), action.getId().toString(), event);
    }

    @Override
    public void publishCompleted(ActionRequest action, ApprovalRequest approval, UUID reviewerUserId) {
        ActionCompletedEvent event = new ActionCompletedEvent(
                UUID.randomUUID().toString(),
                ActionCompletedEvent.TYPE,
                action.getId(),
                approval.getId(),
                action.getAgentId(),
                action.getActionType(),
                action.getRiskLevel(),
                action.getStatus(),
                reviewerUserId,
                Instant.now()
        );
        publish(topics.getActionsCompleted(), action.getId().toString(), event);
    }

    private void publish(String topic, String key, Object payload) {
        if (topic == null || topic.isBlank()) {
            log.warn("Skipping publish: topic name is not configured.");
            return;
        }
        try {
            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, value);
            log.debug("Published event to {} (key={})", topic, key);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize event for topic {}: {}", topic, ex.getOriginalMessage());
        } catch (RuntimeException ex) {
            log.warn("Failed to publish event to {}: {}", topic, ex.getMessage());
        }
    }

    /** Enables KafkaProperties binding without needing a separate config class. */
    @Configuration
    @EnableConfigurationProperties(KafkaProperties.class)
    static class KafkaPropertiesConfig {
    }
}
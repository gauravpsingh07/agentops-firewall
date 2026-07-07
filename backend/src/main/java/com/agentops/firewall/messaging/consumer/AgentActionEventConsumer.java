package com.agentops.firewall.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the agent-action lifecycle stream. Subscribes to the
 * received / decided / completed topics and projects each event into
 * {@link LiveMetricsService}, giving the stream a live reader instead of
 * leaving it write-only.
 *
 * <p>Guarded by {@code agentops.messaging.consumers.enabled} (default true),
 * which is set false in the test profile so the suite never attempts a
 * broker connection.
 */
@Component
@ConditionalOnProperty(name = "agentops.messaging.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class AgentActionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AgentActionEventConsumer.class);

    private final LiveMetricsService metrics;
    private final ObjectMapper objectMapper;

    public AgentActionEventConsumer(LiveMetricsService metrics, ObjectMapper objectMapper) {
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {
                    "${agentops.messaging.kafka.topic.actions-received}",
                    "${agentops.messaging.kafka.topic.actions-decided}",
                    "${agentops.messaging.kafka.topic.actions-completed}"
            },
            groupId = "${agentops.messaging.consumers.group-id:agentops-firewall-metrics}")
    public void onActionEvent(String payload) {
        handle(payload);
    }

    /** Package-visible so tests can drive it without a broker. */
    void handle(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = text(node, "eventType", "ACTION_EVENT");
            metrics.record("kafka", type, summarize(node));
        } catch (Exception ex) {
            log.warn("Skipping malformed action event: {}", ex.getMessage());
        }
    }

    private String summarize(JsonNode node) {
        String actionType = text(node, "actionType", "?");
        String risk = text(node, "riskLevel", "?");
        String decision = node.hasNonNull("decision") ? node.get("decision").asText()
                : text(node, "finalStatus", null);
        return decision == null
                ? actionType + " (" + risk + ")"
                : actionType + " (" + risk + ") -> " + decision;
    }

    private String text(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) ? node.get(field).asText() : fallback;
    }
}

package com.agentops.firewall.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for the approval work queue. Drains the approval-request
 * and approval-notification queues and projects each task into
 * {@link LiveMetricsService}, so the durable work queue actually drives a
 * downstream reader (a reviewer-facing live feed) rather than filling up
 * unread.
 *
 * <p>Only created when RabbitMQ is on the classpath and configured (the
 * {@link RabbitTemplate} bean exists) and consumers are enabled — both false
 * in the test profile, so no broker connection is attempted there.
 */
@Component
@ConditionalOnBean(RabbitTemplate.class)
@ConditionalOnProperty(name = "agentops.messaging.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class ApprovalTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTaskConsumer.class);

    private final LiveMetricsService metrics;
    private final ObjectMapper objectMapper;

    public ApprovalTaskConsumer(LiveMetricsService metrics, ObjectMapper objectMapper) {
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${agentops.messaging.rabbitmq.queue.approval-requests}")
    public void onApprovalRequested(String payload) {
        record("APPROVAL_REQUESTED", payload);
    }

    @RabbitListener(queues = "${agentops.messaging.rabbitmq.queue.approval-notifications}")
    public void onApprovalNotification(String payload) {
        record("APPROVAL_NOTIFICATION", payload);
    }

    private void record(String type, String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            metrics.record("rabbitmq", type, summarize(node));
        } catch (Exception ex) {
            log.warn("Skipping malformed approval task: {}", ex.getMessage());
        }
    }

    private String summarize(JsonNode node) {
        String actionType = node.hasNonNull("actionType") ? node.get("actionType").asText() : "?";
        String status = node.hasNonNull("finalStatus") ? node.get("finalStatus").asText()
                : (node.hasNonNull("riskLevel") ? node.get("riskLevel").asText() : null);
        return status == null ? actionType : actionType + " -> " + status;
    }
}

package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.approval.ApprovalRequest;
import com.agentops.firewall.messaging.events.ApprovalCompletedTask;
import com.agentops.firewall.messaging.events.ApprovalRequestedTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Default {@link ApprovalTaskPublisher} implementation. Serializes
 * task records to JSON and publishes them to the configured RabbitMQ
 * queues via {@link RabbitTemplate}.
 *
 * <p>Failures are logged but never rethrown — the synchronous approval
 * response is never blocked on broker availability. This mirrors the
 * fire-and-forget strategy used by the Kafka publisher.
 *
 * <p>This bean is only created when a {@link RabbitTemplate} is
 * available. In the test profile (where RabbitMQ auto-config is
 * excluded), tests provide a {@code @MockBean} for the
 * {@link ApprovalTaskPublisher} interface instead.
 */
@Component
@Primary
@ConditionalOnBean(RabbitTemplate.class)
public class RabbitApprovalTaskPublisher implements ApprovalTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitApprovalTaskPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties queues;
    private final ObjectMapper objectMapper;

    public RabbitApprovalTaskPublisher(RabbitTemplate rabbitTemplate,
                                       RabbitProperties queues,
                                       ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.queues = queues;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishApprovalRequested(ApprovalRequest approval, ActionRequest action, Agent agent) {
        ApprovalRequestedTask task = new ApprovalRequestedTask(
                UUID.randomUUID().toString(),
                approval.getId(),
                action.getId(),
                agent.getId(),
                agent.getName(),
                action.getActionType().name(),
                action.getRiskLevel().name(),
                action.getResource(),
                action.getDecisionReason(),
                approval.getCreatedAt(),
                approval.getExpiresAt()
        );
        publish(queues.getApprovalRequests(), task);
    }

    @Override
    public void publishApprovalCompleted(ApprovalRequest approval, ActionRequest action, String finalStatus) {
        ApprovalCompletedTask task = new ApprovalCompletedTask(
                UUID.randomUUID().toString(),
                approval.getId(),
                action.getId(),
                action.getAgentId(),
                action.getActionType().name(),
                finalStatus,
                approval.getReviewerUserId(),
                Instant.now()
        );
        publish(queues.getApprovalNotifications(), task);
    }

    private void publish(String queue, Object payload) {
        if (queue == null || queue.isBlank()) {
            log.warn("Skipping publish: queue name is not configured.");
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend(queue, json);
            log.debug("Published task to queue {}", queue);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize task for queue {}: {}", queue, ex.getOriginalMessage());
        } catch (RuntimeException ex) {
            log.warn("Failed to publish task to queue {}: {}", queue, ex.getMessage());
        }
    }
}

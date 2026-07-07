package com.agentops.firewall.messaging.consumer;

import com.agentops.firewall.messaging.consumer.LiveMetricsService.LiveEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the broker consumers. The listener handler methods are
 * driven directly with JSON payloads — no Kafka or RabbitMQ broker is
 * involved.
 */
class MessagingConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final LiveMetricsService metrics = new LiveMetricsService();

    @Test
    @DisplayName("Kafka consumer projects a decided event into the metrics feed")
    void kafkaConsumerRecordsDecidedEvent() {
        AgentActionEventConsumer consumer = new AgentActionEventConsumer(metrics, objectMapper);

        consumer.handle("{\"eventType\":\"ACTION_DECIDED\",\"actionType\":\"DELETE_FILE\","
                + "\"riskLevel\":\"HIGH\",\"decision\":\"NEEDS_APPROVAL\"}");

        assertThat(metrics.counts()).containsEntry("ACTION_DECIDED", 1L);
        assertThat(metrics.recent()).hasSize(1);
        LiveEvent event = metrics.recent().get(0);
        assertThat(event.source()).isEqualTo("kafka");
        assertThat(event.summary()).contains("DELETE_FILE").contains("NEEDS_APPROVAL");
    }

    @Test
    @DisplayName("Kafka consumer swallows malformed payloads without recording")
    void kafkaConsumerIgnoresGarbage() {
        AgentActionEventConsumer consumer = new AgentActionEventConsumer(metrics, objectMapper);

        consumer.handle("not json at all");

        assertThat(metrics.counts()).isEmpty();
        assertThat(metrics.recent()).isEmpty();
    }

    @Test
    @DisplayName("Rabbit consumer projects an approval-requested task and notifies subscribers")
    void rabbitConsumerRecordsAndBroadcasts() {
        ApprovalTaskConsumer consumer = new ApprovalTaskConsumer(metrics, objectMapper);
        List<LiveEvent> received = new ArrayList<>();
        metrics.subscribe(received::add);

        consumer.onApprovalRequested("{\"approvalId\":\"a1\",\"actionType\":\"DELETE_FILE\","
                + "\"riskLevel\":\"MEDIUM\"}");

        assertThat(metrics.counts()).containsEntry("APPROVAL_REQUESTED", 1L);
        assertThat(received).hasSize(1);
        assertThat(received.get(0).source()).isEqualTo("rabbitmq");
        assertThat(received.get(0).summary()).contains("DELETE_FILE");
    }
}

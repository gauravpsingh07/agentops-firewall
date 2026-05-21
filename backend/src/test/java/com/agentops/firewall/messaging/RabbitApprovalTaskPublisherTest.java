package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.approval.ApprovalRequest;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RabbitApprovalTaskPublisher}. Uses mocked
 * {@link RabbitTemplate} — no broker connection required.
 */
class RabbitApprovalTaskPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private RabbitApprovalTaskPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        RabbitProperties props = new RabbitProperties();
        props.setApprovalRequests("test.approval.requests");
        props.setApprovalNotifications("test.approval.notifications");
        publisher = new RabbitApprovalTaskPublisher(rabbitTemplate, props, objectMapper);
    }

    @Test
    @DisplayName("publishApprovalRequested sends JSON to the requests queue")
    void publishApprovalRequestedSendsToQueue() {
        ApprovalRequest approval = buildApproval();
        ActionRequest action = buildAction();
        Agent agent = buildAgent();

        publisher.publishApprovalRequested(approval, action, agent);

        ArgumentCaptor<String> queueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(queueCaptor.capture(), msgCaptor.capture());
        assertThat(queueCaptor.getValue()).isEqualTo("test.approval.requests");
        String json = (String) msgCaptor.getValue();
        assertThat(json).contains("approvalRequestId");
        assertThat(json).contains("actionRequestId");
    }

    @Test
    @DisplayName("publishApprovalRequested payload excludes secrets")
    void payloadExcludesSecrets() {
        ApprovalRequest approval = buildApproval();
        ActionRequest action = buildAction();
        Agent agent = buildAgent();
        agent.setApiKeyHash("should_not_be_in_payload");

        publisher.publishApprovalRequested(approval, action, agent);

        ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(anyString(), msgCaptor.capture());
        String json = (String) msgCaptor.getValue();
        assertThat(json).doesNotContain("apiKey");
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("Bearer");
        assertThat(json).doesNotContain("should_not_be_in_payload");
    }

    @Test
    @DisplayName("publishApprovalCompleted sends JSON to the notifications queue")
    void publishApprovalCompletedSendsToQueue() {
        ApprovalRequest approval = buildApproval();
        approval.setReviewerUserId(UUID.randomUUID());
        ActionRequest action = buildAction();

        publisher.publishApprovalCompleted(approval, action, "APPROVED");

        ArgumentCaptor<String> queueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(queueCaptor.capture(), msgCaptor.capture());
        assertThat(queueCaptor.getValue()).isEqualTo("test.approval.notifications");
        String json = (String) msgCaptor.getValue();
        assertThat(json).contains("APPROVED");
    }

    @Test
    @DisplayName("no publish when queue name is blank")
    void noPublishWhenQueueBlank() {
        RabbitProperties props = new RabbitProperties();
        props.setApprovalRequests("");
        props.setApprovalNotifications("");
        RabbitApprovalTaskPublisher blankPublisher =
                new RabbitApprovalTaskPublisher(rabbitTemplate, props, objectMapper);

        blankPublisher.publishApprovalRequested(buildApproval(), buildAction(), buildAgent());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    // ── Helpers ────────────────────────────────────────────────────

    private ApprovalRequest buildApproval() {
        ApprovalRequest a = new ApprovalRequest();
        a.setActionRequestId(UUID.randomUUID());
        a.setStatus(ApprovalStatus.PENDING);
        a.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return a;
    }

    private ActionRequest buildAction() {
        ActionRequest a = new ActionRequest();
        a.setAgentId(UUID.randomUUID());
        a.setActionType(ActionType.DELETE_FILE);
        a.setResource("/tmp/important.txt");
        a.setRiskLevel(RiskLevel.MEDIUM);
        a.setStatus(ActionRequestStatus.PENDING_APPROVAL);
        a.setDecisionReason("Test reason");
        return a;
    }

    private Agent buildAgent() {
        Agent a = new Agent();
        a.setName("test-agent");
        a.setStatus(AgentStatus.ACTIVE);
        return a;
    }
}

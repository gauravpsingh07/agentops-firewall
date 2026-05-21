package com.agentops.firewall.messaging;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.approval.ApprovalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * No-operation fallback for {@link ApprovalTaskPublisher}. Always
 * registered. When a {@link RabbitApprovalTaskPublisher} is also
 * available it takes precedence (it is marked {@code @Primary}).
 */
@Component
public class NoOpApprovalTaskPublisher implements ApprovalTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpApprovalTaskPublisher.class);

    @Override
    public void publishApprovalRequested(ApprovalRequest approval, ActionRequest action, Agent agent) {
        log.warn("No-op: approval task publishing is disabled (no RabbitMQ connection).");
    }

    @Override
    public void publishApprovalCompleted(ApprovalRequest approval, ActionRequest action, String finalStatus) {
        log.warn("No-op: approval completed notification is disabled (no RabbitMQ connection).");
    }
}

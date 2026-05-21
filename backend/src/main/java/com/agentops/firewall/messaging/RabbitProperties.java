package com.agentops.firewall.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the {@code agentops.messaging.rabbitmq.queue.*}
 * settings in application.yml.
 */
@ConfigurationProperties(prefix = "agentops.messaging.rabbitmq.queue")
public class RabbitProperties {

    private String approvalRequests;
    private String approvalNotifications;

    public String getApprovalRequests() { return approvalRequests; }
    public void setApprovalRequests(String approvalRequests) { this.approvalRequests = approvalRequests; }

    public String getApprovalNotifications() { return approvalNotifications; }
    public void setApprovalNotifications(String approvalNotifications) { this.approvalNotifications = approvalNotifications; }
}

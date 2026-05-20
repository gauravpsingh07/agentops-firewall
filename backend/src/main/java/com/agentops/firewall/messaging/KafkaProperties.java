package com.agentops.firewall.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the {@code agentops.messaging.kafka.topic.*}
 * settings in application.yml. All values are env-driven; the defaults
 * are the topic names the dashboard reads in production.
 */
@ConfigurationProperties(prefix = "agentops.messaging.kafka.topic")
public class KafkaProperties {

    private String actionsReceived;
    private String actionsDecided;
    private String actionsCompleted;

    public String getActionsReceived() { return actionsReceived; }
    public void setActionsReceived(String actionsReceived) { this.actionsReceived = actionsReceived; }

    public String getActionsDecided() { return actionsDecided; }
    public void setActionsDecided(String actionsDecided) { this.actionsDecided = actionsDecided; }

    public String getActionsCompleted() { return actionsCompleted; }
    public void setActionsCompleted(String actionsCompleted) { this.actionsCompleted = actionsCompleted; }
}
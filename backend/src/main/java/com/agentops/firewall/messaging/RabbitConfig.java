package com.agentops.firewall.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ queues used by the approval workflow. Queue
 * names are read from {@link RabbitProperties} so they can be
 * overridden per profile.
 *
 * <p>Only active when RabbitMQ auto-configuration is present (i.e.,
 * when a {@link RabbitTemplate} bean is available).
 */
@Configuration
@ConditionalOnBean(RabbitTemplate.class)
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitConfig {

    @Bean
    public Queue approvalRequestsQueue(RabbitProperties props) {
        return new Queue(props.getApprovalRequests(), true);
    }

    @Bean
    public Queue approvalNotificationsQueue(RabbitProperties props) {
        return new Queue(props.getApprovalNotifications(), true);
    }
}

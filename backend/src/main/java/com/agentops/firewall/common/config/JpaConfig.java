package com.agentops.firewall.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@code @CreatedDate} and
 * {@code @LastModifiedDate} fields on {@link com.agentops.firewall.common.domain.BaseEntity}
 * are populated automatically.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

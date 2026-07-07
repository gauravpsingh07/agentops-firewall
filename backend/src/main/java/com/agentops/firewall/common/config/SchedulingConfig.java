package com.agentops.firewall.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's scheduled-task support for background jobs such as the
 * approval expiry sweeper.
 *
 * <p>Disabled under the {@code test} profile so integration tests remain
 * deterministic — the scheduled trigger never fires mid-test, while the
 * underlying worker methods stay directly invokable.
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}

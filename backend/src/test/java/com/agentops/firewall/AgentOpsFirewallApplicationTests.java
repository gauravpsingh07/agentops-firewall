package com.agentops.firewall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test for Phase 0 — verifies the Spring application context can be
 * loaded with the current minimal configuration. As new features land
 * (security, JPA, messaging) this test continues to act as a regression
 * canary for autoconfiguration.
 */
@SpringBootTest
class AgentOpsFirewallApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty — the context-load itself is the assertion.
    }
}

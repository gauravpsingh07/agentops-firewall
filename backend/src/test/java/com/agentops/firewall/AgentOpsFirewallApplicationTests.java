package com.agentops.firewall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Application context smoke test. Loads the full Spring context against the
 * H2-backed {@code test} profile and verifies it boots without error. As the
 * project grows (security, JPA, messaging) this remains a regression canary
 * for autoconfiguration.
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentOpsFirewallApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty — successful context-load is the assertion.
    }
}

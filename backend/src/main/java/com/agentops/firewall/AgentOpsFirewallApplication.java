package com.agentops.firewall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentOps Firewall — entry point.
 *
 * <p>The firewall mediates AI agent actions: it receives proposed actions,
 * validates agent identity, evaluates policy, optionally requires human
 * approval, and writes an immutable audit trail. This class is intentionally
 * minimal during Phase 0 (scaffolding). Subsequent phases introduce
 * security, persistence, messaging, and the policy engine.
 */
@SpringBootApplication
public class AgentOpsFirewallApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOpsFirewallApplication.class, args);
    }
}

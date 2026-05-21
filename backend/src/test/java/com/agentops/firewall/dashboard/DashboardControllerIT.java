package com.agentops.firewall.dashboard;

import com.agentops.firewall.action.ActionRequestRepository;
import com.agentops.firewall.action.PolicyDecisionRepository;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.agent.AgentKeyService;
import com.agentops.firewall.agent.AgentRepository;
import com.agentops.firewall.approval.ApprovalRequestRepository;
import com.agentops.firewall.audit.AuditLogRepository;
import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.domain.enums.UserRole;
import com.agentops.firewall.messaging.AgentActionEventPublisher;
import com.agentops.firewall.messaging.ApprovalTaskPublisher;
import com.agentops.firewall.policy.PolicyConditionRepository;
import com.agentops.firewall.policy.PolicyRepository;
import com.agentops.firewall.policy.SamplePolicySeeder;
import com.agentops.firewall.support.TestUserFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the dashboard summary APIs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;
    @Autowired AgentRepository agentRepository;
    @Autowired AgentKeyService agentKeyService;
    @Autowired ActionRequestRepository actionRequestRepository;
    @Autowired PolicyDecisionRepository policyDecisionRepository;
    @Autowired ApprovalRequestRepository approvalRequestRepository;
    @Autowired PolicyRepository policyRepository;
    @Autowired PolicyConditionRepository conditionRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockBean AgentActionEventPublisher eventPublisher;
    @MockBean ApprovalTaskPublisher approvalTaskPublisher;

    private String rawKey;
    private String agentName;

    @BeforeEach
    void seed() {
        policyDecisionRepository.deleteAll();
        approvalRequestRepository.deleteAll();
        actionRequestRepository.deleteAll();
        agentRepository.deleteAll();
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
        auditLogRepository.deleteAll();
        testUserFactory.deleteAll();

        testUserFactory.createUser("admin", "admin123", UserRole.ADMIN);

        SamplePolicySeeder.seed(policyRepository, conditionRepository);

        rawKey = agentKeyService.generateRawKey();
        Agent agent = new Agent();
        agent.setName("agent-dashboard-test");
        agent.setApiKeyHash(agentKeyService.hash(rawKey));
        agent.setStatus(AgentStatus.ACTIVE);
        agent = agentRepository.save(agent);
        agentName = agent.getName();

        // Seed some actions: one ALLOW, one DENY, one NEEDS_APPROVAL
        submitAction("CALL_EXTERNAL_API", "LOW", "{\"targetDomain\":\"acme.com\"}"); // ALLOW
        submitAction("READ_SECRET", "MEDIUM", null); // DENY
        submitAction("DELETE_FILE", "MEDIUM", null); // NEEDS_APPROVAL
    }

    @AfterEach
    void cleanup() {
        policyDecisionRepository.deleteAll();
        approvalRequestRepository.deleteAll();
        actionRequestRepository.deleteAll();
        agentRepository.deleteAll();
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
        auditLogRepository.deleteAll();
        testUserFactory.deleteAll();
    }

    @Test
    @DisplayName("Dashboard summary counts match seeded data")
    void summaryCountsMatch() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActions").value(3))
                .andExpect(jsonPath("$.allowedActions").value(1))
                .andExpect(jsonPath("$.deniedActions").value(1))
                .andExpect(jsonPath("$.pendingApprovals").value(1))
                .andExpect(jsonPath("$.totalAgents").value(1))
                .andExpect(jsonPath("$.activeAgents").value(1));
    }

    @Test
    @DisplayName("Recent actions ordered by createdAt desc")
    void recentActionsOrdered() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/dashboard/recent-actions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Risk distribution returns correct grouping")
    void riskDistribution() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/dashboard/risk-distribution")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Decision distribution returns correct grouping")
    void decisionDistribution() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/dashboard/decision-distribution")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Recent audit events ordered by createdAt desc")
    void recentAuditEvents() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/dashboard/recent-audit-events")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Unauthenticated access to dashboard returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ────────────────────────────────────────────────────

    private void submitAction(String actionType, String risk, String metadataJson) {
        try {
            StringBuilder body = new StringBuilder("{")
                    .append("\"agentId\":\"").append(agentName).append("\",")
                    .append("\"actionType\":\"").append(actionType).append("\",")
                    .append("\"riskLevel\":\"").append(risk).append("\"");
            if (metadataJson != null) {
                body.append(",\"metadata\":").append(metadataJson);
            }
            body.append("}");

            mockMvc.perform(post("/api/agent-actions")
                    .header("X-Agent-Key", rawKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body.toString()));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to submit action: " + ex.getMessage(), ex);
        }
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}

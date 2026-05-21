package com.agentops.firewall.security;

import com.agentops.firewall.common.domain.enums.UserRole;
import com.agentops.firewall.support.TestUserFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-cutting authorization tests. Each per-controller IT already
 * exercises happy paths; this class instead asserts the security
 * boundary: every JWT-protected route returns 401 without a token, and
 * write endpoints reject the wrong role with 403 even when the caller is
 * authenticated.
 *
 * <p>Kept in a single class so the RBAC matrix is reviewable at a glance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;

    @BeforeEach
    void seed() {
        testUserFactory.deleteAll();
        testUserFactory.createUser("admin",    "admin123",    UserRole.ADMIN);
        testUserFactory.createUser("reviewer", "reviewer123", UserRole.REVIEWER);
        testUserFactory.createUser("viewer",   "viewer123",   UserRole.VIEWER);
    }

    @AfterEach
    void clean() {
        testUserFactory.deleteAll();
    }

    // ------------------------------------------------------------------ 401

    @Test
    @DisplayName("GET /api/agents returns 401 without a bearer token")
    void agentsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/policies returns 401 without a bearer token")
    void policiesRequireAuth() throws Exception {
        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/audit-logs returns 401 without a bearer token")
    void auditLogsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/dashboard/summary returns 401 without a bearer token")
    void dashboardRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/agent-actions returns 401 when X-Agent-Key is missing")
    void agentIngestionRequiresAgentKey() throws Exception {
        String body = """
                {
                  "agentId": "agent-not-registered",
                  "actionType": "SEND_EMAIL",
                  "resource": "external_email",
                  "riskLevel": "MEDIUM"
                }
                """;

        mockMvc.perform(post("/api/agent-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/agent-actions returns 401 with a bogus X-Agent-Key")
    void agentIngestionRejectsBogusAgentKey() throws Exception {
        String body = """
                {
                  "agentId": "agent-not-registered",
                  "actionType": "SEND_EMAIL",
                  "resource": "external_email",
                  "riskLevel": "MEDIUM"
                }
                """;

        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", "agk_not_a_real_key_at_all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 403

    @Test
    @DisplayName("VIEWER is forbidden from creating an agent")
    void viewerCannotCreateAgent() throws Exception {
        String token = login("viewer", "viewer123");

        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"agent-x\",\"description\":\"viewer cannot create\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("REVIEWER is forbidden from creating an agent")
    void reviewerCannotCreateAgent() throws Exception {
        String token = login("reviewer", "reviewer123");

        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"agent-y\",\"description\":\"reviewer cannot create\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER is forbidden from creating a policy")
    void viewerCannotCreatePolicy() throws Exception {
        String token = login("viewer", "viewer123");

        String body = """
                {
                  "name": "viewer attempt",
                  "effect": "ALLOW",
                  "priority": 50,
                  "conditions": []
                }
                """;

        mockMvc.perform(post("/api/policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER is forbidden from approving an approval request")
    void viewerCannotApprove() throws Exception {
        String token = login("viewer", "viewer123");
        UUID nonExistent = UUID.randomUUID();

        // We do not need a real approval here: the security filter rejects
        // the call before the controller has a chance to load any row.
        mockMvc.perform(post("/api/approvals/" + nonExistent + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER can still GET /api/policies (read-only access)")
    void viewerCanListPolicies() throws Exception {
        String token = login("viewer", "viewer123");

        mockMvc.perform(get("/api/policies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("token").asText();
    }
}

package com.agentops.firewall.agent;

import com.agentops.firewall.audit.AuditLogRepository;
import com.agentops.firewall.common.domain.enums.AgentStatus;
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
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;
    @Autowired AgentRepository agentRepository;
    @Autowired AgentKeyService agentKeyService;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void seed() {
        auditLogRepository.deleteAll();
        agentRepository.deleteAll();
        testUserFactory.deleteAll();
        testUserFactory.createUser("admin",    "admin123",    UserRole.ADMIN);
        testUserFactory.createUser("reviewer", "reviewer123", UserRole.REVIEWER);
        testUserFactory.createUser("viewer",   "viewer123",   UserRole.VIEWER);
    }

    @AfterEach
    void clean() {
        agentRepository.deleteAll();
        testUserFactory.deleteAll();
    }

    @Test
    @DisplayName("ADMIN can create an agent and the raw key is returned exactly once")
    void adminCanCreateAgent() throws Exception {
        String token = login("admin", "admin123");

        MvcResult result = mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"agent-email-bot-01\",\"description\":\"sends emails\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agent.name").value("agent-email-bot-01"))
                .andExpect(jsonPath("$.agent.status").value("ACTIVE"))
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.warning").exists())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String rawKey = body.get("apiKey").asText();
        UUID agentId = UUID.fromString(body.get("agent").get("id").asText());

        assertThat(rawKey).startsWith("agk_");
        assertThat(rawKey.length()).isGreaterThan(20);

        Agent stored = agentRepository.findById(agentId).orElseThrow();
        assertThat(stored.getApiKeyHash()).startsWith("$2");
        assertThat(stored.getApiKeyHash()).doesNotContain(rawKey);
        assertThat(agentKeyService.matches(rawKey, stored.getApiKeyHash())).isTrue();
    }

    @Test
    @DisplayName("rotating the key invalidates the old key and exposes the new one once")
    void rotateKeyInvalidatesOldKey() throws Exception {
        String token = login("admin", "admin123");

        JsonNode created = postJson("/api/agents", token,
                "{\"name\":\"agent-rotate-01\"}", status().isCreated());
        UUID agentId = UUID.fromString(created.get("agent").get("id").asText());
        String firstKey = created.get("apiKey").asText();
        String firstHash = agentRepository.findById(agentId).orElseThrow().getApiKeyHash();

        MvcResult rotateResult = mockMvc.perform(post("/api/agents/" + agentId + "/rotate-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").exists())
                .andReturn();
        JsonNode rotated = objectMapper.readTree(rotateResult.getResponse().getContentAsString());
        String secondKey = rotated.get("apiKey").asText();

        assertThat(secondKey).isNotEqualTo(firstKey);
        Agent reloaded = agentRepository.findById(agentId).orElseThrow();
        assertThat(reloaded.getApiKeyHash()).isNotEqualTo(firstHash);
        assertThat(agentKeyService.matches(secondKey, reloaded.getApiKeyHash())).isTrue();
        assertThat(agentKeyService.matches(firstKey, reloaded.getApiKeyHash())).isFalse();
    }

    @Test
    @DisplayName("REVIEWER cannot create an agent")
    void reviewerCannotCreate() throws Exception {
        String token = login("reviewer", "reviewer123");
        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"agent-x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("REVIEWER can list agents")
    void reviewerCanList() throws Exception {
        String adminToken = login("admin", "admin123");
        postJson("/api/agents", adminToken, "{\"name\":\"agent-a\"}", status().isCreated());

        String reviewerToken = login("reviewer", "reviewer123");
        mockMvc.perform(get("/api/agents")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("agent-a"));
    }

    @Test
    @DisplayName("PATCH can disable an agent")
    void patchCanDisableAgent() throws Exception {
        String token = login("admin", "admin123");
        JsonNode created = postJson("/api/agents", token,
                "{\"name\":\"agent-toggle\"}", status().isCreated());
        UUID id = UUID.fromString(created.get("agent").get("id").asText());

        mockMvc.perform(patch("/api/agents/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(agentRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(AgentStatus.DISABLED);
    }

    @Test
    @DisplayName("duplicate agent name returns 409 Conflict")
    void duplicateAgentNameReturns409() throws Exception {
        String token = login("admin", "admin123");
        postJson("/api/agents", token, "{\"name\":\"agent-dup\"}", status().isCreated());
        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"agent-dup\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("validation rejects empty agent name")
    void blankAgentNameFails400() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(post("/api/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("unauthenticated request returns 401")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("agent-created audit log is written but contains no raw key or hash")
    void auditLogOmitsSecrets() throws Exception {
        String token = login("admin", "admin123");
        JsonNode created = postJson("/api/agents", token,
                "{\"name\":\"agent-audit\"}", status().isCreated());
        String rawKey = created.get("apiKey").asText();

        var logs = auditLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "AGENT_CREATED".equals(l.getEventType()));
        for (var log : logs) {
            String summary = log.getSummary();
            String details = log.getDetailsJson() == null ? "" : log.getDetailsJson();
            assertThat(summary).doesNotContain(rawKey);
            assertThat(details).doesNotContain(rawKey);
            assertThat(details).doesNotContain("$2a$");
            assertThat(details).doesNotContain("$2b$");
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

    private JsonNode postJson(String url, String token, String body, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(expectedStatus)
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
package com.agentops.firewall.policy;

import com.agentops.firewall.audit.AuditLogRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;
    @Autowired PolicyRepository policyRepository;
    @Autowired PolicyConditionRepository conditionRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void seed() {
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
        auditLogRepository.deleteAll();
        testUserFactory.deleteAll();
        testUserFactory.createUser("admin", "admin123", UserRole.ADMIN);
        testUserFactory.createUser("reviewer", "reviewer123", UserRole.REVIEWER);
    }

    @AfterEach
    void cleanup() {
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
        testUserFactory.deleteAll();
    }

    @Test
    @DisplayName("ADMIN can create a policy with conditions; audit log records the creation")
    void adminCanCreatePolicy() throws Exception {
        String token = login("admin", "admin123");

        String body = "{"
                + "\"name\":\"Deny X actions\","
                + "\"effect\":\"DENY\","
                + "\"priority\":75,"
                + "\"actionType\":\"DELETE_FILE\","
                + "\"conditions\":[{\"field\":\"metadata.target\",\"operator\":\"EQUALS\",\"value\":\"prod\"}]"
                + "}";

        MvcResult result = mockMvc.perform(post("/api/policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Deny X actions"))
                .andExpect(jsonPath("$.effect").value("DENY"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.conditions.length()").value(1))
                .andExpect(jsonPath("$.conditions[0].operator").value("EQUALS"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(json.get("id").asText());
        assertThat(policyRepository.findById(id)).isPresent();

        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "POLICY_CREATED".equals(l.getEventType()));
    }

    @Test
    @DisplayName("REVIEWER can list policies but cannot create them")
    void reviewerCannotCreate() throws Exception {
        String reviewerToken = login("reviewer", "reviewer123");
        mockMvc.perform(post("/api/policies")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"reviewer-blocked-create\",\"effect\":\"DENY\",\"priority\":10}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/policies")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH replaces the condition set when conditions are provided")
    void patchReplacesConditions() throws Exception {
        String token = login("admin", "admin123");

        String createBody = "{"
                + "\"name\":\"Patch target\","
                + "\"effect\":\"NEEDS_APPROVAL\","
                + "\"priority\":50,"
                + "\"conditions\":[{\"field\":\"metadata.a\",\"operator\":\"EQUALS\",\"value\":\"1\"}]"
                + "}";
        MvcResult created = mockMvc.perform(post("/api/policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText());

        String patchBody = "{"
                + "\"conditions\":["
                + "{\"field\":\"metadata.b\",\"operator\":\"EQUALS\",\"value\":\"2\"},"
                + "{\"field\":\"metadata.c\",\"operator\":\"EQUALS\",\"value\":\"3\"}"
                + "]}";
        mockMvc.perform(patch("/api/policies/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conditions.length()").value(2));

        assertThat(conditionRepository.findByPolicyId(id)).hasSize(2);
    }

    @Test
    @DisplayName("DELETE soft-disables the policy and writes an audit log")
    void deleteSoftDisables() throws Exception {
        String token = login("admin", "admin123");
        MvcResult created = mockMvc.perform(post("/api/policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"To disable\",\"effect\":\"DENY\",\"priority\":10}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText());

        mockMvc.perform(delete("/api/policies/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(policyRepository.findById(id).orElseThrow().isEnabled()).isFalse();
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "POLICY_DISABLED".equals(l.getEventType()));
    }

    @Test
    @DisplayName("duplicate policy name returns 409")
    void duplicatePolicyNameConflict() throws Exception {
        String token = login("admin", "admin123");
        String body = "{\"name\":\"Dup\",\"effect\":\"DENY\",\"priority\":10}";
        mockMvc.perform(post("/api/policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("validation rejects missing effect on create")
    void missingEffectFails400() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(post("/api/policies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NoEffect\",\"priority\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
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
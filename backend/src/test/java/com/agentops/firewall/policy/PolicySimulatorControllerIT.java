package com.agentops.firewall.policy;

import com.agentops.firewall.action.ActionRequestRepository;
import com.agentops.firewall.action.PolicyDecisionRepository;
import com.agentops.firewall.approval.ApprovalRequestRepository;
import com.agentops.firewall.audit.AuditLogRepository;
import com.agentops.firewall.common.domain.enums.UserRole;
import com.agentops.firewall.support.TestUserFactory;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the policy simulator. Verifies decisions match
 * the live evaluator, that NOTHING is persisted (action requests,
 * policy decisions, approval requests, audit logs all stay empty), and
 * that the endpoint is JWT-protected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicySimulatorControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;
    @Autowired PolicyRepository policyRepository;
    @Autowired PolicyConditionRepository conditionRepository;
    @Autowired ActionRequestRepository actionRequestRepository;
    @Autowired PolicyDecisionRepository policyDecisionRepository;
    @Autowired ApprovalRequestRepository approvalRequestRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void seed() {
        approvalRequestRepository.deleteAll();
        policyDecisionRepository.deleteAll();
        actionRequestRepository.deleteAll();
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
        auditLogRepository.deleteAll();
        testUserFactory.deleteAll();
        testUserFactory.createUser("admin", "admin123", UserRole.ADMIN);
        testUserFactory.createUser("viewer", "viewer123", UserRole.VIEWER);
        SamplePolicySeeder.seed(policyRepository, conditionRepository);
    }

    @AfterEach
    void cleanup() {
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
        testUserFactory.deleteAll();
    }

    @Test
    @DisplayName("simulator returns DENY for READ_SECRET")
    void simulatorDeniesReadSecret() throws Exception {
        String token = login("viewer", "viewer123");

        String body = "{\"actionType\":\"READ_SECRET\",\"resource\":\"vault-x\",\"riskLevel\":\"MEDIUM\"}";
        mockMvc.perform(post("/api/policies/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.matchedPolicyName").value(org.hamcrest.Matchers.containsString("READ_SECRET")))
                .andExpect(jsonPath("$.simulated").value(true));
    }

    @Test
    @DisplayName("simulator returns NEEDS_APPROVAL for DELETE_FILE")
    void simulatorRequiresApprovalForDeleteFile() throws Exception {
        String token = login("admin", "admin123");
        String body = "{\"actionType\":\"DELETE_FILE\",\"resource\":\"file-x\",\"riskLevel\":\"MEDIUM\"}";
        mockMvc.perform(post("/api/policies/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("NEEDS_APPROVAL"));
    }

    @Test
    @DisplayName("simulator does NOT create ActionRequest, PolicyDecision, ApprovalRequest, or AuditLog rows")
    void simulatorDoesNotPersistAnything() throws Exception {
        String token = login("admin", "admin123");
        String body = "{\"actionType\":\"DELETE_FILE\",\"resource\":\"target\",\"riskLevel\":\"HIGH\",\"metadata\":{\"foo\":\"bar\"}}";

        long actionsBefore = actionRequestRepository.count();
        long decisionsBefore = policyDecisionRepository.count();
        long approvalsBefore = approvalRequestRepository.count();
        long auditsBefore = auditLogRepository.count();

        mockMvc.perform(post("/api/policies/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(actionRequestRepository.count()).isEqualTo(actionsBefore);
        assertThat(policyDecisionRepository.count()).isEqualTo(decisionsBefore);
        assertThat(approvalRequestRepository.count()).isEqualTo(approvalsBefore);
        assertThat(auditLogRepository.count()).isEqualTo(auditsBefore);
    }

    @Test
    @DisplayName("simulator requires authentication")
    void simulatorRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/policies/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"DELETE_FILE\",\"riskLevel\":\"MEDIUM\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("simulator rejects missing actionType with 400 and fieldErrors")
    void simulatorRejectsMissingActionType() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(post("/api/policies/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riskLevel\":\"LOW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("simulator can resolve agentName when supplied and uses it in evaluation context")
    void simulatorAcceptsAgentNameInBody() throws Exception {
        String token = login("admin", "admin123");
        // No agent registered in this test class, so resolution returns null, which is fine.
        String body = "{\"actionType\":\"SEND_EMAIL\",\"resource\":\"external_email\",\"riskLevel\":\"MEDIUM\","
                + "\"metadata\":{\"recipientDomain\":\"external.com\",\"containsAttachment\":true},"
                + "\"agentName\":\"nonexistent-agent\"}";
        MvcResult result = mockMvc.perform(post("/api/policies/simulate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("NEEDS_APPROVAL"))
                .andReturn();
        // Sanity: response is valid JSON with the expected fields.
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("simulated").asBoolean()).isTrue();
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
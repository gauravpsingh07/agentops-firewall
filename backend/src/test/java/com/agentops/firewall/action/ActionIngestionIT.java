package com.agentops.firewall.action;

import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.agent.AgentKeyService;
import com.agentops.firewall.agent.AgentRepository;
import com.agentops.firewall.audit.AuditLogRepository;
import com.agentops.firewall.approval.ApprovalRequest;
import com.agentops.firewall.approval.ApprovalRequestRepository;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
import com.agentops.firewall.common.domain.enums.UserRole;
import com.agentops.firewall.messaging.AgentActionEventPublisher;
import com.agentops.firewall.messaging.ApprovalTaskPublisher;
import com.agentops.firewall.policy.PolicyConditionRepository;
import com.agentops.firewall.policy.PolicyEvaluationResult;
import com.agentops.firewall.policy.PolicyRepository;
import com.agentops.firewall.policy.SamplePolicySeeder;
import com.agentops.firewall.support.TestUserFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the action-ingestion pipeline. Seeds an active
 * agent and the sample policy set, then exercises the X-Agent-Key
 * authentication, the policy evaluator, the persistence transitions,
 * and the audit-log writes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActionIngestionIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;
    @Autowired AgentRepository agentRepository;
    @Autowired AgentKeyService agentKeyService;
    @Autowired ActionRequestRepository actionRequestRepository;
    @Autowired PolicyDecisionRepository policyDecisionRepository;
    @Autowired PolicyRepository policyRepository;
    @Autowired PolicyConditionRepository conditionRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockBean AgentActionEventPublisher eventPublisher;
    @MockBean ApprovalTaskPublisher approvalTaskPublisher;
    @Autowired ApprovalRequestRepository approvalRequestRepository;

    private UUID agentId;
    private String agentName;
    private String rawKey;

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
        agent.setName("agent-email-bot-01");
        agent.setApiKeyHash(agentKeyService.hash(rawKey));
        agent.setStatus(AgentStatus.ACTIVE);
        agent = agentRepository.save(agent);
        agentId = agent.getId();
        agentName = agent.getName();
    }

    @AfterEach
    void cleanup() {
        policyDecisionRepository.deleteAll();
        approvalRequestRepository.deleteAll();
        actionRequestRepository.deleteAll();
        agentRepository.deleteAll();
        conditionRepository.deleteAll();
        policyRepository.deleteAll();
        testUserFactory.deleteAll();
    }

    @Test
    @DisplayName("missing X-Agent-Key returns 401 and persists nothing")
    void missingAgentKeyReturns401() throws Exception {
        long actionsBefore = actionRequestRepository.count();
        mockMvc.perform(post("/api/agent-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "DELETE_FILE", "MEDIUM", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        assertThat(actionRequestRepository.count()).isEqualTo(actionsBefore);
    }

    @Test
    @DisplayName("invalid X-Agent-Key returns 401 and persists nothing")
    void invalidAgentKeyReturns401() throws Exception {
        long actionsBefore = actionRequestRepository.count();
        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", "agk_definitely-not-the-right-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "DELETE_FILE", "MEDIUM", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        assertThat(actionRequestRepository.count()).isEqualTo(actionsBefore);
    }

    @Test
    @DisplayName("ALLOW outcome: LOW-risk action -> status ALLOWED, decision persisted, audit logs written, Kafka published")
    void allowOutcomePersistsAllowedStatus() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "CALL_EXTERNAL_API", "LOW",
                                "{\"targetDomain\":\"acme.com\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.status").value("ALLOWED"))
                .andReturn();

        UUID actionId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("actionId").asText());

        ActionRequest persisted = actionRequestRepository.findById(actionId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ActionRequestStatus.ALLOWED);
        assertThat(policyDecisionRepository.findByActionRequestIdOrderByEvaluatedAtAsc(actionId))
                .hasSize(1);
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "ACTION_RECEIVED".equals(l.getEventType()))
                .anyMatch(l -> "POLICY_DECISION".equals(l.getEventType()));

        // Kafka publish verifications: one received-event, one decided-event.
        ArgumentCaptor<ActionRequest> receivedAction = ArgumentCaptor.forClass(ActionRequest.class);
        ArgumentCaptor<Agent> receivedAgent = ArgumentCaptor.forClass(Agent.class);
        verify(eventPublisher, times(1)).publishReceived(receivedAction.capture(), receivedAgent.capture());
        assertThat(receivedAction.getValue().getId()).isEqualTo(actionId);
        assertThat(receivedAgent.getValue().getName()).isEqualTo(agentName);

        ArgumentCaptor<ActionRequest> decidedAction = ArgumentCaptor.forClass(ActionRequest.class);
        ArgumentCaptor<PolicyEvaluationResult> decidedResult = ArgumentCaptor.forClass(PolicyEvaluationResult.class);
        verify(eventPublisher, times(1)).publishDecided(decidedAction.capture(), decidedResult.capture());
        assertThat(decidedAction.getValue().getId()).isEqualTo(actionId);
        assertThat(decidedResult.getValue().outcome().name()).isEqualTo("ALLOW");
    }

    @Test
    @DisplayName("a failed agent-auth attempt never publishes a Kafka event")
    void failedAuthDoesNotPublish() throws Exception {
        mockMvc.perform(post("/api/agent-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "DELETE_FILE", "MEDIUM", null)))
                .andExpect(status().isUnauthorized());
        verify(eventPublisher, never()).publishReceived(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishDecided(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("DENY outcome: READ_SECRET -> status DENIED")
    void denyOutcomePersistsDeniedStatus() throws Exception {
        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "READ_SECRET", "MEDIUM", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.status").value("DENIED"));
    }

    @Test
    @DisplayName("NEEDS_APPROVAL outcome: DELETE_FILE -> status PENDING_APPROVAL, ApprovalRequest created")
    void needsApprovalOutcomePersistsPending() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "DELETE_FILE", "MEDIUM", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("NEEDS_APPROVAL"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.approvalId").isNotEmpty())
                .andReturn();

        String approvalIdStr = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("approvalId").asText();
        UUID approvalId = UUID.fromString(approvalIdStr);
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("disabled agent cannot submit an action (uniform 401)")
    void disabledAgentReturns401() throws Exception {
        Agent agent = agentRepository.findById(agentId).orElseThrow();
        agent.setStatus(AgentStatus.DISABLED);
        agentRepository.save(agent);

        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "DELETE_FILE", "MEDIUM", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/agent-actions requires a JWT (401 anonymous)")
    void getRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/agent-actions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/agent-actions returns paged decisions to ADMIN")
    void adminCanListActions() throws Exception {
        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "DELETE_FILE", "MEDIUM", null)))
                .andExpect(status().isCreated());

        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/agent-actions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("GET /api/agent-actions filters by status")
    void filterByStatus() throws Exception {
        // One ALLOW, one DENY.
        mockMvc.perform(post("/api/agent-actions").header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "CALL_EXTERNAL_API", "LOW",
                                "{\"targetDomain\":\"acme.com\"}")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/agent-actions").header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "READ_SECRET", "MEDIUM", null)))
                .andExpect(status().isCreated());

        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/agent-actions?status=DENIED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DENIED"));
    }

    @Test
    @DisplayName("validation rejects missing actionType with 400")
    void validationRejectsMissingActionType() throws Exception {
        String body = "{\"agentId\":\"" + agentName + "\",\"riskLevel\":\"LOW\"}";
        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    // ── Completion callback (POST /{id}/complete) ─────────────────────

    @Test
    @DisplayName("agent completes an ALLOWED action -> status COMPLETED, audit written")
    void completeAllowedActionSucceeds() throws Exception {
        UUID actionId = submitAllowedAction();

        mockMvc.perform(post("/api/agent-actions/" + actionId + "/complete")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true,\"detail\":\"sent 1 email\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        ActionRequest action = actionRequestRepository.findById(actionId).orElseThrow();
        assertThat(action.getStatus()).isEqualTo(ActionRequestStatus.COMPLETED);
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "ACTION_COMPLETED".equals(l.getEventType()));
    }

    @Test
    @DisplayName("success=false marks the action FAILED")
    void completeWithFailureMarksFailed() throws Exception {
        UUID actionId = submitAllowedAction();

        mockMvc.perform(post("/api/agent-actions/" + actionId + "/complete")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":false,\"detail\":\"smtp timeout\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(actionRequestRepository.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(ActionRequestStatus.FAILED);
    }

    @Test
    @DisplayName("completing an already-completed action returns 409")
    void completeTwiceReturns409() throws Exception {
        UUID actionId = submitAllowedAction();
        mockMvc.perform(post("/api/agent-actions/" + actionId + "/complete")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/agent-actions/" + actionId + "/complete")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("completing with a wrong agent key returns 401")
    void completeWithWrongKeyReturns401() throws Exception {
        UUID actionId = submitAllowedAction();
        mockMvc.perform(post("/api/agent-actions/" + actionId + "/complete")
                        .header("X-Agent-Key", "agk_not-the-right-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true}"))
                .andExpect(status().isUnauthorized());

        assertThat(actionRequestRepository.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(ActionRequestStatus.ALLOWED);
    }

    @Test
    @DisplayName("completing an unknown action returns 404")
    void completeUnknownActionReturns404() throws Exception {
        mockMvc.perform(post("/api/agent-actions/" + UUID.randomUUID() + "/complete")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"success\":true}"))
                .andExpect(status().isNotFound());
    }

    /** Submit a LOW-risk action that the sample policy set ALLOWs, returning its id. */
    private UUID submitAllowedAction() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody(agentName, "CALL_EXTERNAL_API", "LOW",
                                "{\"targetDomain\":\"acme.com\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ALLOWED"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("actionId").asText());
    }

    private String jsonBody(String agent, String actionType, String risk, String metadataJson) {
        StringBuilder sb = new StringBuilder("{")
                .append("\"agentId\":\"").append(agent).append("\",")
                .append("\"actionType\":\"").append(actionType).append("\",")
                .append("\"riskLevel\":\"").append(risk).append("\"");
        if (metadataJson != null) {
            sb.append(",\"metadata\":").append(metadataJson);
        }
        sb.append("}");
        return sb.toString();
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
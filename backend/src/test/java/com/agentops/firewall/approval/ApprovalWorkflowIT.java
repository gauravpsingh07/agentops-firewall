package com.agentops.firewall.approval;

import com.agentops.firewall.action.ActionRequest;
import com.agentops.firewall.action.ActionRequestRepository;
import com.agentops.firewall.action.PolicyDecisionRepository;
import com.agentops.firewall.agent.Agent;
import com.agentops.firewall.agent.AgentKeyService;
import com.agentops.firewall.agent.AgentRepository;
import com.agentops.firewall.audit.AuditLogRepository;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.domain.enums.ApprovalStatus;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the full approval workflow:
 * <ol>
 *   <li>NEEDS_APPROVAL action creates an ApprovalRequest in PENDING state.</li>
 *   <li>Reviewer can list, approve, and reject pending approvals.</li>
 *   <li>VIEWER can list but cannot approve/reject.</li>
 *   <li>Idempotency: acting on already-decided requests returns 409.</li>
 *   <li>RabbitMQ task published for NEEDS_APPROVAL, not for ALLOW/DENY.</li>
 *   <li>Kafka completed event published on approve/reject.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovalWorkflowIT {

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
        testUserFactory.createUser("reviewer", "rev123", UserRole.REVIEWER);
        testUserFactory.createUser("viewer", "view123", UserRole.VIEWER);

        SamplePolicySeeder.seed(policyRepository, conditionRepository);

        rawKey = agentKeyService.generateRawKey();
        Agent agent = new Agent();
        agent.setName("agent-approval-test");
        agent.setApiKeyHash(agentKeyService.hash(rawKey));
        agent.setStatus(AgentStatus.ACTIVE);
        agent = agentRepository.save(agent);
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
        auditLogRepository.deleteAll();
        testUserFactory.deleteAll();
    }

    // ── Ingestion creates ApprovalRequest ─────────────────────────────

    @Test
    @DisplayName("NEEDS_APPROVAL creates ApprovalRequest with PENDING status and approvalId in response")
    void needsApprovalCreatesApprovalRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson(agentName, "DELETE_FILE", "MEDIUM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("NEEDS_APPROVAL"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.approvalId").isNotEmpty())
                .andReturn();

        UUID approvalId = UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("approvalId").asText());
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(approval.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("ALLOW does not create ApprovalRequest")
    void allowDoesNotCreateApproval() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson(agentName, "CALL_EXTERNAL_API", "LOW",
                                "{\"targetDomain\":\"acme.com\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.approvalId").doesNotExist())
                .andReturn();

        assertThat(approvalRequestRepository.count()).isZero();
    }

    @Test
    @DisplayName("DENY does not create ApprovalRequest")
    void denyDoesNotCreateApproval() throws Exception {
        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson(agentName, "READ_SECRET", "MEDIUM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.approvalId").doesNotExist());

        assertThat(approvalRequestRepository.count()).isZero();
    }

    // ── RabbitMQ task publishing ───────────────────────────────────────

    @Test
    @DisplayName("RabbitMQ task published for NEEDS_APPROVAL")
    void rabbitTaskPublishedForNeedsApproval() throws Exception {
        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson(agentName, "DELETE_FILE", "MEDIUM")))
                .andExpect(status().isCreated());

        verify(approvalTaskPublisher).publishApprovalRequested(
                any(ApprovalRequest.class), any(ActionRequest.class), any(Agent.class));
    }

    @Test
    @DisplayName("RabbitMQ task NOT published for ALLOW")
    void rabbitTaskNotPublishedForAllow() throws Exception {
        mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson(agentName, "CALL_EXTERNAL_API", "LOW",
                                "{\"targetDomain\":\"acme.com\"}")))
                .andExpect(status().isCreated());

        verify(approvalTaskPublisher, never()).publishApprovalRequested(any(), any(), any());
    }

    // ── Listing approvals ─────────────────────────────────────────────

    @Test
    @DisplayName("Reviewer can list pending approvals")
    void reviewerCanListApprovals() throws Exception {
        submitNeedsApprovalAction();
        String token = login("reviewer", "rev123");

        mockMvc.perform(get("/api/approvals")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("VIEWER can list approvals (read-only)")
    void viewerCanListApprovals() throws Exception {
        submitNeedsApprovalAction();
        String token = login("viewer", "view123");

        mockMvc.perform(get("/api/approvals")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("Unauthenticated access to approvals returns 401")
    void unauthenticatedApprovalsReturns401() throws Exception {
        mockMvc.perform(get("/api/approvals"))
                .andExpect(status().isUnauthorized());
    }

    // ── Approve ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Reviewer can approve a pending request; action status becomes APPROVED")
    void reviewerCanApprove() throws Exception {
        UUID approvalId = submitNeedsApprovalAction();
        String token = login("reviewer", "rev123");

        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Looks good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewerNote").value("Looks good"));

        ApprovalRequest approval = approvalRequestRepository.findById(approvalId).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getDecidedAt()).isNotNull();

        ActionRequest action = actionRequestRepository.findById(approval.getActionRequestId()).orElseThrow();
        assertThat(action.getStatus()).isEqualTo(ActionRequestStatus.APPROVED);

        // Verify Kafka completed event
        verify(eventPublisher).publishCompleted(any(ActionRequest.class),
                any(ApprovalRequest.class), any(UUID.class));

        // Verify RabbitMQ notification
        verify(approvalTaskPublisher).publishApprovalCompleted(
                any(ApprovalRequest.class), any(ActionRequest.class), eq("APPROVED"));

        // Verify audit log
        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "APPROVAL_APPROVED".equals(l.getEventType()));
    }

    // ── Reject ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reviewer can reject a pending request; action status becomes REJECTED")
    void reviewerCanReject() throws Exception {
        UUID approvalId = submitNeedsApprovalAction();
        String token = login("reviewer", "rev123");

        mockMvc.perform(post("/api/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Not allowed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        ApprovalRequest approval = approvalRequestRepository.findById(approvalId).orElseThrow();
        assertThat(approval.getStatus()).isEqualTo(ApprovalStatus.REJECTED);

        ActionRequest action = actionRequestRepository.findById(approval.getActionRequestId()).orElseThrow();
        assertThat(action.getStatus()).isEqualTo(ActionRequestStatus.REJECTED);

        verify(eventPublisher).publishCompleted(any(), any(), any());
        verify(approvalTaskPublisher).publishApprovalCompleted(any(), any(), eq("REJECTED"));

        assertThat(auditLogRepository.findAll())
                .anyMatch(l -> "APPROVAL_REJECTED".equals(l.getEventType()));
    }

    // ── Authorization ─────────────────────────────────────────────────

    @Test
    @DisplayName("VIEWER cannot approve (403)")
    void viewerCannotApprove() throws Exception {
        UUID approvalId = submitNeedsApprovalAction();
        String token = login("viewer", "view123");

        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER cannot reject (403)")
    void viewerCannotReject() throws Exception {
        UUID approvalId = submitNeedsApprovalAction();
        String token = login("viewer", "view123");

        mockMvc.perform(post("/api/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── Idempotency (409) ─────────────────────────────────────────────

    @Test
    @DisplayName("Approving already-approved returns 409")
    void approvingAlreadyApprovedReturns409() throws Exception {
        UUID approvalId = submitNeedsApprovalAction();
        String token = login("admin", "admin123");

        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rejecting already-rejected returns 409")
    void rejectingAlreadyRejectedReturns409() throws Exception {
        UUID approvalId = submitNeedsApprovalAction();
        String token = login("admin", "admin123");

        mockMvc.perform(post("/api/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("ADMIN can approve (not just REVIEWER)")
    void adminCanApprove() throws Exception {
        UUID approvalId = submitNeedsApprovalAction();
        String token = login("admin", "admin123");

        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Admin approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Submit a DELETE_FILE action (NEEDS_APPROVAL) and return the
     * approval request ID.
     */
    private UUID submitNeedsApprovalAction() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/agent-actions")
                        .header("X-Agent-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionJson(agentName, "DELETE_FILE", "MEDIUM")))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString()).get("approvalId").asText());
    }

    private String actionJson(String agent, String actionType, String risk) {
        return actionJson(agent, actionType, risk, null);
    }

    private String actionJson(String agent, String actionType, String risk, String metadataJson) {
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

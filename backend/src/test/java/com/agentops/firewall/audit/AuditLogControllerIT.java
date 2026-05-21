package com.agentops.firewall.audit;

import com.agentops.firewall.common.domain.enums.UserRole;
import com.agentops.firewall.messaging.AgentActionEventPublisher;
import com.agentops.firewall.messaging.ApprovalTaskPublisher;
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
 * Integration tests for the audit log search API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AuditService auditService;
    @MockBean AgentActionEventPublisher eventPublisher;
    @MockBean ApprovalTaskPublisher approvalTaskPublisher;

    @BeforeEach
    void seed() {
        auditLogRepository.deleteAll();
        testUserFactory.deleteAll();
        testUserFactory.createUser("admin", "admin123", UserRole.ADMIN);
        testUserFactory.createUser("viewer", "view123", UserRole.VIEWER);

        // Seed some audit entries
        auditService.record("ACTION_RECEIVED", "AGENT", null, "ACTION_REQUEST", null,
                "Action received", null);
        auditService.record("POLICY_DECISION", "SYSTEM", null, "ACTION_REQUEST", null,
                "Policy decision", null);
        auditService.record("APPROVAL_REQUESTED", "SYSTEM", null, "APPROVAL_REQUEST", null,
                "Approval requested", null);
    }

    @AfterEach
    void cleanup() {
        auditLogRepository.deleteAll();
        testUserFactory.deleteAll();
    }

    @Test
    @DisplayName("Audit log search returns seeded records")
    void searchReturnsRecords() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("Filter by eventType works")
    void filterByEventType() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/audit-logs?eventType=APPROVAL_REQUESTED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("APPROVAL_REQUESTED"));
    }

    @Test
    @DisplayName("VIEWER can access audit logs")
    void viewerCanAccessAuditLogs() throws Exception {
        String token = login("viewer", "view123");
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated access returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Pagination works")
    void paginationWorks() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/audit-logs?page=0&size=2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
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

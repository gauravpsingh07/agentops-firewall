package com.agentops.firewall.auth;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/auth/* and the JWT-protected /me endpoint.
 * Runs against the H2-backed test profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TestUserFactory testUserFactory;

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

    @Test
    @DisplayName("POST /api/auth/login returns 200 + token + user on valid credentials")
    void loginSuccess() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin123\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("expiresInSeconds").asLong()).isPositive();
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 with ApiError shape on bad password")
    void loginFailureBadPassword() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"wrong-password\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 for unknown username")
    void loginFailureUnknownUser() throws Exception {
        String body = "{\"username\":\"nobody\",\"password\":\"whatever\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 with fieldErrors on blank input")
    void loginValidationFailure() throws Exception {
        String body = "{\"username\":\"\",\"password\":\"\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 without a bearer token")
    void meRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 200 + principal when bearer token is valid")
    void meReturnsPrincipal() throws Exception {
        String token = obtainToken("reviewer", "reviewer123");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("reviewer"))
                .andExpect(jsonPath("$.role").value("REVIEWER"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 with a malformed bearer token")
    void meRejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }

    private String obtainToken(String username, String password) throws Exception {
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

package com.agentops.firewall.stream;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the SSE live-stream endpoint: it is JWT-protected and
 * accepts the token via the {@code access_token} query parameter (since
 * EventSource cannot send headers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveStreamControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;

    @BeforeEach
    void seed() {
        testUserFactory.deleteAll();
        testUserFactory.createUser("admin", "admin123", UserRole.ADMIN);
    }

    @AfterEach
    void cleanup() {
        testUserFactory.deleteAll();
    }

    @Test
    @DisplayName("anonymous access to the stream is rejected with 401")
    void anonymousStreamRejected() throws Exception {
        mockMvc.perform(get("/api/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid access_token query param opens the stream (async started)")
    void tokenQueryParamOpensStream() throws Exception {
        String token = login("admin", "admin123");

        // asyncStarted() confirms the token was accepted (otherwise a 401
        // would short-circuit before the controller returned the emitter).
        // We intentionally do not call getAsyncResult(): an SSE emitter never
        // "completes" on its own, so blocking on it would hang the test.
        mockMvc.perform(get("/api/stream")
                        .param("access_token", token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("a bogus access_token is rejected with 401")
    void bogusTokenRejected() throws Exception {
        mockMvc.perform(get("/api/stream")
                        .param("access_token", "not-a-real-jwt")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
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

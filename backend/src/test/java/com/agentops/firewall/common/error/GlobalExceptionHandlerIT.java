package com.agentops.firewall.common.error;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts that previously-uncaught request-shape errors now return a
 * uniform 400 ApiError instead of bleeding into the catch-all 500
 * handler:
 *
 * <ul>
 *   <li>malformed JSON body
 *   <li>wrong-type path variable (a non-UUID where a UUID is expected)
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestUserFactory testUserFactory;

    @BeforeEach
    void seed() {
        testUserFactory.deleteAll();
        testUserFactory.createUser("admin", "admin123", UserRole.ADMIN);
    }

    @AfterEach
    void clean() {
        testUserFactory.deleteAll();
    }

    @Test
    @DisplayName("malformed JSON body returns 400 with a friendly ApiError, not 500")
    void malformedBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request body is missing or not valid JSON."))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    @DisplayName("non-UUID path variable returns 400 with a parsing-hint message, not 500")
    void nonUuidPathVariableReturns400() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/agents/not-a-real-uuid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("could not be parsed as UUID")))
                .andExpect(jsonPath("$.path").value("/api/agents/not-a-real-uuid"));
    }

    @Test
    @DisplayName("empty body on /api/auth/login returns 400, not 500")
    void emptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }
}

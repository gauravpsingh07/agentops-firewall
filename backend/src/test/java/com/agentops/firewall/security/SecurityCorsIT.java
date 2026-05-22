package com.agentops.firewall.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for the {@link SecurityConfig#corsConfigurationSource}
 * bean. The bean is invisible to a happy-path login flow tested in
 * isolation — Spring Security's {@code .cors(cors -> {})} keeps responding
 * {@code 200 OK} either way — but a real browser silently refuses to
 * expose the JWT to JavaScript when the {@code Access-Control-Allow-Origin}
 * response header is missing, which manifests as the Angular dashboard
 * "succeeding" the login POST and then never navigating to /dashboard.
 *
 * <p>These tests assert the headers that the browser checks, on both
 * the {@code POST /api/auth/login} cross-origin request and the
 * preflight {@code OPTIONS} request that precedes any non-simple call.
 * They lock the {@link org.springframework.web.cors.CorsConfigurationSource}
 * bean into the application context: removing it (or accidentally
 * dropping {@code .cors(cors -> {})} from the filter chain) fails this
 * suite, so a future security refactor cannot silently regress the
 * dev frontend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityCorsIT {

    /** Matches the default allowed origin from application properties. */
    private static final String DEV_FRONTEND_ORIGIN = "http://localhost:4200";

    /** An origin deliberately outside the allow-list. */
    private static final String DISALLOWED_ORIGIN = "http://evil.example.com";

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/auth/login from an allowed origin echoes Access-Control-Allow-Origin")
    void crossOriginLoginEmitsAllowOriginHeader() throws Exception {
        // Note: this request does NOT need to succeed in authenticating —
        // we only care that the CORS filter attached the ACAO header to
        // the response. A non-existent username is fine; the response
        // will be a 401 or 200 depending on H2 seed state, but the
        // CORS header is emitted regardless because the filter sits
        // ahead of authentication.
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, DEV_FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"nothing\"}"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        DEV_FRONTEND_ORIGIN));
    }

    @Test
    @DisplayName("OPTIONS preflight from an allowed origin returns the full CORS header set")
    void preflightFromAllowedOriginReturnsCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, DEV_FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        DEV_FRONTEND_ORIGIN))
                .andExpect(header().stringValues(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("POST"))))
                .andExpect(header().stringValues(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("Authorization"))));
    }

    @Test
    @DisplayName("OPTIONS preflight from a disallowed origin does NOT emit Access-Control-Allow-Origin")
    void preflightFromDisallowedOriginOmitsAllowOriginHeader() throws Exception {
        // Spring Security's CORS filter responds 403 to a preflight
        // whose Origin is not on the allow-list, and crucially does
        // NOT add the ACAO header. The browser then refuses to send
        // the real request — exactly the protective behaviour we want.
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("Cross-origin GET on a protected route still emits ACAO, even without a token")
    void crossOriginRequestOnProtectedRouteEmitsAllowOriginHeader() throws Exception {
        // The CORS filter runs before authentication: an unauthenticated
        // cross-origin GET to /api/dashboard/summary returns 401, but
        // the response still carries the ACAO header. Without that,
        // the browser would block the SPA from even reading the 401 to
        // know it needs to redirect to /login.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/dashboard/summary")
                        .header(HttpHeaders.ORIGIN, DEV_FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        DEV_FRONTEND_ORIGIN));
    }
}

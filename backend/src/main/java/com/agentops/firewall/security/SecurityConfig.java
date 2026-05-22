package com.agentops.firewall.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Application security configuration:
 * <ul>
 *   <li>Stateless sessions (JWT-based authentication only).</li>
 *   <li>Public {@code /api/auth/login} and actuator health endpoint.</li>
 *   <li>Everything else under {@code /api/**} requires a valid bearer token.</li>
 *   <li>Method-level {@code @PreAuthorize} enabled for fine-grained RBAC.</li>
 *   <li>Custom JSON entry-point and access-denied handler so error bodies
 *       match the shape produced by GlobalExceptionHandler.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(AppUserDetailsService uds,
                                                                PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    /**
     * CORS configuration source consumed by Spring Security's
     * {@code .cors(cors -> {})} hook in {@link #filterChain}.
     *
     * <p>Without this bean Spring Security still wires the CORS filter, but
     * because there is no configuration source the filter never emits the
     * {@code Access-Control-Allow-Origin} response header. Browsers then
     * block JavaScript from reading any cross-origin response, so the
     * Angular dev server at {@code http://localhost:4200} cannot consume
     * the JSON returned by {@code POST /api/auth/login} — the login form
     * receives a 200 but the {@code .subscribe} callback rejects, and the
     * router never navigates to {@code /dashboard}.</p>
     *
     * <p>The allowed-origin list is overridable via the
     * {@code agentops.security.cors.allowed-origins} property so each
     * deployment can lock it down to its real frontend URL. The default
     * covers the two local-dev addresses (Angular dev server on 4200,
     * the containerised frontend served on the host's port 80) so
     * {@code mvnw spring-boot:run} works against a fresh checkout.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${agentops.security.cors.allowed-origins:http://localhost:4200,http://localhost}")
            List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Agent-Key"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofMinutes(30));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           RestAuthenticationEntryPoint entryPoint,
                                           RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                // Agent action submissions are authenticated by X-Agent-Key
                // in AgentAuthenticationService rather than by a user JWT.
                // The endpoint is opened in Spring Security so that the
                // service layer can produce a uniform 401 when the agent
                // credential is missing or wrong.
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/agent-actions").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

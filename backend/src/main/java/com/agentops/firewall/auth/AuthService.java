package com.agentops.firewall.auth;

import com.agentops.firewall.auth.dto.AuthenticatedUserResponse;
import com.agentops.firewall.auth.dto.LoginRequest;
import com.agentops.firewall.auth.dto.LoginResponse;
import com.agentops.firewall.security.AppUserPrincipal;
import com.agentops.firewall.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Application-level authentication facade. Delegates credential
 * verification to Spring Security's AuthenticationManager and mints a JWT
 * via JwtService on success.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();

        String token = jwtService.issueToken(principal.getUsername(), principal.getRole());
        long expiresInSeconds = jwtService.getTokenLifetime().toSeconds();

        AuthenticatedUserResponse userResponse = new AuthenticatedUserResponse(
                principal.getId(), principal.getUsername(), principal.getRole());
        return new LoginResponse(token, "Bearer", expiresInSeconds, userResponse);
    }

    public AuthenticatedUserResponse currentUser(AppUserPrincipal principal) {
        return new AuthenticatedUserResponse(
                principal.getId(), principal.getUsername(), principal.getRole());
    }
}

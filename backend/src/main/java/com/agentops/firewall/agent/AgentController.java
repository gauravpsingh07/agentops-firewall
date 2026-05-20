package com.agentops.firewall.agent;

import com.agentops.firewall.agent.dto.AgentCreatedResponse;
import com.agentops.firewall.agent.dto.AgentResponse;
import com.agentops.firewall.agent.dto.CreateAgentRequest;
import com.agentops.firewall.agent.dto.UpdateAgentRequest;
import com.agentops.firewall.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Agent registry endpoints. Read operations are available to any
 * authenticated user; mutating operations require the ADMIN role.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public List<AgentResponse> list() {
        return agentService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public AgentResponse get(@PathVariable UUID id) {
        return agentService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentCreatedResponse> create(
            @Valid @RequestBody CreateAgentRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        AgentCreatedResponse created = agentService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAgentRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return agentService.update(id, request, principal.getId());
    }

    @PostMapping("/{id}/rotate-key")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentCreatedResponse rotateKey(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return agentService.rotateKey(id, principal.getId());
    }
}

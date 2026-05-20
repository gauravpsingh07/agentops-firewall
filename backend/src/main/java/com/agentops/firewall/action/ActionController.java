package com.agentops.firewall.action;

import com.agentops.firewall.action.dto.ActionDecisionResponse;
import com.agentops.firewall.action.dto.ActionRequestResponse;
import com.agentops.firewall.action.dto.SubmitActionRequest;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.common.error.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Action ingestion + query endpoints.
 *
 * <p>{@code POST /api/agent-actions} is authenticated via X-Agent-Key
 * (handled in the service layer) and is therefore permitted in the
 * security configuration without a JWT.
 *
 * <p>{@code GET /api/agent-actions} and {@code GET /api/agent-actions/{id}}
 * are JWT-protected and available to any authenticated role.
 */
@RestController
@RequestMapping("/api/agent-actions")
public class ActionController {

    private final ActionIngestionService ingestionService;
    private final ActionRequestRepository actionRequestRepository;

    public ActionController(ActionIngestionService ingestionService,
                             ActionRequestRepository actionRequestRepository) {
        this.ingestionService = ingestionService;
        this.actionRequestRepository = actionRequestRepository;
    }

    @PostMapping
    public ResponseEntity<ActionDecisionResponse> submit(
            @Valid @RequestBody SubmitActionRequest body,
            @RequestHeader(value = "X-Agent-Key", required = false) String agentKey) {
        ActionDecisionResponse response = ingestionService.submit(body, agentKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public Page<ActionRequestResponse> list(
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) ActionRequestStatus status,
            @RequestParam(required = false) RiskLevel riskLevel,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        // In-memory filter for now; can be promoted to a Specification or
        // a derived query in a later phase once we have richer indexes.
        List<ActionRequestResponse> all = actionRequestRepository.findAll().stream()
                .filter(a -> agentId == null || agentId.equals(a.getAgentId()))
                .filter(a -> actionType == null || actionType == a.getActionType())
                .filter(a -> status == null || status == a.getStatus())
                .filter(a -> riskLevel == null || riskLevel == a.getRiskLevel())
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(ActionRequestResponse::fromEntity)
                .toList();
        int from = Math.min((int) pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public ActionRequestResponse get(@PathVariable UUID id) {
        return actionRequestRepository.findById(id)
                .map(ActionRequestResponse::fromEntity)
                .orElseThrow(() -> new NotFoundException("Action request not found: " + id));
    }
}
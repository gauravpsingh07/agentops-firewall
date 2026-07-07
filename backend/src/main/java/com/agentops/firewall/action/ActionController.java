package com.agentops.firewall.action;

import com.agentops.firewall.action.dto.ActionCompletionResponse;
import com.agentops.firewall.action.dto.ActionDecisionResponse;
import com.agentops.firewall.action.dto.ActionRequestResponse;
import com.agentops.firewall.action.dto.CompleteActionRequest;
import com.agentops.firewall.action.dto.SubmitActionRequest;
import com.agentops.firewall.common.domain.enums.ActionRequestStatus;
import com.agentops.firewall.common.domain.enums.ActionType;
import com.agentops.firewall.common.domain.enums.RiskLevel;
import com.agentops.firewall.common.error.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    @PostMapping("/{id}/complete")
    public ActionCompletionResponse complete(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteActionRequest body,
            @RequestHeader(value = "X-Agent-Key", required = false) String agentKey) {
        return ingestionService.complete(id, body, agentKey);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public Page<ActionRequestResponse> list(
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) ActionRequestStatus status,
            @RequestParam(required = false) RiskLevel riskLevel,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        // Filtering, sorting, and pagination are all pushed to the database
        // via a JPA Specification so the endpoint never materializes the
        // whole table into memory.
        Specification<ActionRequest> spec =
                ActionRequestSpecifications.withFilters(agentId, actionType, status, riskLevel);
        return actionRequestRepository.findAll(spec, pageable)
                .map(ActionRequestResponse::fromEntity);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','VIEWER')")
    public ActionRequestResponse get(@PathVariable UUID id) {
        return actionRequestRepository.findById(id)
                .map(ActionRequestResponse::fromEntity)
                .orElseThrow(() -> new NotFoundException("Action request not found: " + id));
    }
}
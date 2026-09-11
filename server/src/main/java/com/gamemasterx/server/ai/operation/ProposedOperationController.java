package com.gamemasterx.server.ai.operation;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.encounter.model.EncounterDto;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller exposing the single, backend-authoritative path for AI-proposed
 * operations.
 *
 * <p>A proposal submitted here is validated through every backend-authoritative
 * dimension &ndash; schema, entity existence, authorization, actor control,
 * legal action, target, range, resources, expected revision, idempotency,
 * numeric agreement and secret disclosure &ndash; by the orchestrating
 * {@link com.gamemasterx.server.ai.operation.validate.OperationValidator} before
 * any deterministic execution. Authorization is enforced by the backend for
 * every proposed operation, and the deterministic rules engine remains the sole
 * authority for the legal-action decision. A proposal that fails any dimension is
 * rejected with a {@code 400 BAD_REQUEST} before anything is persisted, so the AI
 * never mutates the store directly and can never apply an invalid change.</p>
 *
 * <p>This controller exposes exactly one mutating endpoint; the read-only
 * catalogue of the versioned schemas themselves is published by the
 * {@link OperationSchemaController}.</p>
 */
@RestController
@RequestMapping("/api/encounters")
public class ProposedOperationController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final ProposedOperationExecutionService executionService;

    /**
     * @param executionService the backend-authoritative validation-and-execution path
     */
    public ProposedOperationController(ProposedOperationExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Validates and, only when valid, applies a single AI-proposed operation to
     * the owning encounter. The caller must be authenticated; the operation's
     * required role is enforced by the backend during validation.
     *
     * @param encounterId    the stable identifier of the owning encounter
     * @param request        the proposal to validate and apply
     * @param expectedRevision the revision the caller expects, or omitted to skip
     *                         the optimistic-concurrency guard
     * @param idempotencyKey   the caller-supplied idempotency key for retry-safety,
     *                         or omitted when the operation is not retry-guarded
     * @param request        the underlying HTTP request for the authenticated actor
     * @return the updated encounter
     */
    @PostMapping("/{id}/operations")
    public ResponseEntity<EncounterDto> applyOperation(
            @PathVariable String id,
            @RequestBody ProposedOperationRequest request,
            @RequestParam(value = "expectedRevision", required = false) Long expectedRevision,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        String correlationId = getCorrelationId(httpRequest);
        return ResponseEntity.ok(executionService.execute(
                id, request, expectedRevision, idempotencyKey, actor, correlationId));
    }

    /**
     * Resolves the authenticated actor from the request context established by the
     * auth filter, or raises an authorization denial when the caller is not
     * authenticated.
     */
    private String requireActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new AuthorizationException(MembershipRole.OBSERVER,
                "Authentication required to apply a proposed operation");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    private static ErrorResponse buildBadRequest(String message, HttpServletRequest request) {
        return new ErrorResponse("BAD_REQUEST",
                message == null ? "The proposed operation was invalid" : message,
                getCorrelationId(request), java.util.List.of());
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }
}

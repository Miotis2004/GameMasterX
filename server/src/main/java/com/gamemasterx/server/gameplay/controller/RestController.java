package com.gamemasterx.server.gameplay.controller;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.encounter.model.EncounterDto;
import com.gamemasterx.server.encounter.service.EncounterService;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.filter.AuthFilter;
import com.gamemasterx.server.gameplay.service.RestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing the short-rest and long-rest recovery rules of the
 * supported rules subset.
 *
 * <p>All endpoints run on the backend API port (5172). Each rest is resolved
 * through {@link RestService} &ndash; which recovers hit points and resources by
 * the subset's deterministic short-rest and long-rest rules &ndash; and then
 * applied to every participant of the owning encounter and persisted through
 * {@link EncounterService}. The change is recorded in the immutable turn and
 * audit history as an accepted mutation capturing the before/after hit points,
 * Hit Dice and resource state of every participant.</p>
 *
 * <p>Every rest is gated, entirely in backend code, by turn ownership and action
 * availability: the owning encounter must be {@code ACTIVE} (actions are
 * available) and it must be the requesting actor's turn. This is the same
 * server-side gate used by the other gameplay actions, so an out-of-turn or
 * unavailable rest can never be resolved or recorded, regardless of what the UI
 * happens to show.</p>
 *
 * <p>Every endpoint requires an authenticated actor (see
 * {@link #requireActor(HttpServletRequest)}), matching the role-gated
 * conventions of the rest of the API.</p>
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api/encounters")
public class RestController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final EncounterService encounterService;

    /**
     * Creates the rest controller.
     *
     * @param encounterService the encounter service that applies and persists the rest
     */
    public RestController(EncounterService encounterService) {
        this.encounterService = encounterService;
    }

    /**
     * Resolves a short rest for the whole encounter. Each participant spends any
     * number of their available Hit Dice (up to {@code hitDiceToSpend}) to
     * recover hit points, resolved deterministically by {@link RestService}.
     *
     * @param id               the owning encounter
     * @param request          the short-rest input
     * @param httpRequest      the incoming request, used for the authenticated actor
     * @return the updated encounter, with each participant's hit points and Hit
     *         Dice restored by the short rest
     */
    @PostMapping("/{id}/rest/short")
    public ResponseEntity<EncounterDto> shortRest(
            @PathVariable String id,
            @Valid @RequestBody ShortRestRequest request,
            HttpServletRequest httpRequest) {
        String mode = (request.rollMode() != null && !request.rollMode().isBlank())
                ? request.rollMode().toLowerCase() : "random";
        if ("seeded".equals(mode) && (request.seed() == null || request.seed().isBlank())) {
            throw new IllegalArgumentException("A seed is required for a seeded short rest roll");
        }
        return ResponseEntity.ok(encounterService.performShortRest(
                id, requireActor(httpRequest),
                request.hitDiceToSpend(), request.hitDieSize(),
                request.conModifier(), request.rollMode(), request.seed(),
                request.note()));
    }

    /**
     * Resolves a long rest for the whole encounter. Every participant recovers all
     * hit points, recovers spent Hit Dice up to half their total, and recovers
     * every other resource to its maximum, resolved deterministically by {@link
     * RestService}.
     *
     * @param id          the owning encounter
     * @param request     the long-rest input
     * @param httpRequest the incoming request, used for the authenticated actor
     * @return the updated encounter, with every participant restored by the long rest
     */
    @PostMapping("/{id}/rest/long")
    public ResponseEntity<EncounterDto> longRest(
            @PathVariable String id,
            @Valid @RequestBody LongRestRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.performLongRest(
                id, requireActor(httpRequest), request.note()));
    }

    /**
     * Resolves the authenticated actor from the request context established by
     * the auth filter, or throws an authorization denial when the caller is not
     * authenticated.
     */
    private String requireActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new AuthorizationException(MembershipRole.OBSERVER,
                "Authentication required to take a rest");
    }

    /**
     * Maps an illegal encounter action (for example a rest taken out of turn,
     * while the encounter is paused, or against a participant that does not
     * exist) to a {@code 400 Bad Request} response, unless it is a missing
     * encounter, which is a {@code 404 Not Found}. This controller-level handler
     * takes precedence over the global handler for exceptions thrown within this
     * controller.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        String message = ex.getMessage();
        boolean notFound = message != null && message.toLowerCase().contains("not found");
        HttpStatus status = notFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String title = notFound ? "NOT_FOUND" : "BAD_REQUEST";
        return ResponseEntity.status(status).body(buildResponse(title, message, request));
    }

    private static ErrorResponse buildResponse(String title, String message, HttpServletRequest request) {
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new ArrayList<>();
        return new ErrorResponse(title,
                message == null ? "The rest request was invalid" : message,
                getCorrelationId(request), fieldErrors);
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }
}

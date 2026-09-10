package com.gamemasterx.server.encounter.controller;

import com.gamemasterx.server.encounter.EncounterStateTransitionException;
import com.gamemasterx.server.encounter.RulesProfileValidationException;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.EncounterCreateRequest;
import com.gamemasterx.server.encounter.model.EncounterDto;
import com.gamemasterx.server.encounter.service.EncounterService;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.filter.AuthFilter;
import com.gamemasterx.server.gameplay.model.Audit;
import com.gamemasterx.server.gameplay.model.Turn;
import com.gamemasterx.server.gameplay.service.GameplayAuditService;
import com.gamemasterx.server.encounter.controller.DamageRequest;
import com.gamemasterx.server.encounter.controller.HealingRequest;
import com.gamemasterx.server.encounter.controller.TemporaryHitPointsRequest;
import com.gamemasterx.server.encounter.controller.ConditionRequest;
import com.gamemasterx.server.encounter.controller.DeathSaveRequest;
import com.gamemasterx.server.encounter.controller.MoveRequest;
import com.gamemasterx.server.gameplay.MovementRangeValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing the Encounter aggregate.
 *
 * <p>All endpoints run on the backend API port (5172). Role requirements are
 * delegated to the {@link EncounterService}, which enforces them against the
 * {@link MembershipRole} hierarchy. Reads require at least the {@link
 * MembershipRole#OBSERVER} role; lifecycle transitions and draft organisation
 * require the {@link MembershipRole#GAME_MASTER} role.</p>
 */
@RestController
@RequestMapping("/api/encounters")
public class EncounterController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final EncounterService encounterService;
    private final GameplayAuditService gameplayAuditService;

    public EncounterController(EncounterService encounterService, GameplayAuditService gameplayAuditService) {
        this.encounterService = encounterService;
        this.gameplayAuditService = gameplayAuditService;
    }

    /**
     * Creates a new draft encounter. Role requirement: the caller must hold at
     * least the {@link MembershipRole#OBSERVER} role in the referenced campaign.
     */
    @PostMapping
    public ResponseEntity<EncounterDto> createEncounter(@Valid @RequestBody EncounterCreateRequest request,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.createEncounter(request, requireActor(httpRequest)));
    }

    /**
     * Lists the encounters of a campaign. Role requirement: the caller must hold
     * at least the {@link MembershipRole#OBSERVER} role in the campaign.
     */
    @GetMapping
    public ResponseEntity<List<EncounterDto>> listCampaignEncounters(@RequestParam(required = false) String campaignId,
                                                                     HttpServletRequest httpRequest) {
        // The campaign id is supplied as a query parameter; when omitted every
        // accessible encounter is returned.
        return ResponseEntity.ok(encounterService.listForCampaign(campaignId, requireActor(httpRequest)));
    }

    /**
     * Reads a single encounter. Role requirement: the caller must hold at least
     * the {@link MembershipRole#OBSERVER} role in the campaign the encounter
     * belongs to.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EncounterDto> getEncounter(@PathVariable String id,
                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.findById(id, requireActor(httpRequest)));
    }

    /**
     * Adds or replaces a participant in a draft encounter. Role requirement: the
     * caller must hold the {@link MembershipRole#GAME_MASTER} role.
     */
    @PostMapping("/{id}/participants")
    public ResponseEntity<EncounterDto> addParticipant(@PathVariable String id,
                                                       @RequestBody Encounter.Participant participant,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.addParticipant(id, participant, requireActor(httpRequest)));
    }

    /**
     * Removes a participant from a draft encounter. Role requirement: the caller
     * must hold the {@link MembershipRole#GAME_MASTER} role.
     */
    @DeleteMapping("/{id}/participants/{participantId}")
    public ResponseEntity<EncounterDto> removeParticipant(@PathVariable String id,
                                                          @PathVariable String participantId,
                                                          HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.removeParticipant(id, participantId, requireActor(httpRequest)));
    }

    /**
     * Starts a draft encounter (DRAFT &rarr; ACTIVE). Role requirement: the
     * caller must hold the {@link MembershipRole#GAME_MASTER} role.
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<EncounterDto> startEncounter(
            @PathVariable String id,
            @RequestParam(required = false) String rollMode,
            @RequestParam(required = false) String seed,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.startEncounter(
                id, requireActor(httpRequest), rollMode, seed));
    }

    /**
     * Generates combat initiative for every participant in a draft encounter by
     * rolling a {@code 1d20} for each through the dice subsystem, records the
     * auditable before/after initiative state and recomputes the initiative order.
     * The encounter must remain in the DRAFT state so the order can be re-rolled
     * before the encounter is started.
     *
     * <p>{@code rollMode} is {@code "random"} (default) or {@code "seeded"}; a
     * {@code seed} is required when the mode is {@code "seeded"}. Seeded rolls
     * reproduce the same initiative order for a given seed.</p>
     */
    @PostMapping("/{id}/initiative")
    public ResponseEntity<InitiativeResponse> generateInitiative(
            @PathVariable String id,
            @RequestParam(required = false) String rollMode,
            @RequestParam(required = false) String seed,
            HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        String mode = (rollMode != null && !rollMode.isBlank()) ? rollMode.toLowerCase() : "random";
        if ("seeded".equals(mode) && (seed == null || seed.isBlank())) {
            throw new IllegalArgumentException("A seed is required for a seeded initiative roll");
        }
        var rollout = encounterService.generateInitiative(id, mode, seed, actor);
        return ResponseEntity.ok(
                InitiativeResponse.of(rollout.initiativeOrder(), rollout.results()));
    }

    /**
     * Returns the immutable turn history for an encounter, in round/turn order.
     * This is where initiative-generation turns and their auditable dice results
     * are surfaced.
     */
    @GetMapping("/{id}/turns")
    public ResponseEntity<List<Turn>> encounterTurns(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        return ResponseEntity.ok(gameplayAuditService.turnsForEncounter(id));
    }

    /**
     * Returns the immutable audit log for an encounter, in append (sequence)
     * order. Initiative-generation results are auditable here with their revision
     * before and after.
     */
    @GetMapping("/{id}/audit")
    public ResponseEntity<List<Audit>> encounterAudit(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        return ResponseEntity.ok(gameplayAuditService.auditLogForEncounter(id));
    }

    /**
     * Pauses an active encounter (ACTIVE &rarr; PAUSED). Role requirement: the
     * caller must hold the {@link MembershipRole#GAME_MASTER} role.
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<EncounterDto> pauseEncounter(@PathVariable String id,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.pauseEncounter(id, requireActor(httpRequest)));
    }

    /**
     * Resumes a paused encounter (PAUSED &rarr; ACTIVE). Role requirement: the
     * caller must hold the {@link MembershipRole#GAME_MASTER} role.
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<EncounterDto> resumeEncounter(@PathVariable String id,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.resumeEncounter(id, requireActor(httpRequest)));
    }

    /**
     * Completes an encounter (ACTIVE/PAUSED &rarr; COMPLETED). Role requirement:
     * the caller must hold the {@link MembershipRole#GAME_MASTER} role.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<EncounterDto> completeEncounter(@PathVariable String id,
                                                          HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.completeEncounter(id, requireActor(httpRequest)));
    }

    /**
     * Advances the encounter to the next turn. Role requirement: the caller must
     * hold the {@link MembershipRole#GAME_MASTER} role.
     */
    @PostMapping("/{id}/turns/advance")
    public ResponseEntity<EncounterDto> advanceTurn(@PathVariable String id,
                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.advanceTurn(id, requireActor(httpRequest)));
    }

    /**
     * Moves a participant to a new grid square (ACTIVE). The caller must hold
     * the {@link MembershipRole#GAME_MASTER} role. The movement distance is
     * validated server-side against the participant's available movement; a
     * movement that exceeds the available movement is rejected with a clear
     * diagnostic.
     */
    @PostMapping("/{id}/participants/{participantId}/move")
    public ResponseEntity<EncounterDto> moveParticipant(
            @PathVariable String id,
            @PathVariable String participantId,
            @Valid @RequestBody MoveRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.moveParticipant(
                id, participantId, request.x(), request.y(), request.note(), requireActor(httpRequest)));
    }

    /**
     * Applies damage to a participant (ACTIVE). The caller must hold the
     * {@link MembershipRole#GAME_MASTER} role. Temporary hit points are absorbed
     * before current hit points; current hit points are clamped at {@code 0}.
     */
    @PostMapping("/{id}/participants/{participantId}/damage")
    public ResponseEntity<EncounterDto> applyDamage(
            @PathVariable String id,
            @PathVariable String participantId,
            @Valid @RequestBody DamageRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.applyDamage(
                id, participantId, request.amount(), request.note(), requireActor(httpRequest)));
    }

    /**
     * Heals a participant (ACTIVE). The caller must hold the
     * {@link MembershipRole#GAME_MASTER} role. Current hit points are restored up
     * to the maximum; healing is not applied at {@code 0} hit points.
     */
    @PostMapping("/{id}/participants/{participantId}/healing")
    public ResponseEntity<EncounterDto> applyHealing(
            @PathVariable String id,
            @PathVariable String participantId,
            @Valid @RequestBody HealingRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.applyHealing(
                id, participantId, request.amount(), request.note(), requireActor(httpRequest)));
    }

    /**
     * Grants temporary hit points to a participant (ACTIVE). The caller must
     * hold the {@link MembershipRole#GAME_MASTER} role. Temporary hit points are
     * tracked separately and consumed before current hit points.
     */
    @PostMapping("/{id}/participants/{participantId}/temporary-hit-points")
    public ResponseEntity<EncounterDto> applyTemporaryHitPoints(
            @PathVariable String id,
            @PathVariable String participantId,
            @Valid @RequestBody TemporaryHitPointsRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.applyTemporaryHitPoints(
                id, participantId, request.amount(), request.note(), requireActor(httpRequest)));
    }

    /**
     * Applies (or updates) a common condition on a participant (ACTIVE).
     * The caller must hold the {@link MembershipRole#GAME_MASTER} role. Applying
     * a condition already held replaces its description and remaining rounds.
     */
    @PostMapping("/{id}/participants/{participantId}/conditions")
    public ResponseEntity<EncounterDto> addCondition(
            @PathVariable String id,
            @PathVariable String participantId,
            @Valid @RequestBody ConditionRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.addCondition(
                id, participantId, request.name(), request.description(),
                request.roundsRemaining(), requireActor(httpRequest)));
    }

    /**
     * Removes a condition from a participant (ACTIVE). The caller must hold the
     * {@link MembershipRole#GAME_MASTER} role.
     */
    @DeleteMapping("/{id}/participants/{participantId}/conditions/{conditionName}")
    public ResponseEntity<EncounterDto> removeCondition(
            @PathVariable String id,
            @PathVariable String participantId,
            @PathVariable String conditionName,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(encounterService.removeCondition(
                id, participantId, conditionName, requireActor(httpRequest)));
    }

    /**
     * Resolves a death saving throw for a participant that is at {@code 0} hit
     * points (ACTIVE). The caller must hold the {@link MembershipRole#GAME_MASTER}
     * role. The d20 is rolled server-side under the encounter's selected,
     * supported rules subset.
     */
    @PostMapping("/{id}/participants/{participantId}/death-save")
    public ResponseEntity<EncounterDto> resolveDeathSave(
            @PathVariable String id,
            @PathVariable String participantId,
            @Valid @RequestBody DeathSaveRequest request,
            HttpServletRequest httpRequest) {
        String mode = (request.rollMode() != null && !request.rollMode().isBlank())
                ? request.rollMode().toLowerCase()
                : "random";
        if ("seeded".equals(mode) && (request.seed() == null || request.seed().isBlank())) {
            throw new IllegalArgumentException("A seed is required for a seeded death save roll");
        }
        return ResponseEntity.ok(encounterService.resolveDeathSave(
                id, participantId, request.savingThrowModifier(), request.failures(),
                request.successes(), mode, request.seed(), request.note(),
                requireActor(httpRequest)));
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
                "Authentication required to manage an encounter");
    }

    /**
     * Maps the service's {@code IllegalArgumentException} for a missing encounter
     * to a {@code 404 Not Found} response in the consistent API error format.
     * This controller-level handler takes precedence over the global handler for
     * exceptions thrown within this controller.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildNotFound(ex.getMessage(), request));
    }

    /**
     * Maps an unsupported or missing rules profile to a {@code 400 Bad Request}
     * response. This is raised by the service when an encounter cannot proceed
     * because no supported {@link com.gamemasterx.server.encounter.model.RulesProfile}
     * has been selected. This controller-level handler takes precedence over the
     * global handler for exceptions thrown within this controller.
     */
    @ExceptionHandler(RulesProfileValidationException.class)
    @Order(4)
    public ResponseEntity<ErrorResponse> handleRulesProfile(RulesProfileValidationException ex,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    /**
     * Maps an illegal encounter lifecycle transition to a {@code 400 Bad
     * Request} response, describing the rejected transition. This
     * controller-level handler takes precedence over the global handler for
     * exceptions thrown within this controller.
     */
    @ExceptionHandler(EncounterStateTransitionException.class)
    @Order(3)
    public ResponseEntity<ErrorResponse> handleTransition(EncounterStateTransitionException ex,
                                                          HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    /**
     * Maps a movement-distance or reach validation failure (for example a move
     * that exceeds the participant's available movement) to a {@code 400 Bad
     * Request} response, carrying the clear diagnostic produced by the
     * backend-owned {@link
     * com.gamemasterx.server.gameplay.service.MovementRangeService}.
     */
    @ExceptionHandler(MovementRangeValidationException.class)
    @Order(5)
    public ResponseEntity<ErrorResponse> handleMovementRange(MovementRangeValidationException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }

    private static ErrorResponse buildNotFound(String message, HttpServletRequest request) {
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new ArrayList<>();
        return new ErrorResponse("NOT_FOUND",
                message == null ? "The requested encounter was not found" : message,
                getCorrelationId(request), fieldErrors);
    }

    private static ErrorResponse buildBadRequest(String message, HttpServletRequest request) {
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new ArrayList<>();
        return new ErrorResponse("BAD_REQUEST",
                message == null ? "The encounter state transition was not permitted" : message,
                getCorrelationId(request), fieldErrors);
    }
}

package com.gamemasterx.server.gameplay.controller;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.EncounterDto;
import com.gamemasterx.server.encounter.model.EncounterStatus;
import com.gamemasterx.server.encounter.service.EncounterService;
import com.gamemasterx.server.encounter.model.RulesProfile;
import com.gamemasterx.server.encounter.service.RulesProfileService;
import com.gamemasterx.server.gameplay.model.CheckType;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.filter.AuthFilter;
import com.gamemasterx.server.gameplay.model.Turn;
import com.gamemasterx.server.gameplay.model.Mutation;
import com.gamemasterx.server.gameplay.model.MutationDecision;
import com.gamemasterx.server.gameplay.service.GameplayAuditService;
import com.gamemasterx.server.gameplay.service.GameplayRulesService;
import com.gamemasterx.server.gameplay.service.GameplayRulesService.CheckInput;
import com.gamemasterx.server.gameplay.service.GameplayRulesService.ResolvedCheck;
import com.gamemasterx.server.gameplay.service.GameplayRulesService.AttackInput;
import com.gamemasterx.server.gameplay.service.GameplayRulesService.ResolvedAttack;
import com.gamemasterx.server.gameplay.service.MovementRangeService;
import com.gamemasterx.server.gameplay.service.MovementRangeService.ResolvedRange;
import com.gamemasterx.server.gameplay.MovementRangeValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller exposing the backend-owned ability checks, skill checks and
 * saving throws.
 *
 * <p>All endpoints run on the backend API port (5172). Each endpoint resolves a
 * check through {@link GameplayRulesService} &ndash; which rolls a {@code 1d20}
 * via the shared dice subsystem and applies the ability and proficiency
 * modifiers deterministically &ndash; and then persists the resolved check as an
 * <em>auditable record</em> via {@link GameplayAuditService}: a new {@link Turn}
 * document is appended, embedding the immutable {@link
 * com.gamemasterx.server.gameplay.model.Action} and its {@link
 * com.gamemasterx.server.gameplay.model.DiceResult}, together with the encounter
 * aggregate's revision before and after the check. Prior turn documents are
 * never updated, so the recorded outcome can never be retro-edited.</p>
 *
 * <p>Every endpoint requires an authenticated actor (see
 * {@link #requireActor(HttpServletRequest)}), matching the role-gated
 * conventions of the rest of the API.</p>
 */
@RestController
@RequestMapping("/api/gameplay")
public class GameplayController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    /** The default melee reach, in grid squares, when the request omits one. */
    private static final int DEFAULT_MELEE_REACH = 1;

    private final GameplayRulesService gameplayRulesService;
    private final GameplayAuditService gameplayAuditService;
    private final EncounterService encounterService;
    private final RulesProfileService rulesProfileService;
    private final MovementRangeService movementRangeService;

    /**
     * Creates the gameplay controller.
     *
     * @param gameplayRulesService  the check resolver
     * @param gameplayAuditService  the append-only audit/turn store
     * @param encounterService      the encounter loader used to read the revision
     * @param rulesProfileService   the single authority for the selected rules
     *                              profile and its critical-hit behaviour
     * @param movementRangeService  the single authority for movement-distance
     *                              and reach validation
     */
    public GameplayController(GameplayRulesService gameplayRulesService,
                              GameplayAuditService gameplayAuditService,
                              EncounterService encounterService,
                              RulesProfileService rulesProfileService,
                              MovementRangeService movementRangeService) {
        this.gameplayRulesService = gameplayRulesService;
        this.gameplayAuditService = gameplayAuditService;
        this.encounterService = encounterService;
        this.rulesProfileService = rulesProfileService;
        this.movementRangeService = movementRangeService;
    }

    /**
     * Resolves and records an ability check.
     *
     * @param request the ability-check input
     * @param httpRequest the incoming request, used for the authenticated actor
     * @return the 200 response carrying the {@link ResolvedCheck}
     */
    @PostMapping("/ability-check")
    public ResponseEntity<ResolvedCheck> abilityCheck(@Valid @RequestBody CheckRequest request,
                                                     HttpServletRequest httpRequest) {
        // Load and gate the action before anything is resolved: when an
        // encounter is supplied, availability and turn ownership are enforced
        // server-side and the encounter is returned for the audit path.
        EncounterDto dto = loadEncounterForAction(request.encounterId(), requireActor(httpRequest));
        CheckInput input = toInput(request, CheckType.ABILITY_CHECK);
        ResolvedCheck resolved = gameplayRulesService.resolveAbilityCheck(input);
        return ResponseEntity.ok(persistCheck(resolved, request, httpRequest, dto));
    }

    /**
     * Resolves and records a skill check.
     *
     * @param request the skill-check input
     * @param httpRequest the incoming request, used for the authenticated actor
     * @return the 200 response carrying the {@link ResolvedCheck}
     */
    @PostMapping("/skill-check")
    public ResponseEntity<ResolvedCheck> skillCheck(@Valid @RequestBody CheckRequest request,
                                                    HttpServletRequest httpRequest) {
        EncounterDto dto = loadEncounterForAction(request.encounterId(), requireActor(httpRequest));
        CheckInput input = toInput(request, CheckType.SKILL_CHECK);
        ResolvedCheck resolved = gameplayRulesService.resolveSkillCheck(input);
        return ResponseEntity.ok(persistCheck(resolved, request, httpRequest, dto));
    }

    /**
     * Resolves and records a saving throw.
     *
     * @param request the saving-throw input
     * @param httpRequest the incoming request, used for the authenticated actor
     * @return the 200 response carrying the {@link ResolvedCheck}
     */
    @PostMapping("/saving-throw")
    public ResponseEntity<ResolvedCheck> savingThrow(@Valid @RequestBody CheckRequest request,
                                                     HttpServletRequest httpRequest) {
        EncounterDto dto = loadEncounterForAction(request.encounterId(), requireActor(httpRequest));
        CheckInput input = toInput(request, CheckType.SAVING_THROW);
        ResolvedCheck resolved = gameplayRulesService.resolveSavingThrow(input);
        return ResponseEntity.ok(persistCheck(resolved, request, httpRequest, dto));
    }

    /**
     * Resolves and records an attack roll: a {@code 1d20} attack compared
     * against the target's armor class, with the critical-hit verdict taken from
     * the owning encounter's rules profile.
     *
     * <p>The attack is only valid while the owning encounter is {@code ACTIVE}
     * and it is the requesting participant's turn. The encounter's current turn
     * participant is the acting participant; an attack is resolved and recorded
     * only when {@code actorId} matches that participant. These ownership and
     * action-availability checks run server-side before the attack is resolved.
     *
     * @param request the attack input
     * @param httpRequest the incoming request, used for the authenticated actor
     * @return the 200 response carrying the {@link ResolvedAttack}
     */
    @PostMapping("/attack")
    public ResponseEntity<ResolvedAttack> attack(@Valid @RequestBody AttackRequest request,
                                                 HttpServletRequest httpRequest) {
        // Authenticate the actor before anything else.
        String actor = requireActor(httpRequest);

        // Load the owning encounter first. The critical-hit verdict is governed
        // exclusively by the encounter's stored, validated, supported rules
        // profile; it is never taken from a value that could be supplied or
        // overridden on the attack request wire. Loading also enforces turn
        // ownership and action availability (see
        // #loadEncounterForAction) before any attack is resolved.
        EncounterDto dto = loadEncounterForAction(request.encounterId(), actor);

        // Enforce turn-ownership and action-availability, and reject any
        // request-supplied rules profile that disagrees with the encounter's
        // stored profile.
        assertAttackPermitted(dto, request);

        // Range validation: when both the attacker and the target carry a
        // stored grid position, the target must be within the attacker's reach.
        // An unreachable target is rejected with a clear diagnostic; a reachable
        // target yields a ResolvedRange that is recorded as an accepted mutation.
        ResolvedRange range = resolveAttackRange(dto, request);

        // The governing profile is the encounter's selected profile. Critical
        // hits are only applied when that profile is supported; this is the
        // server-side guard at the point at which critical hits are resolved.
        RulesProfile governing = (dto.getRulesProfile() != null)
                ? dto.getRulesProfile()
                : RulesProfile.SRD_5_2024;
        rulesProfileService.assertCriticalHitPermitted(governing);

        AttackInput input = toInput(request, governing);
        ResolvedAttack resolved = gameplayRulesService.resolveAttack(input);

        return ResponseEntity.ok(persistAttack(resolved, dto, request, httpRequest, range));
    }

    /**
     * Validates the attack range when both the attacker and the target carry a
     * stored grid position. The attacker's reach defaults to a melee reach of
     * {@value #DEFAULT_MELEE_REACH} grid squares when the request omits it.
     *
     * <p>When either party has no stored position, the distance cannot be
     * determined and range is left unvalidated (returns {@code null}); the
     * attack then proceeds as it did before grid positions were introduced. A
     * target that is present but out of range is rejected here with a clear
     * diagnostic.
     *
     * @param dto    the owning encounter, already loaded for the actor
     * @param request the originating attack request
     * @return the resolved range when both positions are present and in range,
     *         or {@code null} when range cannot be determined
     * @throws IllegalArgumentException when the target is present but out of reach
     */
    private ResolvedRange resolveAttackRange(EncounterDto dto, AttackRequest request) {
        int reach = (request.reach() != null) ? request.reach() : DEFAULT_MELEE_REACH;
        EncounterDto.PositionDto attackerPosition = findPosition(dto, request.actorId());
        EncounterDto.PositionDto targetPosition = findPosition(dto, request.targetId());
        if (attackerPosition == null || targetPosition == null) {
            return null;
        }
        return movementRangeService.resolveReach(
                toPosition(attackerPosition), toPosition(targetPosition), reach);
    }

    private static Encounter.Position toPosition(EncounterDto.PositionDto position) {
        return new Encounter.Position(position.getX(), position.getY());
    }

    private static EncounterDto.PositionDto findPosition(EncounterDto dto, String participantId) {
        if (participantId == null) {
            return null;
        }
        for (EncounterDto.ParticipantDto participant : dto.getParticipants()) {
            if (participantId.equals(participant.getId())) {
                return participant.getPosition();
            }
        }
        return null;
    }

    /**
     * Loads the owning encounter for a gameplay action and enforces, in backend
     * code, that the action is legal: it is available (the encounter is
     * {@code ACTIVE}) and it is the requesting actor's turn. This is the single
     * server-side gate that prevents out-of-turn or unavailable actions from
     * being resolved or recorded, regardless of what the UI shows.
     *
     * <p>When no encounter is supplied ({@code encounterId} omitted or blank) the
     * caller is explicitly recording a check outside any encounter; in that case
     * no encounter-scoped turn ownership or availability can be checked and
     * {@code null} is returned, preserving the "record without an encounter"
     * behaviour. When an encounter is supplied it is loaded for the actor and
     * passed through {@link #assertActionAvailableToActor} before anything is
     * resolved; a violation is rejected with a clear {@link IllegalArgumentException}
     * that the controller maps to a {@code 400 BAD_REQUEST}.</p>
     *
     * @param encounterId the owning encounter identifier, or {@code null}/blank
     *                    to record an encounter-less check
     * @param actor       the authenticated actor submitting the action
     * @return the owning encounter when one was supplied and permitted, or
     *         {@code null} when the check is encounter-less
     */
    private EncounterDto loadEncounterForAction(String encounterId, String actor) {
        if (encounterId == null || encounterId.isBlank()) {
            return null;
        }
        EncounterDto dto = encounterService.findById(encounterId, actor);
        assertActionAvailableToActor(dto, actor);
        return dto;
    }

    /**
     * Persists the resolved check as an auditable turn. The encounter's current
     * revision is read as the "revision before"; because a check does not mutate
     * the encounter aggregate, the "revision after" equals the "revision before".
     * The recorded {@link Turn} carries the encounter's live round and turn
     * index, so turn ownership transitions are reflected in the immutable turn
     * history.
     *
     * @param resolved the resolved check
     * @param request  the originating request (carries the encounter context)
     * @param httpRequest the incoming request, used for the authenticated actor
     * @param dto      the owning encounter, or {@code null} when encounter-less
     * @return the 200 response carrying the resolved check
     */
    private ResolvedCheck persistCheck(ResolvedCheck resolved, CheckRequest request,
                                       HttpServletRequest httpRequest, EncounterDto dto) {
        String actor = requireActor(httpRequest);

        String resolvedCampaignId;
        String resolvedEncounterId;
        int round;
        int turnIndex;
        int revisionBefore;
        if (dto != null) {
            // When an encounter is supplied we read its current revision so the
            // audited record is bounded by the encounter's optimistic-concurrency
            // generation. A check does not mutate the encounter, so the revision
            // before and after are equal. The live round and turn index are
            // recorded so the audit trail reflects whose turn the action belongs
            // to.
            resolvedCampaignId = (request.campaignId() != null && !request.campaignId().isBlank())
                    ? request.campaignId() : dto.getCampaignId();
            resolvedEncounterId = dto.getId();
            round = dto.getRound();
            turnIndex = dto.getTurn();
            revisionBefore = dto.getRevision();
        } else {
            resolvedCampaignId = request.campaignId();
            resolvedEncounterId = request.encounterId();
            round = 1;
            turnIndex = 0;
            revisionBefore = 0;
        }

        Turn turn = new Turn(
                null,
                resolvedCampaignId,
                resolvedEncounterId,
                round,
                turnIndex,
                request.actorId(),
                List.of(resolved.action()),
                List.of(resolved.dieResult()),
                revisionBefore,
                revisionBefore,
                Instant.now(),
                Instant.now());
        gameplayAuditService.appendTurn(turn);
        return resolved;
    }

    /**
     * Enforces the turn-ownership and action-availability invariants for an
     * attack, and ensures the critical-hit rules profile is the encounter's
     * stored profile and cannot be overridden on the wire.
     *
     * <p>An attack is valid only while the owning encounter is {@code ACTIVE}
     * (actions are available) and it is the requesting participant's turn. In
     * addition, the request-supplied {@code rulesProfile}, when present, must
     * match the encounter's stored profile so that critical-hit behaviour can
     * never be decided by a client-supplied value.</p>
     *
     * @param dto    the owning encounter, already loaded for the actor
     * @param request the originating attack request
     * @throws IllegalArgumentException when the encounter is not active, it is
     *                                 not the actor's turn, or the supplied
     *                                 rules profile disagrees with the stored
     *                                 profile
     */
    /**
     * Enforces turn-ownership and action-availability for any gameplay action
     * (an attack or a check) against an already-loaded encounter. This is the
     * single backend-authoritative gate that prevents out-of-turn or
     * unavailable actions from being resolved or recorded: it does not depend on
     * what the UI happens to show.
     *
     * <p>An action is available only while the owning encounter is
     * {@code ACTIVE} (a draft, paused or completed encounter has no actions
     * available), and it is legal only when the requesting actor is the
     * participant whose turn it currently is. The current turn participant is
     * identified from the encounter's stored initiative order and turn index
     * (the source of truth owned by the backend, never a client-supplied value).
     * The acting participant is recorded on the resulting {@link Turn}, so turn
     * ownership transitions are captured in the immutable turn history.</p>
     *
     * @param dto   the owning encounter, already loaded for the actor
     * @param actor the authenticated actor submitting the action
     * @throws IllegalArgumentException when the encounter is not active, no turn
     *                                 is in progress, or it is not the actor's turn
     */
    private void assertActionAvailableToActor(EncounterDto dto, String actor) {
        if (dto.getStatus() != EncounterStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "An action can only be resolved while the encounter is active"
                            + " (current state: " + dto.getStatus() + ")");
        }

        List<String> order = dto.getInitiativeOrder();
        if (order == null) {
            throw new IllegalArgumentException(
                    "No turn is in progress in this encounter; an action requires an active turn");
        }
        int turnIndex = dto.getTurn();
        String currentTurnParticipant = (turnIndex >= 0 && turnIndex < order.size())
                ? order.get(turnIndex) : null;
        if (currentTurnParticipant == null) {
            throw new IllegalArgumentException(
                    "No turn is in progress in this encounter; an action requires an active turn");
        }
        if (!currentTurnParticipant.equals(actor)) {
            throw new IllegalArgumentException(
                    "It is not the actor's turn to act; "
                            + "it is " + currentTurnParticipant + "'s turn");
        }
    }

    private void assertAttackPermitted(EncounterDto dto, AttackRequest request) {
        // Turn ownership and action availability are enforced by the shared
        // backend gate used by every gameplay action.
        assertActionAvailableToActor(dto, request.actorId());

        // Critical-hit governance: the critical-hit verdict is governed by the
        // encounter's stored rules profile. A request-supplied rules profile is
        // only accepted when it matches the stored profile, so a caller cannot
        // override critical-hit behaviour on the wire.
        RulesProfile stored = dto.getRulesProfile();
        String supplied = request.rulesProfile();
        if (supplied != null && !supplied.isBlank()) {
            RulesProfile suppliedProfile = RulesProfile.fromWire(supplied);
            if (!suppliedProfile.equals(stored)) {
                throw new IllegalArgumentException(
                        "The supplied rules profile '" + supplied
                                + "' does not match the encounter's selected profile '"
                                + (stored != null ? stored.getRulesSubset() : "none") + "'");
            }
        }
    }

    /**
     * Persists the resolved attack as an auditable turn. When the attack
     * resolves to a critical hit, the critical hit is also recorded as an
     * <em>accepted mutation</em> in the immutable audit log, capturing the
     * before/after state of the critical verdict and the backend-owned damage
     * multiplier that the governing profile prescribes. This is what makes a
     * critical hit auditable and provably governed by backend rules.
     *
     * @param resolved  the resolved attack
     * @param dto       the owning encounter (already validated)
     * @param request   the originating request (carries the encounter context)
     * @param httpRequest the incoming request, used for the authenticated actor
     * @return the resolved attack, unchanged
     */
    private ResolvedAttack persistAttack(ResolvedAttack resolved, EncounterDto dto,
                                         AttackRequest request, HttpServletRequest httpRequest,
                                         ResolvedRange range) {
        String actor = requireActor(httpRequest);
        int revisionBefore = dto.getRevision();
        // An attack does not mutate the encounter aggregate, so the revision
        // before and after are equal.
        int revisionAfter = revisionBefore;

        Turn turn = new Turn(
                null,
                dto.getCampaignId(),
                dto.getId(),
                dto.getRound(),
                dto.getTurn(),
                request.actorId(),
                List.of(resolved.action()),
                List.of(resolved.dieResult()),
                revisionBefore,
                revisionAfter,
                Instant.now(),
                Instant.now());

        // Record accepted mutations in the audit log. When the attack was
        // resolved against stored grid positions, the in-range verdict is
        // recorded as a single accepted mutation; a critical hit is additionally
        // recorded. A non-critical attack with no positional data appends only
        // the turn.
        List<Mutation> mutations = new ArrayList<>();
        List<MutationDecision> resolutions = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        if (range != null) {
            mutations.add(rangeMutation(resolved, range));
            resolutions.add(MutationDecision.ACCEPTED);
            reasons.add(null);
        }
        if (resolved.critical()) {
            mutations.add(criticalMutation(resolved));
            resolutions.add(MutationDecision.ACCEPTED);
            reasons.add(null);
        }

        gameplayAuditService.appendTurnAndAudit(
                turn,
                mutations,
                resolutions,
                reasons,
                revisionBefore,
                revisionAfter,
                actor,
                getCorrelationId(httpRequest));
        return resolved;
    }

    /**
     * Builds the accepted mutation that records a resolved critical hit. The
     * mutation captures the before/after state of the critical verdict and the
     * backend-owned critical-damage multiplier that the governing profile
     * prescribes, so the critical hit is fully auditable and the damage rule is
     * provably owned by the backend rather than by any language-model output.
     *
     * @param resolved the resolved attack carrying the critical verdict
     * @return the accepted critical-hit mutation
     */
    private Mutation criticalMutation(ResolvedAttack resolved) {
        String subjectId = (resolved.actorId() != null) ? resolved.actorId() : "unknown";
        String description = "Critical hit applied under rules profile "
                + resolved.profile().getRulesSubset()
                + ": critical-hit threshold " + resolved.profile().getCriticalHitThreshold()
                + ", damage multiplier " + resolved.criticalDamageMultiplier();

        Map<String, Object> before = new HashMap<>();
        before.put("roll", resolved.roll());
        before.put("toHitTotal", resolved.toHitTotal());
        before.put("armorClass", resolved.armorClass());
        before.put("critical", false);
        before.put("outcome", resolved.action().outcome().name());

        Map<String, Object> after = new HashMap<>();
        after.put("roll", resolved.roll());
        after.put("toHitTotal", resolved.toHitTotal());
        after.put("armorClass", resolved.armorClass());
        after.put("critical", true);
        after.put("outcome", resolved.action().outcome().name());
        after.put("criticalDamageMultiplier", resolved.criticalDamageMultiplier());
        after.put("rulesProfile", resolved.profile().getRulesSubset());
        after.put("criticalHitThreshold", resolved.profile().getCriticalHitThreshold());

        return new Mutation(
                UUID.randomUUID().toString(),
                "criticalHit",
                subjectId,
                description,
                before,
                after,
                Instant.now());
    }

    /**
     * Builds the accepted mutation that records a validated attack range. The
     * mutation captures the before/after state of the reach check (attacker and
     * target squares, the Chebyshev distance and the reach), so the range
     * verdict is auditable and provably owned by the backend rather than by any
     * language-model output.
     *
     * @param resolved the resolved attack
     * @param range    the validated range
     * @return the accepted range mutation
     */
    private Mutation rangeMutation(ResolvedAttack resolved, ResolvedRange range) {
        String description = "Attack in range under reach " + range.reach()
                + " sq: distance " + range.distance() + " sq"
                + " (" + range.from().getX() + "," + range.from().getY()
                + " -> " + range.to().getX() + "," + range.to().getY() + ")";

        Map<String, Object> before = new HashMap<>();
        before.put("attacker", range.from().getX() + "," + range.from().getY());
        before.put("target", range.to().getX() + "," + range.to().getY());
        before.put("distance", range.distance());
        before.put("reach", range.reach());
        Map<String, Object> after = new HashMap<>();
        after.putAll(before);
        after.put("inRange", true);

        return new Mutation(
                UUID.randomUUID().toString(),
                "range",
                resolved.actorId(),
                description,
                before,
                after,
                Instant.now());
    }

    /**
     * Maps an attack request onto a fully-specified {@link AttackInput}, using
     * the encounter's governing {@link RulesProfile} for critical-hit
     * determination.
     *
     * <p>The roll mode defaults to {@code random} when the request omits it. A
     * seeded roll (roll mode {@code seeded}) must also carry a non-blank seed;
     * the dice subsystem rejects a missing seed.</p>
     *
     * @param request the attack request
     * @param governing the encounter's selected, supported rules profile that
     *                  governs critical hits; never {@code null}
     * @return the resolved attack input
     */
    private AttackInput toInput(AttackRequest request, RulesProfile governing) {
        return new AttackInput(
                request.actorId(),
                request.targetId(),
                request.targetName(),
                request.abilityName(),
                request.abilityScore(),
                request.proficient(),
                request.proficiencyBonus(),
                request.armorClass(),
                governing,
                (request.rollMode() != null && !request.rollMode().isBlank())
                        ? request.rollMode()
                        : "random",
                request.seed(),
                request.label(),
                request.note());
    }

    /**
     * Maps a request and its check kind onto a fully-specified {@link CheckInput}.
     *
     * <p>The roll mode defaults to {@code random} when the request omits it. A
     * seeded roll (roll mode {@code seeded}) must also carry a non-blank seed;
     * the dice subsystem rejects a missing seed.</p>
     *
     * @param request the request
     * @param kind    the check kind this endpoint resolves
     * @return the resolved check input
     */
    private CheckInput toInput(CheckRequest request, CheckType kind) {
        return new CheckInput(
                kind,
                request.actorId(),
                request.targetId(),
                request.targetName(),
                request.abilityName(),
                request.abilityScore(),
                request.proficient(),
                request.proficiencyBonus(),
                request.dc(),
                (request.rollMode() != null && !request.rollMode().isBlank())
                        ? request.rollMode()
                        : "random",
                request.seed(),
                request.label(),
                request.note());
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
                "Authentication required to resolve a check");
    }

    /**
     * Maps an unrecognized roll mode to a {@code 400 BAD_REQUEST} response. This
     * is raised by the dice subsystem when the requested mode is not
     * {@code random} or {@code seeded}.
     */
    @ExceptionHandler(com.gamemasterx.server.dice.DiceExpressionException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleDiceExpressionException(
            com.gamemasterx.server.dice.DiceExpressionException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    /**
     * Maps a validation failure (for example an ability score outside 1-30) to a
     * {@code 400 BAD_REQUEST} response.
     */
    /**
     * Maps a movement-distance or reach validation failure (for example an
     * attack against a target that is out of reach) to a {@code 400 Bad
     * Request} response, carrying the clear diagnostic produced by the
     * backend-owned {@link
     * com.gamemasterx.server.gameplay.service.MovementRangeService}.
     */
    @ExceptionHandler(MovementRangeValidationException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleMovementRange(MovementRangeValidationException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @Order(3)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    private static ErrorResponse buildBadRequest(String message, HttpServletRequest request) {
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new java.util.ArrayList<>();
        return new ErrorResponse("BAD_REQUEST",
                message == null ? "The check request was invalid" : message,
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

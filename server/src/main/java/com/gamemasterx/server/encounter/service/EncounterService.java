package com.gamemasterx.server.encounter.service;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.encounter.EncounterStateTransitionException;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.EncounterCreateRequest;
import com.gamemasterx.server.encounter.model.EncounterDto;
import com.gamemasterx.server.encounter.model.EncounterStatus;
import com.gamemasterx.server.encounter.repository.EncounterRepository;
import com.gamemasterx.server.encounter.model.RulesProfile;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.service.InitiativeService;
import com.gamemasterx.server.encounter.service.InitiativeService.InitiativeResult;
import com.gamemasterx.server.encounter.service.RulesProfileService;
import com.gamemasterx.server.dice.DiceExpression;
import com.gamemasterx.server.dice.DiceRoller;
import com.gamemasterx.server.dice.RollMode;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.gameplay.model.Action;
import com.gamemasterx.server.gameplay.model.ActionType;
import com.gamemasterx.server.gameplay.model.ActionOutcome;
import com.gamemasterx.server.gameplay.model.DiceResult;
import com.gamemasterx.server.gameplay.model.Mutation;
import com.gamemasterx.server.gameplay.model.MutationDecision;
import com.gamemasterx.server.gameplay.model.Modifier;
import com.gamemasterx.server.gameplay.model.Turn;
import com.gamemasterx.server.gameplay.service.DamageService;
import com.gamemasterx.server.gameplay.service.GameplayAuditService;
import com.gamemasterx.server.gameplay.service.MovementRangeService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

/**
 * Application service for the Encounter aggregate.
 *
 * <p>This service owns all mutation of {@link Encounter} documents and is the
 * single place where the encounter lifecycle state machine is exercised. Every
 * lifecycle transition is delegated to {@link Encounter#transitionTo}, so the
 * set of legal transitions is enforced in exactly one place. The service also
 * enforces that the caller holds the required {@link MembershipRole} in the
 * campaign the encounter belongs to.</p>
 *
 * <p>In addition to the lifecycle, this service owns the application of the
 * backend-owned hit-point, temporary-hit-point, death-save and condition rules
 * (see {@link DamageService}) to encounter participants. Each of those
 * operations resolves the change through {@link DamageService}, applies the
 * deterministic result to the stored participant, records the before/after state
 * as a {@link Mutation} and appends the change to the immutable turn and audit
 * history through {@link GameplayAuditService}.</p>
 *
 * <p>Encounters are only ever started once they are in the {@link
 * EncounterStatus#DRAFT} state and may be paused, resumed and completed through
 * the dedicated lifecycle methods, each of which maps onto a single permitted
 * transition.</p>
 */
@Service
public class EncounterService {

    /** Current schema version for the Encounter document shape. */
    private static final int SCHEMA_VERSION = 1;

    private final EncounterRepository encounterRepository;
    private final MembershipService membershipService;
    private final RulesProfileService rulesProfileService;
    private final DamageService damageService;
    private final GameplayAuditService gameplayAuditService;
    private final DiceRoller diceRoller;
    private final InitiativeService initiativeService;
    private final MovementRangeService movementRangeService;

    public EncounterService(EncounterRepository encounterRepository,
                            MembershipService membershipService,
                            RulesProfileService rulesProfileService,
                            DamageService damageService,
                            GameplayAuditService gameplayAuditService,
                            DiceRoller diceRoller,
                            InitiativeService initiativeService,
                            MovementRangeService movementRangeService) {
        this.encounterRepository = encounterRepository;
        this.membershipService = membershipService;
        this.rulesProfileService = rulesProfileService;
        this.damageService = damageService;
        this.gameplayAuditService = gameplayAuditService;
        this.diceRoller = diceRoller;
        this.initiativeService = initiativeService;
        this.movementRangeService = movementRangeService;
    }

    /**
     * Creates a new Encounter aggregate in the {@link EncounterStatus#DRAFT}
     * state. The caller must hold at least the lowest membership role
     * ({@link MembershipRole#OBSERVER}) in the referenced campaign.
     *
     * @param request the minimum identifying information for the encounter
     * @param actor   the authenticated caller creating the encounter
     * @return the persisted encounter, projected into the API representation
     */
    public EncounterDto createEncounter(EncounterCreateRequest request, String actor) {
        String campaignId = requireNonBlank(request.getCampaignId(), "campaignId");
        membershipService.assertAuthorized(campaignId, actor, MembershipRole.OBSERVER);
        RulesProfile rulesProfile = rulesProfileService.resolve(request.getRulesProfile());

        Encounter encounter = new Encounter();
        encounter.setId(UUID.randomUUID().toString());
        encounter.setSchemaVersion(SCHEMA_VERSION);
        encounter.setRevision(1);
        Instant now = Instant.now();
        encounter.setCreatedAt(now);
        encounter.setUpdatedAt(now);
        encounter.setCampaignId(campaignId);
        encounter.setName(request.getName());
        encounter.setStatus(EncounterStatus.DRAFT);
        encounter.setRulesProfile(rulesProfile);
        encounterRepository.save(encounter);
        return toDto(encounter);
    }

    /**
     * Reads a single encounter the given actor is permitted to access. Access is
     * granted when the actor holds at least the lowest {@link
     * MembershipRole#OBSERVER} role in the campaign the encounter belongs to.
     *
     * @param encounterId the stable encounter identifier
     * @param actor       the authenticated caller
     * @return the requested encounter
     * @throws IllegalArgumentException when no such encounter exists
     * @throws AuthorizationException   when the actor lacks access
     */
    public EncounterDto findById(String encounterId, String actor) {
        Encounter encounter = requireEncounter(encounterId);
        ensureAccessible(actor, encounter);
        return toDto(encounter);
    }

    /**
     * Lists every encounter belonging to the given campaign that the actor may
     * access.
     *
     * @param campaignId the stable campaign identifier
     * @param actor      the authenticated caller
     * @return the accessible encounters for the campaign
     */
    public List<EncounterDto> listForCampaign(String campaignId, String actor) {
        String resolved = requireNonBlank(campaignId, "campaignId");
        membershipService.assertAuthorized(resolved, actor, MembershipRole.OBSERVER);
        return encounterRepository.findByCampaignId(resolved).stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Adds or replaces a participant in a draft encounter. The caller must hold
     * the {@link MembershipRole#GAME_MASTER} role. The initiative order is
     * recomputed automatically from the participant's initiative score.
     *
     * @param encounterId the stable encounter identifier
     * @param participant the participant to add or replace
     * @param actor       the authenticated game master
     * @return the updated encounter
     */
    public EncounterDto addParticipant(String encounterId, Encounter.Participant participant, String actor) {
        Encounter encounter = requireEncounterAtState(encounterId, EncounterStatus.DRAFT);
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.GAME_MASTER);
        encounter.upsertParticipant(participant);
        bumpUpdated(encounter);
        return toDto(encounterRepository.save(encounter));
    }

    /**
     * Removes a participant from a draft encounter. The caller must hold the
     * {@link MembershipRole#GAME_MASTER} role.
     *
     * @param encounterId   the stable encounter identifier
     * @param participantId the id of the participant to remove
     * @param actor         the authenticated game master
     * @return the updated encounter
     */
    public EncounterDto removeParticipant(String encounterId, String participantId, String actor) {
        Encounter encounter = requireEncounterAtState(encounterId, EncounterStatus.DRAFT);
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.GAME_MASTER);
        encounter.removeParticipant(participantId);
        bumpUpdated(encounter);
        return toDto(encounterRepository.save(encounter));
    }

    /**
     * Starts a draft encounter, moving it from {@link EncounterStatus#DRAFT} to
     * {@link EncounterStatus#ACTIVE}. This is the {@code start} transition.
     *
     * @param encounterId the stable encounter identifier
     * @param actor       the authenticated game master
     * @param rollMode    the initiative roll mode: {@code "random"} (default) or {@code "seeded"}
     * @param seed        the seed for a seeded initiative roll; required when the
     *                    mode is {@code "seeded"}, ignored otherwise
     * @return the updated encounter
     * @throws EncounterStateTransitionException when the encounter is not DRAFT
     * @throws IllegalArgumentException           when a seeded roll is requested without a seed
     */
    public EncounterDto startEncounter(String encounterId, String actor, String rollMode, String seed) {
        Encounter encounter = requireEncounterAtState(encounterId, EncounterStatus.DRAFT);
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.GAME_MASTER);
        // The rules profile is validated server-side before the encounter may
        // proceed: a missing profile, or a profile whose subset is no longer
        // supported, is rejected with a RulesProfileValidationException.
        rulesProfileService.validateEncounterCanProceed(encounter);
        // The combat order is established from the dice subsystem before the
        // encounter begins. Any participant that does not already carry an
        // initiative score is rolled now, so the initiative order -- and therefore
        // the round/turn tracking established below -- is owned by backend rules
        // rather than by an untrusted caller-supplied value. The roll mode and
        // (for seeded rolls) the seed are supplied by the caller; the default is
        // unpredictable random, and a seeded roll reproduces the same order for a
        // given seed.
        RollMode startMode = RollMode.fromWire((rollMode != null && !rollMode.isBlank()) ? rollMode : "random");
        if (startMode == RollMode.SEEDED && (seed == null || seed.isBlank())) {
            throw new IllegalArgumentException("A seed is required for a seeded initiative roll");
        }
        int revisionBefore = encounter.getRevision();
        List<InitiativeResult> rolled =
                initiativeService.generateForMissing(encounter.getParticipants(), startMode, seed);
        encounter.recomputeInitiativeOrder();
        if (!rolled.isEmpty()) {
            recordInitiativeTurn(
                    encounter, actor, revisionBefore, rolled,
                    "Initiative generated for " + rolled.size() + " participant(s)" + initNoteSuffix(startMode, seed));
        }
        // The encounter is positioned at the start of the freshly computed
        // initiative order: round is already 1 and transitionTo sets the acting
        // turn index to 0.
        encounter.transitionTo(EncounterStatus.ACTIVE);
        bumpUpdated(encounter);
        return toDto(encounterRepository.save(encounter));
    }

    /**
     * Generates combat initiative for every participant in a draft encounter by
     * rolling a {@code 1d20} for each through the shared dice subsystem, writes
     * the resolved initiative scores back onto the participants and appends an
     * auditable turn capturing the before/after initiative state and the revision
     * before and after the roll. The initiative order is recomputed from the
     * freshly rolled scores. The encounter must remain in the {@link
     * EncounterStatus#DRAFT} state so the initiative order can still be re-rolled
     * before the encounter is started.
     *
     * @param encounterId the stable encounter identifier
     * @param rollMode    the roll mode: {@code "random"} (default) or {@code "seeded"}
     * @param seed        the seed for a seeded roll; required when {@code rollMode}
     *                    is {@code "seeded"}, ignored otherwise
     * @param actor       the authenticated game master
     * @return the updated encounter
     * @throws IllegalStateException when the encounter is not DRAFT or has no
     *                               participants
     * @throws IllegalArgumentException when a seeded roll is requested without a
     *                                  seed
     */
    public InitiativeService.Rollout generateInitiative(String encounterId, String rollMode, String seed, String actor) {
        Encounter encounter = requireEncounterAtState(encounterId, EncounterStatus.DRAFT);
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.GAME_MASTER);
        if (encounter.getParticipants().isEmpty()) {
            throw new IllegalArgumentException("Cannot generate initiative for an encounter with no participants");
        }
        RollMode mode = RollMode.fromWire((rollMode != null && !rollMode.isBlank()) ? rollMode : "random");
        if (mode == RollMode.SEEDED && (seed == null || seed.isBlank())) {
            throw new IllegalArgumentException("A seed is required for a seeded initiative roll");
        }
        int revisionBefore = encounter.getRevision();
        List<InitiativeResult> results =
                initiativeService.generate(encounter.getParticipants(), mode, seed);
        encounter.recomputeInitiativeOrder();
        String note = "Initiative generated for " + results.size() + " participant(s)"
                + initNoteSuffix(mode, seed);
        return recordInitiativeTurn(encounter, actor, revisionBefore, results, note);
    }

    /**
     * Pauses an active encounter, moving it from {@link EncounterStatus#ACTIVE}
     * to {@link EncounterStatus#PAUSED}.
     *
     * @param encounterId the stable encounter identifier
     * @param actor       the authenticated game master
     * @return the updated encounter
     * @throws EncounterStateTransitionException when the encounter is not ACTIVE
     */
    public EncounterDto pauseEncounter(String encounterId, String actor) {
        Encounter encounter = requireEncounterAtState(encounterId, EncounterStatus.ACTIVE);
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.GAME_MASTER);
        encounter.transitionTo(EncounterStatus.PAUSED);
        bumpUpdated(encounter);
        return toDto(encounterRepository.save(encounter));
    }

    /**
     * Resumes a paused encounter, moving it from {@link EncounterStatus#PAUSED}
     * back to {@link EncounterStatus#ACTIVE}.
     *
     * @param encounterId the stable encounter identifier
     * @param actor       the authenticated game master
     * @return the updated encounter
     * @throws EncounterStateTransitionException when the encounter is not PAUSED
     */
    public EncounterDto resumeEncounter(String encounterId, String actor) {
        Encounter encounter = requireEncounterAtState(encounterId, EncounterStatus.PAUSED);
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.GAME_MASTER);
        encounter.transitionTo(EncounterStatus.ACTIVE);
        bumpUpdated(encounter);
        return toDto(encounterRepository.save(encounter));
    }

    /**
     * Completes an encounter, moving it from {@link EncounterStatus#ACTIVE} or
     * {@link EncounterStatus#PAUSED} to the terminal {@link
     * EncounterStatus#COMPLETED} state.
     *
     * @param encounterId the stable encounter identifier
     * @param actor       the authenticated game master
     * @return the updated encounter
     * @throws EncounterStateTransitionException when the encounter is not ACTIVE
     * or PAUSED
     */
    public EncounterDto completeEncounter(String encounterId, String actor) {
        Encounter encounter = requireEncounterAtState(
                encounterId, EncounterStatus.ACTIVE, EncounterStatus.PAUSED);
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.GAME_MASTER);
        encounter.transitionTo(EncounterStatus.COMPLETED);
        bumpUpdated(encounter);
        return toDto(encounterRepository.save(encounter));
    }

    /**
     * Advances the encounter to the next turn, wrapping into the next round when
     * the current initiative list is exhausted. Only permitted while the
     * encounter is {@link EncounterStatus#ACTIVE}.
     *
     * @param encounterId the stable encounter identifier
     * @param actor       the authenticated game master
     * @return the updated encounter
     * @throws EncounterStateTransitionException when the encounter is not ACTIVE
     * @throws IllegalStateException              when the initiative order is
     *                                          empty
     */
    /**
     * Moves a participant to a new grid square, validating that the distance
     * travelled does not exceed the participant's available movement. The new
     * position is applied to the stored participant and recorded in the
     * immutable turn and audit history as an accepted mutation capturing the
     * before/after grid position.
     *
     * <p>The movement distance is validated server-side by
     * {@link MovementRangeService#resolveMovement(Encounter.Position, Encounter.Position, int)}
     * against the participant's configured {@link Participant#getMovementSpeed()}
     * movement. A participant that has no starting position, or a movement that
     * exceeds its available movement, is rejected with a clear diagnostic
     * ({@link IllegalArgumentException}) and no position change is applied.</p>
     *
     * @param encounterId   the stable encounter identifier
     * @param participantId the id of the participant to move
     * @param toX           the destination grid X coordinate
     * @param toY           the destination grid Y coordinate
     * @param note          a free-form note on the movement, or {@code null}
     * @param actor         the authenticated caller
     * @return the updated encounter
     * @throws IllegalArgumentException when the encounter or participant is
     *                                  missing, the participant has no starting
     *                                  position, or the movement exceeds the
     *                                  participant's available movement
     */
    public EncounterDto moveParticipant(String encounterId, String participantId,
                                        int toX, int toY, String note, String actor) {
        String resolvedParticipant = requireNonBlank(participantId, "participantId");
        Encounter encounter = requireEncounter(encounterId);
        ensureAccessible(actor, encounter);
        requireEncounterActiveForGameplay(encounter);
        Encounter.Participant participant = requireParticipant(encounter, resolvedParticipant);

        Encounter.Position from = participant.getPosition();
        if (from == null) {
            throw new IllegalArgumentException(
                    "Participant " + resolvedParticipant + " has no starting grid position to move from");
        }
        Encounter.Position to = new Encounter.Position(toX, toY);

        // Validate the movement distance against the participant's available
        // movement. A movement that exceeds the available movement is rejected
        // with a clear diagnostic and no position change is applied.
        MovementRangeService.ResolvedMovement resolved =
                movementRangeService.resolveMovement(from, to, participant.getMovementSpeed());

        participant.setPosition(to);

        int revisionBefore = encounter.getRevision();
        Map<String, Object> before = new HashMap<>();
        before.put("x", from.getX());
        before.put("y", from.getY());
        Map<String, Object> after = new HashMap<>();
        after.put("x", to.getX());
        after.put("y", to.getY());

        String description = "Moved (" + from.getX() + "," + from.getY() + ") to ("
                + to.getX() + "," + to.getY() + ") [" + resolved.distance() + " sq]"
                + noteSuffix(note);
        Action action = new Action(
                null, ActionType.MOVEMENT, actor, resolvedParticipant,
                participant.getName(), "position", List.of(), List.of(),
                resolved.distance(), resolved.distance(),
                ActionOutcome.SUCCESS, description, Instant.now());

        return mutate(encounter, actor, revisionBefore, action, List.of(
                new Mutation(null, "position", resolvedParticipant, description, before, after, Instant.now())));
    }

    public EncounterDto advanceTurn(String encounterId, String actor) {
        Encounter encounter = requireEncounterAtState(encounterId, EncounterStatus.ACTIVE);
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.GAME_MASTER);
        encounter.advanceTurn();
        bumpUpdated(encounter);
        return toDto(encounterRepository.save(encounter));
    }

    /**
     * Applies {@link DamageRequest#amount()} points of damage to a participant,
     * absorbing any temporary hit points first and clamping the current hit
     * points at {@code 0}. The change is applied to the stored participant and
     * recorded in the immutable turn and audit history.
     *
     * @param encounterId   the stable encounter identifier
     * @param participantId the id of the participant to damage
     * @param amount        the amount of damage to apply (non-negative)
     * @param note          a free-form note on the damage, or {@code null}
     * @param actor         the authenticated game master
     * @return the updated encounter
     * @throws IllegalArgumentException when the encounter or participant is
     *                                  missing or the amount is negative
     */
    public EncounterDto applyDamage(String encounterId, String participantId, int amount,
                                    String note, String actor) {
        String resolvedParticipant = requireNonBlank(participantId, "participantId");
        if (amount < 0) {
            throw new IllegalArgumentException("Damage amount must not be negative");
        }
        Encounter encounter = requireEncounter(encounterId);
        ensureAccessible(actor, encounter);
        requireEncounterActiveForGameplay(encounter);
        Encounter.Participant participant = requireParticipant(encounter, resolvedParticipant);
        Encounter.HitPoints hp = hitPointsFor(participant);

        int revisionBefore = encounter.getRevision();
        DamageService.ResolvedDamage resolved =
                damageService.resolveDamage(new DamageService.DamageInput(hp, amount));
        damageService.applyDamage(hp, resolved);

        List<Modifier> modifiers = List.of(new Modifier("damage", -resolved.amount()));
        Action action = new Action(
                null, ActionType.DAMAGE, actor, resolvedParticipant,
                participant.getName(), "hitPoints", modifiers, List.of(),
                -resolved.amount(), -resolved.damageToHitPoints(),
                resolved.droppedToZeroHitPoints() ? ActionOutcome.FAILURE : ActionOutcome.SUCCESS,
                note, Instant.now());
        Map<String, Object> before = hitPointsSnapshot(
                new Encounter.HitPoints(
                        hp.getMax(), hp.getCurrent() + resolved.damageToHitPoints(),
                        hp.getTemporary() + resolved.temporaryHitPointsAbsorbed()));
        Map<String, Object> after = hitPointsSnapshot(hp);

        return mutate(encounter, actor, revisionBefore, action,
                List.of(new Mutation(null, "hitPoints", resolvedParticipant,
                        "Applied damage",
                        before, after, Instant.now())));
    }

    public EncounterDto applyHealing(String encounterId, String participantId, int amount,
                                     String note, String actor) {
        String resolvedParticipant = requireNonBlank(participantId, "participantId");
        if (amount < 0) {
            throw new IllegalArgumentException("Healing amount must not be negative");
        }
        Encounter encounter = requireEncounter(encounterId);
        ensureAccessible(actor, encounter);
        requireEncounterActiveForGameplay(encounter);
        Encounter.Participant participant = requireParticipant(encounter, resolvedParticipant);
        Encounter.HitPoints hp = hitPointsFor(participant);

        int revisionBefore = encounter.getRevision();
        DamageService.ResolvedHealing resolved =
                damageService.resolveHealing(new DamageService.HealingInput(hp, amount));
        Map<String, Object> before = hitPointsSnapshot(hp);
        damageService.applyHealing(hp, resolved);
        Map<String, Object> after = hitPointsSnapshot(hp);

        List<Modifier> modifiers = List.of(new Modifier("heal", resolved.applied()));
        Action action = new Action(
                null, ActionType.DAMAGE, actor, resolvedParticipant,
                participant.getName(), "hitPoints", modifiers, List.of(),
                resolved.applied(), resolved.applied(),
                resolved.applied() > 0 ? ActionOutcome.SUCCESS : ActionOutcome.UNKNOWN,
                note, Instant.now());

        return mutate(encounter, actor, revisionBefore, action,
                List.of(new Mutation(null, "hitPoints", resolvedParticipant,
                        "Applied " + resolved.applied() + " healing".concat(note != null ? " (" + note + ")" : ""),
                        before, after, Instant.now())));
    }

    public EncounterDto applyTemporaryHitPoints(String encounterId, String participantId, int amount,
                                                String note, String actor) {
        String resolvedParticipant = requireNonBlank(participantId, "participantId");
        if (amount < 0) {
            throw new IllegalArgumentException("Temporary hit points must not be negative");
        }
        Encounter encounter = requireEncounter(encounterId);
        ensureAccessible(actor, encounter);
        requireEncounterActiveForGameplay(encounter);
        Encounter.Participant participant = requireParticipant(encounter, resolvedParticipant);
        Encounter.HitPoints hp = hitPointsFor(participant);

        int revisionBefore = encounter.getRevision();
        DamageService.ResolvedTemporaryHitPoints resolved =
                damageService.resolveTemporaryHitPoints(new DamageService.TemporaryHitPointsInput(hp, amount));
        damageService.applyTemporaryHitPoints(hp, resolved);

        List<Modifier> modifiers = List.of(new Modifier("temporary hit points", resolved.amount()));
        Action action = new Action(
                null, ActionType.DAMAGE, actor, resolvedParticipant,
                participant.getName(), "temporaryHitPoints", modifiers, List.of(),
                resolved.amount(), resolved.amount(),
                ActionOutcome.SUCCESS, note, Instant.now());
        Map<String, Object> before = hitPointsSnapshot(hp);
        hp.setTemporary(resolved.newTemporaryHitPoints());
        Map<String, Object> after = hitPointsSnapshot(hp);

        return mutate(encounter, actor, revisionBefore, action,
                List.of(new Mutation(null, "temporaryHitPoints", resolvedParticipant,
                        "Granted " + amount + " temporary hit points".concat(note != null ? " (" + note + ")" : ""),
                        before, after, Instant.now())));
    }

    public EncounterDto resolveDeathSave(String encounterId, String participantId,
                                         int savingThrowModifier, int failures, int successes,
                                         String rollMode, String seed, String note, String actor) {
        String resolvedParticipant = requireNonBlank(participantId, "participantId");
        Encounter encounter = requireEncounter(encounterId);
        ensureAccessible(actor, encounter);
        requireEncounterActiveForGameplay(encounter);
        Encounter.Participant participant = requireParticipant(encounter, resolvedParticipant);
        Encounter.HitPoints hp = hitPointsFor(participant);

        int revisionBefore = encounter.getRevision();
        int roll = rollD20(rollMode, seed);
        DamageService.DeathSaveOutcome outcome = damageService.resolveDeathSave(new DamageService.DeathSaveInput(
                hp, roll, savingThrowModifier, Math.max(0, failures), Math.max(0, successes),
                encounter.getRulesProfile()));
        if (outcome.recoveredHp()) {
            hp.setCurrent(outcome.hitPointsAfter());
        }

        List<Modifier> modifiers = List.of(new Modifier("death save", 0));
        ActionOutcome outcomeMapped = "DEAD".equals(outcome.result())
                ? ActionOutcome.FAILURE : ActionOutcome.SUCCESS;
        Action action = new Action(
                null, ActionType.SAVING_THROW, actor, resolvedParticipant,
                participant.getName(), "deathSave", modifiers, diceResultFor(roll),
                0, roll, outcomeMapped, note, Instant.now());
        Map<String, Object> before = new HashMap<>();
        before.put("failures", Math.max(0, failures));
        before.put("successes", Math.max(0, successes));
        before.put("hitPoints", hp.getCurrent());
        Map<String, Object> after = new HashMap<>();
        after.put("failures", outcome.failuresAfter());
        after.put("successes", outcome.successesAfter());
        after.put("hitPoints", hp.getCurrent());
        after.put("alive", outcome.alive());
        after.put("result", outcome.result());

        String description = "Death save rolled " + roll + " => " + outcome.result() + noteSuffix(note);
        return mutate(encounter, actor, revisionBefore, action,
                List.of(new Mutation(null, "deathSave", resolvedParticipant, description,
                        before, after, Instant.now())));
    }

    public EncounterDto addCondition(String encounterId, String participantId, String name,
                                     String description, Integer roundsRemaining, String actor) {
        String resolvedParticipant = requireNonBlank(participantId, "participantId");
        String conditionName = requireNonBlank(name, "name");
        Encounter encounter = requireEncounter(encounterId);
        ensureAccessible(actor, encounter);
        requireEncounterActiveForGameplay(encounter);
        Encounter.Participant participant = requireParticipant(encounter, resolvedParticipant);

        String canonical = damageService.commonCondition(conditionName) != null
                ? damageService.commonCondition(conditionName).canonicalName() : conditionName.toUpperCase();

        // An omitted or blank description falls back to the canonical description
        // of the recognised condition; unknown conditions keep whatever the caller
        // supplied (which may be null).
        String effectiveDescription = (description != null && !description.isBlank())
                ? description
                : (damageService.commonCondition(canonical) != null
                        ? damageService.commonCondition(canonical).description()
                        : null);

        int revisionBefore = encounter.getRevision();
        List<String> before = conditionNames(participant);
        applyCondition(participant, canonical, effectiveDescription, roundsRemaining);
        List<String> after = conditionNames(participant);

        String conditionDescription = (damageService.commonCondition(canonical) != null
                ? damageService.commonCondition(canonical).description() : "")
                + noteSuffix(canonical) + " applied";
        Action action = new Action(
                null, ActionType.OTHER, actor, resolvedParticipant,
                participant.getName(), "condition", List.of(), List.of(),
                0, 0, ActionOutcome.SUCCESS, conditionDescription, Instant.now());
        Map<String, Object> beforeMap = new HashMap<>();
        beforeMap.put("conditions", before);
        Map<String, Object> afterMap = new HashMap<>();
        afterMap.put("conditions", after);

        return mutate(encounter, actor, revisionBefore, action,
                List.of(new Mutation(null, "condition", resolvedParticipant,
                        "Applied condition '" + canonical + "'".concat(noteSuffix(description)),
                        beforeMap, afterMap, Instant.now())));
    }

    public EncounterDto removeCondition(String encounterId, String participantId, String name, String actor) {
        String resolvedParticipant = requireNonBlank(participantId, "participantId");
        String conditionName = requireNonBlank(name, "name");
        Encounter encounter = requireEncounter(encounterId);
        ensureAccessible(actor, encounter);
        requireEncounterActiveForGameplay(encounter);
        Encounter.Participant participant = requireParticipant(encounter, resolvedParticipant);

        int revisionBefore = encounter.getRevision();
        List<String> before = conditionNames(participant);
        boolean removed = removeCondition(participant, conditionName);
        List<String> after = conditionNames(participant);

        if (!removed) {
            throw new IllegalArgumentException(
                    "Participant " + resolvedParticipant + " does not have the condition '" + conditionName + "'");
        }
        Action action = new Action(
                null, ActionType.OTHER, actor, resolvedParticipant,
                participant.getName(), "condition", List.of(), List.of(),
                0, 0, ActionOutcome.SUCCESS, "Removed condition '" + conditionName.toUpperCase() + "'",
                Instant.now());
        Map<String, Object> beforeMap = new HashMap<>();
        beforeMap.put("conditions", before);
        Map<String, Object> afterMap = new HashMap<>();
        afterMap.put("conditions", after);

        return mutate(encounter, actor, revisionBefore, action,
                List.of(new Mutation(null, "condition", resolvedParticipant,
                        "Removed condition '" + conditionName.toUpperCase() + "'",
                        beforeMap, afterMap, Instant.now())));
    }

    private List<String> conditionNames(Encounter.Participant participant) {
        List<String> names = new ArrayList<>();
        for (Encounter.Condition c : participant.getConditions()) {
            names.add(c.getName());
        }
        return List.copyOf(names);
    }

    private void applyCondition(Encounter.Participant participant, String name,
                                String description, Integer roundsRemaining) {
        List<Encounter.Condition> conditions = participant.getConditions();
        for (int i = 0; i < conditions.size(); i++) {
            if (conditions.get(i).getName().equalsIgnoreCase(name)) {
                conditions.set(i, new Encounter.Condition(name, description, roundsRemaining));
                return;
            }
        }
        conditions.add(new Encounter.Condition(name, description, roundsRemaining));
    }

    private boolean removeCondition(Encounter.Participant participant, String name) {
        List<Encounter.Condition> conditions = participant.getConditions();
        Iterator<Encounter.Condition> it = conditions.iterator();
        while (it.hasNext()) {
            if (it.next().getName().equalsIgnoreCase(name)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    private List<DiceResult> diceResultFor(int roll) {
        return List.of(new DiceResult(null, "death save", "1d20", 20, 1, List.of(roll), 0, Instant.now()));
    }

    private int rollD20(String rollMode, String seed) {
        DiceExpression expression = DiceExpression.parse("1d20");
        List<DiceResult> results = diceRoller.roll(expression, "death save");
        if (results.isEmpty() || results.get(0).hasNoRolls()) {
            throw new IllegalStateException("The dice subsystem produced no value for the death save roll");
        }
        return results.get(0).rolls().get(0);
    }

    private String noteSuffix(String note) {
        return (note != null && !note.isBlank()) ? " (" + note + ")" : "";
    }

    private Map<String, Object> hitPointsSnapshot(Encounter.HitPoints hp) {
        Map<String, Object> m = new HashMap<>();
        m.put("max", hp.getMax());
        m.put("current", hp.getCurrent());
        m.put("temporary", hp.getTemporary());
        return m;
    }

    private Encounter.HitPoints hitPointsFor(Encounter.Participant participant) {
        Encounter.HitPoints hp = participant.getHitPoints();
        if (hp == null) {
            hp = new Encounter.HitPoints(0, 0, 0);
            participant.setHitPoints(hp);
        }
        return hp;
    }

    private void requireEncounterActiveForGameplay(Encounter encounter) {
        if (encounter.getStatus() != EncounterStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Damage, healing, temporary hit points and death saves can only be applied "
                            + "while the encounter is active (current state: " + encounter.getStatus() + ")");
        }
    }

    private Encounter.Participant requireParticipant(Encounter encounter, String participantId) {
        Optional<Encounter.Participant> found = encounter.getParticipants().stream()
                .filter(p -> participantId.equals(p.getId()))
                .findFirst();
        if (!found.isPresent()) {
            throw new IllegalArgumentException("Participant not found: " + participantId);
        }
        return found.get();
    }

    /**
     * Applies a mutation-bearing gameplay change to the stored encounter and
     * records it in the immutable turn and audit history. The participant is
     * mutated directly (already performed by the caller); the encounter revision
     * is bumped, the encounter saved and a single turn with the accepted
     * mutation appended.
     *
     * @param encounter      the already-loaded, already-access-encounter being mutated
     * @param actor          the authenticated actor recording the change
     * @param revisionBefore the revision before the change
     * @param action         the gameplay action summarising the change
     * @param mutations      the before/after mutations to record as accepted
     * @return the updated encounter
     */
    private EncounterDto mutate(Encounter encounter, String actor, int revisionBefore,
                                Action action, List<Mutation> mutations) {
        int revisionAfter = revisionBefore + 1;
        bumpUpdated(encounter);
        EncounterDto dto = toDto(encounterRepository.save(encounter));

        Turn turn = new Turn(
                null, encounter.getCampaignId(), encounter.getId(),
                encounter.getRound(), encounter.getTurn(), actor,
                List.of(action), List.of(),
                revisionBefore, revisionAfter, Instant.now(), Instant.now());
        List<MutationDecision> resolutions = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < mutations.size(); i++) {
            resolutions.add(MutationDecision.ACCEPTED);
            reasons.add(null);
        }
        gameplayAuditService.appendTurnAndAudit(
                turn, mutations, resolutions, reasons,
                revisionBefore, revisionAfter, actor, null);
        return dto;
    }

    /**
     * Records an initiative-generation event in the immutable turn and audit
     * history. A single turn is appended embedding one {@link ActionType#INITIATIVE}
     * action that carries every participant's auditable {@link DiceResult}, plus
     * one accepted {@link Mutation} per participant capturing the initiative
     * score's before (null) and after state. The encounter revision is bumped
     * exactly once and the encounter saved; the turn and every audit entry record
     * the revision before and after, so the initiative results are auditable and
     * the recorded outcome can never be retro-edited.
     *
     * @param encounter    the already-loaded, mutated encounter
     * @param actor        the authenticated actor recording the generation
     * @param revisionBefore the encounter revision before the generation
     * @param results      the auditable per-participant initiative results
     * @param note         a free-form note on the initiative generation
     * @return the audit trail of the generation (results and revisions)
     */
    private InitiativeService.Rollout recordInitiativeTurn(Encounter encounter, String actor, int revisionBefore,
                                              List<InitiativeResult> results, String note) {
        List<Mutation> mutations = new ArrayList<>();
        List<DiceResult> dice = new ArrayList<>();
        for (InitiativeResult r : results) {
            Map<String, Object> before = new HashMap<>();
            before.put("participantId", r.participantId());
            before.put("initiative", null);
            Map<String, Object> after = new HashMap<>();
            after.put("participantId", r.participantId());
            after.put("initiative", r.initiative());
            mutations.add(new Mutation(
                    null, "initiative", r.participantId(),
                    "Rolled initiative " + r.roll() + " => " + r.initiative(),
                    before, after, Instant.now()));
            dice.add(r.dieResult());
        }
        Action action = new Action(
                null, ActionType.INITIATIVE, actor, null, null, "initiative",
                List.of(), dice, 0, Math.max(0, results.size()),
                ActionOutcome.SUCCESS, note, Instant.now());
        int revisionAfter = revisionBefore + 1;
        // Capture the deterministic initiative order before the persisted save so
        // the audit trail reflects the established combat order.
        List<String> initiativeOrder = new ArrayList<>(encounter.getInitiativeOrder());
        bumpUpdated(encounter);
        toDto(encounterRepository.save(encounter));
        Turn turn = new Turn(
                null, encounter.getCampaignId(), encounter.getId(),
                Math.max(1, encounter.getRound()), 0, actor,
                List.of(action), dice,
                revisionBefore, revisionAfter, Instant.now(), Instant.now());
        List<MutationDecision> resolutions = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < mutations.size(); i++) {
            resolutions.add(MutationDecision.ACCEPTED);
            reasons.add(null);
        }
        gameplayAuditService.appendTurnAndAudit(
                turn, mutations, resolutions, reasons,
                revisionBefore, revisionAfter, actor, null);
        return new InitiativeService.Rollout(initiativeOrder, results, revisionBefore, revisionAfter);
    }

    private static String initNoteSuffix(RollMode mode, String seed) {
        return (mode == RollMode.SEEDED)
                ? " (seeded: " + seed.trim() + ")"
                : " (random)";
    }

    private void bumpUpdated(Encounter encounter) {
        encounter.setRevision(encounter.getRevision() + 1);
        encounter.setUpdatedAt(Instant.now());
    }

    private Encounter requireEncounter(String encounterId) {
        return encounterRepository.findById(encounterId).orElseThrow(
                () -> new IllegalArgumentException("Encounter not found: " + encounterId));
    }

    /**
     * Loads an encounter and asserts that it is in one of the expected states,
     * so lifecycle operations only ever observe the state they expect.
     */
    private Encounter requireEncounterAtState(String encounterId, EncounterStatus... expected) {
        Encounter encounter = requireEncounter(encounterId);
        for (EncounterStatus status : expected) {
            if (encounter.getStatus() == status) {
                return encounter;
            }
        }
        throw new EncounterStateTransitionException(encounter.getStatus(), expected[0]);
    }

    /**
     * Grants access when the actor holds at least the lowest {@link
     * MembershipRole#OBSERVER} role in the campaign the encounter belongs to.
     * Access is denied otherwise with a consistent {@link AuthorizationException}
     * mapped to a {@code 403 Forbidden} response.
     */
    private void ensureAccessible(String actor, Encounter encounter) {
        membershipService.assertAuthorized(encounter.getCampaignId(), actor, MembershipRole.OBSERVER);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * Projects a persisted document entity into the API-facing DTO.
     */
    private EncounterDto toDto(Encounter encounter) {
        return EncounterDto.from(encounter);
    }
}

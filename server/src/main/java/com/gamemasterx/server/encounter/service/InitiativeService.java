package com.gamemasterx.server.encounter.service;

import com.gamemasterx.server.dice.DiceExpression;
import com.gamemasterx.server.dice.DiceRoller;
import com.gamemasterx.server.dice.RollMode;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.gameplay.model.DiceResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Backend-owned authority for generating combat initiative through the shared
 * dice subsystem.
 *
 * <p>Initiative is never trusted from the wire: it is <b>generated</b> by rolling
 * a {@code 1d20} for every participant via the {@link DiceRoller} that backs the
 * {@code /api/dice} endpoints. This keeps initiative generation deterministic
 * where a seed is supplied and auditable otherwise (the individual die value that
 * produced each participant's initiative is recorded on the resulting
 * {@link DiceResult}).</p>
 *
 * <p>A single {@link DiceRoller} drives every participant's roll so that each
 * participant draws a distinct die value from one generator. In seeded mode this
 * yields a stable, reproducible sequence: the same participants and seed always
 * produce the same initiative order.</p>
 */
@Service
public final class InitiativeService {

    /** The dice expression resolved for each participant's initiative roll. */
    private static final String INITIATIVE_EXPRESSION = "1d20";

    private final DiceRoller diceRoller;

    /**
     * Creates the initiative service backed by the shared dice roller.
     *
     * @param diceRoller the shared {@link DiceRoller} that resolves die values
     */
    public InitiativeService(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    /**
     * Rolls a {@code 1d20} for every participant and assigns each participant's
     * initiative score from the resolved die value. The initiative score is
     * written back onto each {@link Encounter.Participant}.
     *
     * <p>Every participant receives a distinct die value drawn from the same
     * {@link DiceRoller}: a fresh roller is not created per participant, because a
     * seeded roller re-derives an identical generator from its seed and would
     * otherwise give every participant the same initiative value.</p>
     *
     * @param participants the participants to roll initiative for (mutated in place)
     * @param mode         the {@link RollMode} selecting the source of randomness
     * @param seed         the seed for {@link RollMode#SEEDED}, or {@code null}/blank otherwise
     * @return the ordered, auditable per-participant initiative results
     * @throws IllegalArgumentException when {@code participants} is {@code null} or empty,
     *                                  when {@code mode} is {@link RollMode#SEEDED} without a
     *                                  non-blank seed, or when the dice subsystem fails to
     *                                  resolve a value
     */
    public List<InitiativeResult> generate(List<Encounter.Participant> participants,
                                           RollMode mode, String seed) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required to generate initiative");
        }
        if (mode == RollMode.SEEDED && (seed == null || seed.isBlank())) {
            throw new IllegalArgumentException("A seed is required for a seeded initiative roll");
        }
        DiceExpression expression = DiceExpression.parse(INITIATIVE_EXPRESSION);
        // One roller drives every participant so that each participant draws a
        // distinct value from a single generator state.
        DiceRoller roller = new DiceRoller(mode, seed);
        Instant rolledAt = Instant.now();
        String seedForEcho = (mode == RollMode.SEEDED) ? seed.trim() : null;
        List<InitiativeResult> results = new ArrayList<>();
        for (Encounter.Participant participant : participants) {
            if (participant == null || participant.getId() == null) {
                throw new IllegalArgumentException("Every participant must have an id to roll initiative");
            }
            String label = participantLabel(participant);
            List<DiceResult> resolved = roller.roll(expression, label);
            DiceResult dieResult = resolved.get(0);
            if (dieResult == null || dieResult.rolls().isEmpty()) {
                throw new IllegalStateException(
                        "The dice subsystem produced no initiative value for " + participant.getId());
            }
            int value = dieResult.rolls().get(0);
            Long initiative = Long.valueOf(value);
            participant.setInitiative(initiative);
            results.add(new InitiativeResult(
                    participant.getId(),
                    participant.getName(),
                    initiative,
                    value,
                    dieResult,
                    mode.wire(),
                    seedForEcho,
                    rolledAt));
        }
        return List.copyOf(results);
    }

    /**
     * The outcome of an initiative generation: the auditable per-participant
     * {@link #results()} together with the revision before and after the roll so
     * a caller can surface the initiative with its audit trail.
     */
    public record Rollout(

            /** The established initiative order (participant ids in descending order). */
            List<String> initiativeOrder,

            /** The auditable per-participant initiative results. */
            List<InitiativeResult> results,

            /** The encounter revision immediately before the roll. */
            int revisionBefore,

            /** The encounter revision immediately after the roll. */
            int revisionAfter) {
    }

    /**
     * Generates initiative only for the participants that do not yet carry an
     * initiative score, leaving any pre-existing scores untouched. Used by the
     * encounter start flow so a game master's pre-set initiative is respected
     * while still guaranteeing that the combat order is ultimately resolved by
     * the dice subsystem.
     *
     * @param participants the participants to inspect and roll for
     * @param mode         the {@link RollMode}
     * @param seed         the seed for {@link RollMode#SEEDED}, or {@code null}/blank otherwise
     * @return the auditable per-participant initiative results for the participants that were rolled
     * @throws IllegalArgumentException when no participant is missing a score, when
     *                                  {@code participants} is {@code null} or empty, or when
     *                                  {@code mode} is {@link RollMode#SEEDED} without a seed
     */
    public List<InitiativeResult> generateForMissing(List<Encounter.Participant> participants,
                                                     RollMode mode, String seed) {
        boolean anyMissing = false;
        for (Encounter.Participant p : participants) {
            if (p.getInitiative() == null) {
                anyMissing = true;
                break;
            }
        }
        if (!anyMissing) {
            return List.of();
        }
        return generate(participants, mode, seed);
    }

    private static String participantLabel(Encounter.Participant participant) {
        if (participant.getName() != null && !participant.getName().isBlank()) {
            return participant.getName() + " initiative";
        }
        return "initiative";
    }

    /**
     * A single participant's auditable initiative roll.
     *
     * <p>The {@link #initiative()} score equals the {@link #roll()} value of the
     * {@code 1d20} that was resolved, and the full {@link #dieResult()} preserves
     * the individual die value so the roll can be replayed and verified.</p>
     */
    public record InitiativeResult(

            /** Identifier of the participant the roll was made for. */
            String participantId,

            /** Name of the participant, when one was supplied. */
            String participantName,

            /** The initiative score assigned to the participant (the die total). */
            Long initiative,

            /** The raw {@code 1d20} value that was rolled. */
            int roll,

            /** The auditable die record resolved through the dice subsystem. */
            DiceResult dieResult,

            /** The applied roll mode as a wire string ({@code random} / {@code seeded}). */
            String rollMode,

            /** The seed that produced this roll, or {@code null} for random mode. */
            String seed,

            /** Timestamp at which the roll was resolved. */
            Instant rolledAt) {

        /**
         * Canonical constructor enforcing the non-null die record and timestamp.
         */
        public InitiativeResult {
            Objects.requireNonNull(dieResult, "dieResult must not be null");
            Objects.requireNonNull(rolledAt, "rolledAt must not be null");
        }
    }
}

package com.gamemasterx.server.gameplay.service;

import com.gamemasterx.server.dice.DiceExpression;
import com.gamemasterx.server.dice.DiceRoller;
import com.gamemasterx.server.encounter.model.RulesProfile;
import com.gamemasterx.server.gameplay.model.DiceResult;
import com.gamemasterx.server.gameplay.model.Modifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application service that owns the deterministic resolution of the
 * short-rest and long-rest recovery rules of the supported rules subset
 * ({@link RulesProfile#SRD_5_2024} and, through it, the standard fifth-edition
 * rest rules).
 *
 * <p>This service is the single, closed authority for what a short rest and a
 * long rest recover. It is deliberately pure: every method is a deterministic
 * function of its inputs and the governing {@link RulesProfile} and never touches
 * storage. The {@link com.gamemasterx.server.encounter.service.EncounterService}
 * is the only layer that loads the aggregate, calls this service to resolve the
 * recovery for each participant, applies the resolved outcome to the stored
 * participants and persists it (recording it in the immutable audit trail).</p>
 *
 * <h2>Short rest</h2>
 *
 * <p>A short rest lasts {@value #SHORT_REST_HOURS} hour. During it a
 * participant may spend any number of their available {@link HitDice} to
 * recover hit points. For each Hit Die spent the participant recovers
 * {@code (hit die value + Constitution modifier)} hit points, capped at the
 * points they were actually missing, and the Hit Die is consumed. The number of
 * Hit Dice a participant may spend is capped by the number they hold, so the
 * recovered total never exceeds what was needed to reach maximum hit points.</p>
 *
 * <p>Each spent Hit Die is resolved through the shared dice subsystem so the
 * individual die values are auditable and, in seeded mode, reproducible. The
 * fixed default Hit Die size is {@value #DEFAULT_HIT_DIE_SIZE} (a six-sided die,
 * matching the subset's fixed short-rest recovery of {@code 2 + Con} for a
 * six-sided die), which the caller may override.</p>
 *
 * <h2>Long rest</h2>
 *
 * <p>A long rest lasts {@value #LONG_REST_HOURS} hours. On completing a long rest
 * a participant:</p>
 *
 * <ul>
 *   <li>recovers all hit points, to their maximum;</li>
 *   <li>recovers spent Hit Dice up to the number
 *       {@code floor(totalHitDice / 2)} (rounded down) &ndash; the number the
 *       subset allows a participant to recover from a long rest; and</li>
 *   <li>recovers every other spent resource (spell slots, action charges and
 *           like limits) to its maximum.</li>
 * </ul>
 *
 * <p>Every method here is deterministic: the same inputs always produce the same
 * recovered amounts.</p>
 */
@Service
public final class RestService {

    /** The duration of a short rest, in hours, under the supported subset. */
    public static final int SHORT_REST_HOURS = 1;

    /** The duration of a long rest, in hours, under the supported subset. */
    public static final int LONG_REST_HOURS = 8;

    /** The default Hit Die size used when the caller does not supply one. */
    public static final int DEFAULT_HIT_DIE_SIZE = 6;

    /** The canonical name of the resource that tracks a participant's Hit Dice. */
    public static final String HIT_DIE_RESOURCE_NAME = "Hit Dice";

    private final DiceRoller diceRoller;

    /**
     * Creates the rest service with its shared dice roller, so spent Hit Dice
     * are resolved through the same deterministic subsystem used everywhere
     * else.
     *
     * @param diceRoller the shared dice roller
     */
    public RestService(DiceRoller diceRoller) {
        this.diceRoller = Objects.requireNonNull(
                diceRoller, "A dice roller is required");
    }

    /**
     * Resolves a short rest for a single participant.
     *
     * <p>The participant spends up to {@link #hitDiceToSpend()} of their
     * available {@link #availableHitDice()} Hit Dice. For each Die spent the
     * participant recovers {@code min(hitDieValue + conModifier, missingHitPoints)}
     * hit points, and the Die is consumed. The recovered total is capped so it
     * never raises the participant's current hit points past their maximum, even
     * when more Dice than were needed are spent.</p>
     *
     * @param input the short-rest input
     * @return the resolved short rest and its effect on the participant
     * @throws IllegalArgumentException when {@code input} is {@code null}, the hit
     *                                  points are outside {@code [0, max]}, or the
     *                                  number of Hit Dice to spend is negative
     */
    public ResolvedShortRest resolveShortRest(ShortRestInput input) {
        Objects.requireNonNull(input, "A short rest input is required");
        DicePointsState.validate(input.hitPoints());

        int hitDieSize = normalizeHitDieSize(input.hitDieSize());
        int conModifier = input.conModifier();
        int available = Math.max(0, input.availableHitDice());
        int toSpend = Math.max(0, input.hitDiceToSpend());
        int spent = Math.min(toSpend, available);

        int missing = Math.max(0, input.hitPoints().max() - input.hitPoints().current());
        int hpRecovered = 0;
        List<DiceResult> perDie = new ArrayList<>();
        for (int i = 0; i < spent; i++) {
            int value = rollHitDieValue(hitDieSize);
            int recoveredForDie = value + conModifier;
            int recovered = Math.max(0, Math.min(recoveredForDie, missing - hpRecovered));
            hpRecovered += recovered;
            perDie.add(new DiceResult(
                    null, "hit die", "1d" + hitDieSize, hitDieSize, 1,
                    List.of(value), conModifier, java.time.Instant.now()));
            if (hpRecovered >= missing) {
                break;
            }
        }

        // Every Die that was rolled and applied is one Die that was spent: the
        // participant never spends a Die they do not roll, and stops spending
        // once they are at their maximum.
        int hitDiceSpent = perDie.size();
        int newCurrent = Math.min(input.hitPoints().max(),
                input.hitPoints().current() + hpRecovered);
        int hitDiceRemaining = available - hitDiceSpent;

        return new ResolvedShortRest(
                hitDieSize,
                conModifier,
                hitDiceSpent,
                hpRecovered,
                newCurrent,
                hitDiceRemaining,
                List.copyOf(perDie));
    }

    /**
     * Resolves a long rest for a single participant.
     *
     * <p>The participant recovers all hit points to their maximum, recovers spent
     * Hit Dice up to {@code floor(totalHitDice / 2)} (rounded down), and recovers
     * every other resource to its maximum. Hit Dice are modelled as a single
     * {@link HitDice} pool; all other resources are passed as {@link ResourceRecovery}
     * records and are each restored to their maximum.</p>
     *
     * @param input the long-rest input
     * @return the resolved long rest and its effect on the participant
     * @throws IllegalArgumentException when {@code input} is {@code null} or the
     *                                  hit points are outside {@code [0, max]}
     */
    public ResolvedLongRest resolveLongRest(LongRestInput input) {
        Objects.requireNonNull(input, "A long rest input is required");
        DicePointsState.validate(input.hitPoints());

        RulesProfile profile = (input.rulesProfile() != null)
                ? input.rulesProfile()
                : RulesProfile.SRD_5_2024;

        int newCurrent = input.hitPoints().max();
        int hpRecovered = newCurrent - input.hitPoints().current();

        int totalHitDice = Math.max(0, input.hitDiceTotal());
        int spentHitDice = Math.max(0, totalHitDice - input.hitDiceCurrent());
        int recoverable = totalHitDice / 2;
        int hitDiceRecovered = Math.min(spentHitDice, recoverable);
        int newHitDice = Math.min(totalHitDice,
                input.hitDiceCurrent() + hitDiceRecovered);

        List<ResourceRecovery> recovered = new ArrayList<>();
        long totalResourcePointsRecovered = 0;
        for (ResourceRecovery resource : input.otherResources()) {
            int max = Math.max(0, resource.max());
            int current = Math.max(0, Math.min(max, resource.current()));
            int recoveredPoints = max - current;
            totalResourcePointsRecovered += recoveredPoints;
            recovered.add(new ResourceRecovery(
                    resource.name(), current, max, recoveredPoints));
        }

        return new ResolvedLongRest(
                hpRecovered,
                newCurrent,
                input.hitDiceCurrent(),
                newHitDice,
                hitDiceRecovered,
                totalHitDice,
                recoverable,
                List.copyOf(recovered),
                totalResourcePointsRecovered,
                profile);
    }

    private int rollHitDieValue(int hitDieSize) {
        if (hitDieSize < 2) {
            throw new IllegalArgumentException(
                    "A Hit Die must have at least two faces (got " + hitDieSize + ")");
        }
        DiceExpression expression = DiceExpression.parse("1d" + hitDieSize);
        List<DiceResult> results = diceRoller.roll(expression, "hit die");
        if (results.isEmpty() || results.get(0).hasNoRolls()) {
            throw new IllegalStateException(
                    "The dice subsystem produced no value for the hit die");
        }
        return results.get(0).rolls().get(0);
    }

    private static int normalizeHitDieSize(Integer hitDieSize) {
        return (hitDieSize != null && hitDieSize >= 2)
                ? hitDieSize
                : DEFAULT_HIT_DIE_SIZE;
    }

    /**
     * @param hitPoints the participant's hit points
     * @return the number of hit points the participant is currently missing
     */
    private static final class DicePointsState {
        private DicePointsState() {
        }

        static void validate(HitPoints hitPoints) {
            if (hitPoints.max() < 0) {
                throw new IllegalArgumentException("Maximum hit points must not be negative");
            }
            if (hitPoints.current() < 0 || hitPoints.current() > hitPoints.max()) {
                throw new IllegalArgumentException(
                        "Current hit points must be within [0, max]");
            }
        }
    }

    /**
     * The hit-point state required by the rest resolution methods.
     *
     * @param max    the participant's maximum hit points
     * @param current the participant's current hit points
     * @param temporary the participant's temporary hit points
     */
    public record HitPoints(int max, int current, int temporary) {
    }

    /**
     * A hit-point resource used to resolve a long rest: its name, its current
     * value, its maximum and, for a resolved long rest, the number of points the
     * rest restored.
     *
     * @param name       the resource name
     * @param current    the current value
     * @param max        the maximum value
     * @param recovered  the number of points restored (zero for an input resource)
     */
    public record ResourceRecovery(String name, int current, int max, int recovered) {
    }

    /**
     * Input for {@link #resolveShortRest(ShortRestInput)}. The {@link
     * #hitPoints()} are not mutated by this method.
     *
     * @param hitPoints       the participant's hit points (not mutated)
     * @param availableHitDice the number of Hit Dice the participant holds
     * @param hitDieSize       the size of the participant's Hit Dice, or {@code null}
     *                         to use the default six-sided die
     * @param conModifier      the participant's Constitution modifier added to each Die
     * @param hitDiceToSpend   the number of Hit Dice the participant wishes to spend
     */
    public record ShortRestInput(
            HitPoints hitPoints,
            int availableHitDice,
            Integer hitDieSize,
            int conModifier,
            int hitDiceToSpend) {
    }

    /**
     * The auditable outcome of a resolved short rest.
     *
     * @param hitDieSize       the size of the Hit Dice that were rolled
     * @param conModifier      the Constitution modifier applied to each Die
     * @param hitDiceSpent     the number of Hit Dice that were spent
     * @param hpRecovered      the hit points recovered
     * @param newCurrentHitPoints the participant's current hit points after the rest
     * @param hitDiceRemaining the Hit Dice remaining after the rest
     * @param perDie           the auditable per-Die results that were resolved
     */
    public record ResolvedShortRest(
            int hitDieSize,
            int conModifier,
            int hitDiceSpent,
            int hpRecovered,
            int newCurrentHitPoints,
            int hitDiceRemaining,
            List<DiceResult> perDie) {
    }

    /**
     * Input for {@link #resolveLongRest(LongRestInput)}. The hit points are not
     * mutated by this method.
     *
     * @param hitPoints          the participant's hit points (not mutated)
     * @param hitDiceCurrent      the participant's current Hit Dice
     * @param hitDiceTotal        the participant's total (maximum) Hit Dice
     * @param otherResources      the participant's other limited resources, each restored
     *                            to its maximum by the long rest
     * @param rulesProfile        the governing rules profile, or {@code null} to default
     *                            to {@link RulesProfile#SRD_5_2024}
     */
    public record LongRestInput(
            HitPoints hitPoints,
            int hitDiceCurrent,
            int hitDiceTotal,
            List<ResourceRecovery> otherResources,
            RulesProfile rulesProfile) {
    }

    /**
     * The auditable outcome of a resolved long rest.
     *
     * @param hpRecovered              the hit points recovered (all missing points)
     * @param newCurrentHitPoints      the participant's current hit points after the rest
     * @param hitDiceBefore            the participant's Hit Dice before the rest
     * @param hitDiceAfter             the participant's Hit Dice after the rest
     * @param hitDiceRecovered         the Hit Dice recovered by the rest
     * @param totalHitDice             the participant's total Hit Dice
     * @param hitDiceRecoverableMax    the maximum Hit Dice a long rest may restore
     *                                 ({@code floor(totalHitDice / 2)})
     * @param resourcesRecovered       the per-resource recovery records
     * @param resourcePointsRecovered  the total resource points recovered across all resources
     * @param profile                  the governing rules profile
     */
    public record ResolvedLongRest(
            int hpRecovered,
            int newCurrentHitPoints,
            int hitDiceBefore,
            int hitDiceAfter,
            int hitDiceRecovered,
            int totalHitDice,
            int hitDiceRecoverableMax,
            List<ResourceRecovery> resourcesRecovered,
            long resourcePointsRecovered,
            RulesProfile profile) {
    }
}

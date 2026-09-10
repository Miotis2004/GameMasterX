package com.gamemasterx.server.gameplay.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable record capturing the resolution of a single die roll (or batch of
 * identical dice).
 *
 * <p>A {@code DiceResult} is a pure, value-typed record: once constructed its
 * contents cannot change. It captures exactly what the audit requirements ask
 * for &ndash; the <em>inputs</em> ({@link #label} and {@link #diceExpression}),
 * the <em>random values</em> ({@link #rolls}) that were drawn, an optional
 * {@link #modifier}, and the resulting {@link #total} which is the final
 * outcome of the roll).</p>
 *
 * <p>These records are embedded inside {@link Action} and {@link Turn} records
 * and are likewise written verbatim into the append-oriented audit log, so the
 * exact random values that produced an outcome are preserved for later
 * verification.</p>
 */
public record DiceResult(
        /** Stable identifier of this roll result, or {@code null} when transient. */
        String id,

        /**
         * Human-readable label describing what the roll represents, for example
         * {@code "Attack roll"} or {@code "Damage"}.
         */
        String label,

        /**
         * The dice expression that was resolved, for example {@code "2d6+3"} or
         * {@code "1d20"}. Captured as supplied; not parsed here.
         */
        String diceExpression,

        /** Size of the die (number of faces), for example {@code 6} or {@code 20}. */
        int dieSize,

        /** Number of dice rolled of the given {@link #dieSize}. */
        int numberOfDice,

        /**
         * The individual random values drawn, one per die. The list is the
         * authoritative record of the randomness that produced this result. The
         * returned list is unmodifiable.
         */
        List<Integer> rolls,

        /** Constant added to (or subtracted from) the sum of the rolls. */
        int modifier,

        /**
         * The final outcome of the roll: {@code sum(rolls) + modifier}. When the
         * rolls are absent this is computed as simply {@code modifier}.
         */
        int total,

        /** Timestamp of when the roll was resolved, or {@code null} when transient. */
        Instant rolledAt) {

    /**
     * Convenience factory that fills in {@link #total} and normalises the rolls
     * and label. Rolls default to an empty list; the label defaults to the dice
     * expression when {@code null} or blank.
     *
     * @param id            stable identifier, or {@code null}
     * @param label         human-readable label, or {@code null}
     * @param diceExpression the dice expression, for example {@code "2d6+3"}
     * @param dieSize       number of faces on each die
     * @param numberOfDice  how many dice of the given size were rolled
     * @param rolls         the individual drawn values (may be empty)
     * @param modifier      constant modifier applied to the sum of the rolls
     * @param rolledAt      the time the roll was resolved, or {@code null}
     */
    public DiceResult(String id, String label, String diceExpression, int dieSize, int numberOfDice,
                      List<Integer> rolls, int modifier, Instant rolledAt) {
        this(
                id,
                (label != null && !label.isBlank()) ? label : diceExpression,
                diceExpression,
                dieSize,
                numberOfDice,
                (rolls != null) ? List.copyOf(rolls) : List.of(),
                modifier,
                (rolls != null) ? rolls.stream().mapToInt(Integer::intValue).sum() + modifier : modifier,
                rolledAt);
    }

    /**
     * @return an unmodifiable view of the individual die values
     */
    public List<Integer> rolls() {
        return Collections.unmodifiableList(rolls);
    }

    /**
     * @return {@code true} when no individual die value was recorded
     */
    public boolean hasNoRolls() {
        return rolls.isEmpty();
    }

    /**
     * @return a defensive copy of this result with a newly assigned id, used
     * when materialising a transient result as a persisted audit entry
     */
    public DiceResult withId(String newId) {
        return new DiceResult(
                newId, label, diceExpression, dieSize, numberOfDice, rolls, modifier, rolledAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiceResult other)) return false;
        return dieSize == other.dieSize
                && numberOfDice == other.numberOfDice
                && modifier == other.modifier
                && total == other.total
                && Objects.equals(id, other.id)
                && Objects.equals(label, other.label)
                && Objects.equals(diceExpression, other.diceExpression)
                && Objects.equals(rolls, other.rolls)
                && Objects.equals(rolledAt, other.rolledAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label, diceExpression, dieSize, numberOfDice, rolls, modifier, total, rolledAt);
    }

    @Override
    public String toString() {
        return "DiceResult[" +
                "label=" + label +
                ", diceExpression=" + diceExpression +
                ", rolls=" + rolls +
                ", modifier=" + modifier +
                ", total=" + total +
                ", rolledAt=" + rolledAt + ']';
    }
}

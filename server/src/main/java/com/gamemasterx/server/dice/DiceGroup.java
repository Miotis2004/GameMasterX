package com.gamemasterx.server.dice;

import com.gamemasterx.server.dice.DiceExpression.Advantage;

import java.util.Objects;

/**
 * An immutable, parsed batch of identical dice within a {@link DiceExpression}.
 *
 * <p>A group captures how many dice were rolled ({@link #count()}), how many
 * faces each die has ({@link #sides()}), any {@link #advantage()} applied, and
 * a constant {@link #modifier()} added to (or subtracted from) the sum of the
 * rolled values. The group {@link #subtotal()} is
 * {@code sum(rolls) + modifier}.</p>
 */
public record DiceGroup(
        /** Number of dice of the given {@link #sides()} to roll. */
        int count,

        /** Number of faces on each die (for example {@code 6} or {@code 20}). */
        int sides,

        /** Whether advantage or disadvantage was applied to the batch. */
        Advantage advantage,

        /** Constant added to (or subtracted from) the sum of the rolled values. */
        int modifier) {

    /** Inclusive minimum value a single group can produce. */
    static final int MIN_SIDES = 2;
    /** Inclusive maximum number of dice permitted in a single expression. */
    static final int MAX_DICE = 10000;
    /** Inclusive maximum number of faces permitted on a single die. */
    static final int MAX_SIDES = 1000;
    /** Inclusive maximum absolute modifier permitted on a single group. */
    static final int MAX_MODIFIER = 100_000;

    /**
     * Builds and validates a die group.
     *
     * @param count      the number of dice, at least {@code 1}
     * @param sides      the number of faces, between {@link #MIN_SIDES} and
     *                   {@link #MAX_SIDES} inclusive
     * @param advantage  the advantage applied, or {@link Advantage#NONE}
     * @param modifier   the constant modifier
     * @return a validated {@link DiceGroup}
     * @throws DiceExpressionException if any field is out of range
     */
    public static DiceGroup of(int count, int sides, Advantage advantage, int modifier) {
        if (count < 1) {
            throw new DiceExpressionException("expression",
                    "A dice group must roll at least one die");
        }
        if (sides < MIN_SIDES || sides > MAX_SIDES) {
            throw new DiceExpressionException("expression",
                    "A die must have between " + MIN_SIDES + " and " + MAX_SIDES + " faces");
        }
        if (advantage == null) {
            advantage = Advantage.NONE;
        }
        if (Math.abs(modifier) > MAX_MODIFIER) {
            throw new DiceExpressionException("expression",
                    "A modifier must be between -" + MAX_MODIFIER + " and " + MAX_MODIFIER);
        }
        return new DiceGroup(count, sides, advantage, modifier);
    }

    /**
     * @return the minimum achievable subtotal for this group:
     *         {@code count + modifier}
     */
    public int minimum() {
        return count + modifier;
    }

    /**
     * @return the maximum achievable subtotal for this group:
     *         {@code count * sides + modifier}
     */
    public int maximum() {
        return Math.toIntExact((long) count * sides + modifier);
    }

    /**
     * @return {@code true} when this group applies advantage or disadvantage
     */
    public boolean hasAdvantage() {
        return advantage != Advantage.NONE;
    }

    /**
     * @return the canonical text of this group, for example {@code "2d6+3"} or
     *         {@code "1d20dis"}
     */
    public String text() {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append('d').append(sides);
        if (advantage == Advantage.ADVANTAGE) {
            sb.append("adv");
        } else if (advantage == Advantage.DISADVANTAGE) {
            sb.append("dis");
        }
        if (modifier > 0) {
            sb.append('+').append(modifier);
        } else if (modifier < 0) {
            sb.append(modifier);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiceGroup other)) return false;
        return count == other.count
                && sides == other.sides
                && modifier == other.modifier
                && advantage == other.advantage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, sides, advantage, modifier);
    }

    @Override
    public String toString() {
        return text();
    }
}

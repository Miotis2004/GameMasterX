package com.gamemasterx.server.dice;

import com.gamemasterx.server.gameplay.model.DiceResult;

import java.util.List;

/**
 * Response body for a dice roll, as documented in the dice API contract.
 *
 * <p>The response mirrors the request ({@link #expression()} and
 * {@link #mode()}) and, for {@link RollMode#SEEDED} rolls, echoes back the
 * {@link #seed()} that was used so the exact generator state can be reproduced
 * elsewhere. The resolved {@link #results()} are the authoritative, auditable
 * records: each one records the individual {@link DiceResult#rolls()} that were
 * drawn, the {@link DiceResult#modifier()} and the resulting
 * {@link DiceResult#total()}. {@link #total()} is the sum of every result's
 * {@link DiceResult#total()}.</p>
 */
public record DiceRollResponse(

        /** The expression that was resolved (as supplied). */
        String expression,

        /** The {@link RollMode#wire()} that was applied. */
        String mode,

        /**
         * The seed that produced this roll, or {@code null} when the roll was
         * made in {@link RollMode#RANDOM} mode (no seed is used).
         */
        String seed,

        /** The ordered, auditable resolved results, one per expression group. */
        List<DiceResult> results,

        /** The aggregate total: the sum of every {@link DiceResult#total()}. */
        int total) {

    /**
     * Builds a response from a rolled {@link DiceRoller} and its results,
     * computing the aggregate total as the sum of every result's total.
     *
     * @param roller  the roller that produced the results (provides mode and seed)
     * @param expression the original expression string, as supplied by the caller
     * @param results the resolved results, in expression order
     * @return the immutable {@link DiceRollResponse}
     */
    public static DiceRollResponse of(DiceRoller roller, String expression, List<DiceResult> results) {
        int total = 0;
        for (DiceResult r : results) {
            total += r.total();
        }
        return new DiceRollResponse(
                expression,
                roller.mode().wire(),
                roller.effectiveSeed(),
                results,
                total);
    }
}

package com.gamemasterx.server.dice;

/**
 * Request body for a dice roll, as documented in the dice API contract.
 *
 * <p>The request carries the {@link #expression()} to resolve, the
 * {@link #mode()} selecting between unpredictable and reproducible rolls, and,
 * when {@link #mode()} is {@link RollMode#SEEDED}, the {@link #seed()} used to
 * derive the deterministic generator.</p>
 */
public record DiceRollRequest(

        /**
         * The parsed-free dice expression to resolve, for example {@code "2d6+3"}
         * or {@code "1d20dis"}. Parsed on receipt; a malformed expression is
         * rejected with a {@code 400 VALIDATION_ERROR}.
         */
        String expression,

        /**
         * The {@link RollMode} selecting the source of randomness. Wire value is
         * {@code "random"} or {@code "seeded"} (case-insensitive). When omitted
         * the default is {@link RollMode#RANDOM}.
         */
        String mode,

        /**
         * The caller-supplied seed for {@link RollMode#SEEDED} rolls. Required
         * when {@link #mode()} is {@code "seeded"}; ignored otherwise.
         */
        String seed,

        /**
         * A human-readable label applied to each resolved
         * {@link com.gamemasterx.server.gameplay.model.DiceResult}, or
         * {@code null}/blank to default to the group text.
         */
        String label) {
}

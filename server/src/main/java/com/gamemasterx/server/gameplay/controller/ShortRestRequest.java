package com.gamemasterx.server.gameplay.controller;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for a short rest ({@code POST /api/encounters/{id}/rest/short}).
 *
 * <p>A short rest lets each participant spend any number of their available Hit
 * Dice to recover hit points. The number of Hit Dice spent is capped by what
 * each participant holds, and the recovered hit points never exceed the amount
 * needed to reach maximum hit points.</p>
 */
public record ShortRestRequest(

        /**
         * The number of Hit Dice each participant spends. May be any value up to
         * the number of Hit Dice each participant holds; a value of {@code 0} is a
         * no-op short rest that spends nothing.
         */
        @PositiveOrZero
        int hitDiceToSpend,

        /**
         * The size of each participant's Hit Die, for example {@code 6}, {@code 8}
         * or {@code 10}. When omitted the default {@value
         * com.gamemasterx.server.gameplay.service.RestService#DEFAULT_HIT_DIE_SIZE}-sided
         * die is used.
         */
        Integer hitDieSize,

        /**
         * Each participant's Constitution modifier added to each Hit Die. Defaults
         * to {@code 0} when omitted.
         */
        Integer conModifier,

        /**
         * The roll mode for the hit-die rolls: {@code "random"} (default) or
         * {@code "seeded"}. Case-insensitive.
         */
        String rollMode,

        /**
         * The seed for a {@code "seeded"} hit-die roll, or {@code null}. Required
         * when {@link #rollMode()} is {@code "seeded"}.
         */
        String seed,

        /**
         * A free-form note recorded on the audited short-rest action, or
         * {@code null}.
         */
        String note) {
}

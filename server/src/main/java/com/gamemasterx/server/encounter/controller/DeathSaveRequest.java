package com.gamemasterx.server.encounter.controller;

/**
 * Request body for resolving a death saving throw on a participant, as accepted
 * by the
 * {@code POST /api/encounters/{id}/participants/{participantId}/death-save}
 * endpoint.
 *
 * <p>The death save is resolved server-side by the backend-owned
 * {@link com.gamemasterx.server.gameplay.service.DamageService} under the
 * encounter's currently selected, supported rules subset. A d20 is rolled
 * (server-side, via the shared dice subsystem) and the accumulated failures and
 * successes are updated; three failures mean death and three successes make the
 * creature stable. The creature must already be at {@code 0} hit points.</p>
 */
public record DeathSaveRequest(

        /**
         * The saving-throw modifier to add to the d20 roll (may be negative).
         */
        int savingThrowModifier,

        /**
         * The participant's accumulated failure count before this throw.
         */
        int failures,

        /**
         * The participant's accumulated success count before this throw.
         */
        int successes,

        /**
         * The roll mode: {@code "random"} (default) or {@code "seeded"}. Case
         * insensitive.
         */
        String rollMode,

        /**
         * The seed for a {@code "seeded"} roll. Required when
         * {@link #rollMode()} is {@code "seeded"}; ignored otherwise.
         */
        String seed,

        /** Optional free-form note recorded with the death save. */
        String note) {
}

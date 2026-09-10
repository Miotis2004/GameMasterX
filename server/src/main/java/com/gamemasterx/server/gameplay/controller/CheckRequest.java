package com.gamemasterx.server.gameplay.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for a gameplay check (ability check, skill check or saving
 * throw), as accepted by the {@code /api/gameplay} endpoints.
 *
 * <p>The three endpoints share a single request shape; the endpoint itself
 * selects the {@link com.gamemasterx.server.gameplay.model.CheckType}, which
 * determines how proficiency is applied. The ability modifier is derived
 * server-side from {@link #abilityScore()} using the standard
 * {@code floor((score - 10) / 2)} formula, so callers never compute or trust it.
 * Proficiency is applied via {@link #proficiencyBonus()} (&ndash; a value of
 * {@code 0} means "no proficiency bonus"), and for ability checks and saving
 * throws it is only applied when {@link #proficient()} is {@code true}. Skill
 * checks always apply proficiency.</p>
 *
 * <p>The check is rolled with the shared dice subsystem. {@link #rollMode()}
 * selects {@code "random"} (the default) or {@code "seeded"}; a seeded roll must
 * also carry a non-blank {@link #seed()}. The individual die value is recorded on
 * the resulting audit record, so the roll is fully auditable.</p>
 *
 * <p>{@link #encounterId()} identifies the encounter whose revision bounds the
 * audited record.</p>
 */
public record CheckRequest(

        /** Identifier of the actor (participant or NPC) performing the check. */
        @NotBlank
        String actorId,

        /** Stable identifier of the campaign the check is performed in. */
        String campaignId,

        /**
         * Identifier of the encounter that bounds the audited record. When
         * present it is used to read the revision before/after; when omitted the
         * check is resolved and recorded without an encounter.
         */
        String encounterId,

        /** Identifier of the targeted participant, when applicable. */
        String targetId,

        /** Human-readable name of the target, when applicable. */
        String targetName,

        /**
         * The ability the check is based on, for example {@code "Strength"}.
         * Recorded on the audit action as the ability used.
         */
        @NotBlank
        String abilityName,

        /**
         * The raw ability score. The ability modifier is derived
         * server-side; this value is not trusted as a modifier. Validated to the
         * same {@code [1, 30]} range as character ability scores.
         */
        @Min(1)
        @Max(30)
        int abilityScore,

        /** Whether the actor is proficient in the check. Ignored by skill checks. */
        boolean proficient,

        /**
         * The proficiency bonus to apply when proficient. A value of {@code 0}
         * contributes no proficiency bonus.
         */
        int proficiencyBonus,

        /**
         * The difficulty class the final total must meet or exceed to succeed, or
         * {@code null} for an unqualified roll (outcome recorded as unknown).
         */
        Integer dc,

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

        /** Optional human-readable label for the resolved die result. */
        String label,

        /** Optional free-form note recorded on the audit action. */
        String note) {
}

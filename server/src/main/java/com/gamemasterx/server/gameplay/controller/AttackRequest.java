package com.gamemasterx.server.gameplay.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for an attack roll, as accepted by the {@code /api/gameplay/attack}
 * endpoint.
 *
 * <p>An attack rolls a {@code 1d20} and adds the deterministic attack modifier
 * (the ability modifier, derived server-side from {@link #abilityScore()}, plus
 * the proficiency bonus when {@link #proficient()} is {@code true}). The
 * resulting to-hit total is compared server-side against the target's {@link
 * #armorClass()}. A natural attack roll meeting the encounter's critical-hit
 * threshold (a natural 20 under the default profile) is classified as a critical
 * hit; otherwise the attack is a hit when the to-hit total meets or exceeds the
 * armor class and a miss otherwise.</p>
 *
 * <p>An attack is only valid while the owning encounter is {@code ACTIVE} and it
 * is the supplied {@link #actorId()} participant's turn; these ownership and
 * availability checks are performed by the controller before the attack is
 * resolved.</p>
 *
 * <p>The attack is rolled with the shared dice subsystem. {@link #rollMode()}
 * selects {@code "random"} (the default) or {@code "seeded"}; a seeded roll must
 * also carry a non-blank {@link #seed()}. The individual die value is recorded on
 * the resulting audit record, so the roll is fully auditable.</p>
 *
 * <p>{@link #encounterId()} identifies the encounter whose revision bounds the
 * audited record and whose rules profile governs critical hits.</p>
 */
public record AttackRequest(

        /** Identifier of the actor (participant or NPC) performing the attack. */
        @NotBlank
        String actorId,

        /** Stable identifier of the campaign the attack is performed in. */
        String campaignId,

        /**
         * Identifier of the encounter that bounds the audited record and whose
         * rules profile governs critical hits. Required: an attack is only valid
         * within an active encounter on the actor's turn.
         */
        @NotBlank
        String encounterId,

        /** Identifier of the targeted participant, when applicable. */
        String targetId,

        /** Human-readable name of the target, when applicable. */
        String targetName,

        /**
         * The ability the attack is based on, for example {@code "Strength"}.
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

        /** Whether the actor is proficient in the attack. */
        boolean proficient,

        /**
         * The proficiency bonus to apply when proficient. A value of {@code 0}
         * contributes no proficiency bonus.
         */
        int proficiencyBonus,

        /**
         * The target's armor class. The to-hit total must meet or exceed this
         * value to score a hit. Validated to the same {@code [1, 30]} range as
         * typical armor classes.
         */
        @Min(1)
        @Max(30)
        int armorClass,

        /**
         * The attacker's reach, in grid squares, when the owning encounter
         * tracks grid positions for the attacker and target. When omitted it
         * defaults to {@code 1} (melee reach). Used to validate that the
         * target is within reach before the attack is resolved; an unreachable
         * target is rejected with a clear diagnostic. Range is only validated
         * when both the attacker and the target have a stored grid position.
         */
        @Min(0)
        Integer reach,

        /**
         * The wire representation of the rules profile that governs critical-hit
         * behaviour (for example {@code "SRD-5.2-2024"}). When omitted the
         * default profile ({@code SRD-5.2-2024}) is applied.
         */
        String rulesProfile,

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

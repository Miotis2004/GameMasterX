package com.gamemasterx.server.encounter.controller;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for applying (or updating) a common condition on a participant,
 * as accepted by the
 * {@code POST /api/encounters/{id}/participants/{participantId}/conditions}
 * endpoint.
 *
 * <p>Conditions are applied server-side by the backend-owned
 * {@link com.gamemasterx.server.gameplay.service.DamageService}. A condition is
 * recognised when its name matches a common condition of the supported rules
 * subset (matched case-insensitively); unknown names are still tracked. Applying
 * a condition whose name matches one already held replaces its
 * description and remaining rounds rather than creating a duplicate.</p>
 */
public record ConditionRequest(

        /**
         * The condition name, for example {@code "POISONED"}. Required.
         */
        @NotBlank
        String name,

        /**
         * An optional mechanical description of the condition. When omitted the
         * canonical description of the recognised condition is used.
         */
        String description,

        /**
         * The condition's remaining duration, in rounds, or {@code null} for an
         * indefinite condition.
         */
        Integer roundsRemaining) {
}

package com.gamemasterx.server.encounter.controller;

import jakarta.validation.constraints.Min;

/**
 * Request body for applying damage to a participant, as accepted by the
 * {@code POST /api/encounters/{id}/participants/{participantId}/damage}
 * endpoint.
 *
 * <p>The damage amount is applied server-side by the backend-owned
 * {@link com.gamemasterx.server.gameplay.service.DamageService}: any temporary
 * hit points held by the participant are absorbed first and only the remainder
 * reduces current hit points, which are clamped at {@code 0}.</p>
 */
public record DamageRequest(

        /**
         * The amount of damage to apply. Must be non-negative.
         */
        @Min(0)
        int amount,

        /** Optional free-form note recorded with the damage. */
        String note) {
}

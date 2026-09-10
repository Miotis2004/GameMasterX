package com.gamemasterx.server.encounter.controller;

import jakarta.validation.constraints.Min;

/**
 * Request body for healing a participant, as accepted by the
 * {@code POST /api/encounters/{id}/participants/{participantId}/healing}
 * endpoint.
 *
 * <p>The healing is applied server-side by the backend-owned
 * {@link com.gamemasterx.server.gameplay.service.DamageService}: current hit
 * points are restored up to the maximum and beyond temporary hit points. Healing
 * is not applied to a creature at {@code 0} hit points under the supported rules
 * subset.</p>
 */
public record HealingRequest(

        /**
         * The amount of healing to apply. Must be non-negative.
         */
        @Min(0)
        int amount,

        /** Optional free-form note recorded with the healing. */
        String note) {
}

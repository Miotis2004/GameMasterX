package com.gamemasterx.server.encounter.controller;

import jakarta.validation.constraints.Min;

/**
 * Request body for granting temporary hit points to a participant, as accepted
 * by the {@code POST /api/encounters/{id}/participants/{participantId}/temporary-hit-points}
 * endpoint.
 *
 * <p>The temporary hit points are granted server-side by the backend-owned
 * {@link com.gamemasterx.server.gameplay.service.DamageService}. Temporary hit
 * points are tracked separately from current hit points, are consumed before
 * current hit points when damage is dealt, and are never allowed to fall below
 * {@code 0}.</p>
 */
public record TemporaryHitPointsRequest(

        /**
         * The amount of temporary hit points to grant. Must be non-negative.
         */
        @Min(0)
        int amount,

        /** Optional free-form note recorded with the temporary hit points. */
        String note) {
}

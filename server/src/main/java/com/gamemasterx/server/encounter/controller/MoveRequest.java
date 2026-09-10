package com.gamemasterx.server.encounter.controller;

/**
 * Request body for moving a participant to a new grid square, as accepted by
 * the {@code POST /api/encounters/{id}/participants/{participantId}/move}
 * endpoint.
 *
 * <p>The movement is validated server-side by the backend-owned
 * {@link com.gamemasterx.server.gameplay.service.MovementRangeService}: the
 * Manhattan distance travelled from the participant's current square to the
 * destination square must not exceed the participant's configured available
 * movement. A movement that exceeds that budget is rejected with a clear
 * diagnostic and no position change is applied.</p>
 */
public record MoveRequest(

        /**
         * The destination grid X coordinate.
         */
        int x,

        /**
         * The destination grid Y coordinate.
         */
        int y,

        /** Optional free-form note recorded with the movement. */
        String note) {
}

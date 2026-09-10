package com.gamemasterx.server.gameplay;

/**
 * Raised when a movement distance or an attack/effect range fails backend
 * validation: the distance travelled exceeds the actor's available movement, or
 * a target falls outside the attacker's reach.
 *
 * <p>The message is a clear, human-readable diagnostic naming the squares
 * involved, the distance and the bound that was exceeded. Controllers map this
 * to a {@code 400 BAD_REQUEST} so an invalid movement or an unreachable target
 * is rejected at the boundary rather than applied.</p>
 */
public class MovementRangeValidationException extends RuntimeException {

    public MovementRangeValidationException(String message) {
        super(message);
    }
}
